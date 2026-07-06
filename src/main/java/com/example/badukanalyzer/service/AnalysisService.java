package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.dto.GalleryItem;
import com.example.badukanalyzer.dto.MistakeNote;
import com.example.badukanalyzer.dto.MoveDetail;
import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.dto.WeaknessInsight;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 실력 리포트(/analysis/batch) 데이터 공급.
 * KataGo를 다시 돌리지 않고, /game 복기에서 이미 저장된 결과(GameResults/*.json)를
 * 내 기보 vs 프로(파일명 "신진서 vs") 그룹으로 나눠 구간별로 집계한다.
 */
@Service
public class AnalysisService {
    private static final String[] PHASES = {"초반", "중반", "종반"};
    private static final String PRO_MARKER = "신진서 vs";

    private final SingleGameService singleGameService;
    private volatile String errorMessage;

    public AnalysisService(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    public List<AnalysisResponse> getUserResults() { return aggregate(false); }
    public List<AnalysisResponse> getProResults()  { return aggregate(true); }
    public int getUserGameCount() { return countGames(false); }
    public int getProGameCount()  { return countGames(true); }
    public Object getProWinrateTrend()  { return null; }   // 현재 템플릿 미사용
    public Object getUserWinrateTrend() { return null; }
    public String getErrorMessage()     { return errorMessage; }
    public boolean isRunning()          { return false; }  // 즉시 집계라 백그라운드 작업 없음

    private List<AnalysisResponse> aggregate(boolean pro) {
        List<SingleGameResult> games;
        try {
            games = singleGameService.listResultSummaries();   // 파일명 기준 최신 분석만(중복 제거)
            errorMessage = null;
        } catch (Exception e) {
            errorMessage = "분석 결과를 읽지 못했습니다: " + e.getMessage();
            return List.of();
        }

        List<AnalysisResponse> out = new ArrayList<>();
        for (String phase : PHASES) {
            int total = 0, match = 0, ex = 0, gd = 0, nm = 0, bd = 0, bl = 0;
            double scoreLossSum = 0, wrLossSum = 0;

            for (SingleGameResult g : games) {
                boolean isPro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);
                if (isPro != pro || g.getMoves() == null) continue;

                for (MoveDetail m : g.getMoves()) {
                    if (!phase.equals(m.getPhase())) continue;
                    total++;
                    if (m.isMatchesBest()) match++;
                    scoreLossSum += m.getScoreLoss();
                    wrLossSum    += m.getWinrateLoss();
                    switch (m.getGrade() == null ? "" : m.getGrade()) {
                        case "최선" -> ex++;
                        case "좋음" -> gd++;
                        case "보통" -> nm++;
                        case "실수" -> bd++;
                        case "악수" -> bl++;
                    }
                }
            }
            if (total == 0) continue;

            out.add(AnalysisResponse.builder()
                    .phase(phase)
                    .matchRate(round2(match * 100.0 / total))
                    .winRateLoss(round3(wrLossSum / total))
                    .scoreLoss(round2(scoreLossSum / total))
                    .excellentRate(round2(ex * 100.0 / total))
                    .goodRate(round2(gd * 100.0 / total))
                    .normalRate(round2(nm * 100.0 / total))
                    .badRate(round2(bd * 100.0 / total))
                    .blunderRate(round2(bl * 100.0 / total))
                    .build());
        }
        return out;
    }

    /**
     * "반복 약점" 자동 코멘트 — 내 기보 전체를 여러 판에 걸쳐 집계해 만든 코칭 문구.
     * 재분석 없이 저장된 MoveDetail(구간·등급·집손해·수번)만으로 판단한다.
     */
    public List<WeaknessInsight> getUserWeaknesses() {
        List<SingleGameResult> games;
        try {
            games = singleGameService.listResultSummaries();
        } catch (Exception e) {
            return List.of();
        }

        int gameCount = 0, totalMoves = 0, totalBlunder = 0, totalMistake = 0;
        long blunderTurnSum = 0;
        // 구간별 누계
        Map<String, int[]> byPhase = new LinkedHashMap<>();   // phase -> [moves, match, blunder]
        Map<String, double[]> lossByPhase = new LinkedHashMap<>(); // phase -> [scoreLossSum]
        for (String p : PHASES) { byPhase.put(p, new int[3]); lossByPhase.put(p, new double[1]); }

        for (SingleGameResult g : games) {
            boolean isPro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);
            if (isPro || g.getMoves() == null || g.getMoves().isEmpty()) continue;
            gameCount++;
            for (MoveDetail m : g.getMoves()) {
                String phase = m.getPhase();
                int[] pc = byPhase.get(phase);
                double[] pl = lossByPhase.get(phase);
                if (pc == null || pl == null) continue;   // 미지의 구간은 건너뜀
                totalMoves++;
                pc[0]++;
                if (m.isMatchesBest()) pc[1]++;
                pl[0] += Math.max(0, m.getScoreLoss());
                String grade = m.getGrade() == null ? "" : m.getGrade();
                if ("악수".equals(grade)) { totalBlunder++; pc[2]++; blunderTurnSum += m.getTurnNumber(); }
                if ("실수".equals(grade)) totalMistake++;
            }
        }

        List<WeaknessInsight> out = new ArrayList<>();
        if (gameCount == 0 || totalMoves == 0) return out;

        // 1) 반복 약점 구간: AI 유사도가 가장 낮은 구간 (유효 표본만)
        String worstPhase = null; double worstMatch = 101;
        String bestPhase = null;  double bestMatch = -1;
        for (String p : PHASES) {
            int[] pc = byPhase.get(p);
            if (pc[0] < 5) continue;                    // 표본 부족 구간 제외
            double match = pc[1] * 100.0 / pc[0];
            if (match < worstMatch) { worstMatch = match; worstPhase = p; }
            if (match > bestMatch)  { bestMatch = match; bestPhase = p; }
        }
        if (worstPhase != null && bestPhase != null && !worstPhase.equals(bestPhase)) {
            out.add(WeaknessInsight.builder()
                    .icon("🎯")
                    .title("반복 약점: " + worstPhase)
                    .detail("여러 판에서 <b>" + worstPhase + "</b> AI 유사도(" + round1(worstMatch)
                            + "%)가 가장 낮아요. 가장 잘 두는 " + bestPhase + "(" + round1(bestMatch)
                            + "%)과 " + round1(bestMatch - worstMatch) + "%p 차이 — 이 구간을 집중 복기해 보세요.")
                    .severity(worstMatch < 40 ? "high" : "mid")
                    .build());
        } else if (worstPhase != null && worstMatch < 50) {
            // 유효 구간이 하나뿐이거나 표본이 한 구간에 몰린 경우 — 그 구간을 약점으로 안내
            out.add(WeaknessInsight.builder()
                    .icon("🎯")
                    .title("집중 복기: " + worstPhase)
                    .detail("아직 <b>" + worstPhase + "</b> 구간 데이터가 주로 쌓였어요. AI 유사도 "
                            + round1(worstMatch) + "% — 이 구간부터 복기하면 효과가 커요."
                            + " (기보가 더 쌓이면 중·종반 분석도 나와요)")
                    .severity(worstMatch < 40 ? "high" : "mid")
                    .build());
        }

        // 2) 큰 실수(악수, 5집 이상) 빈도 + 몰리는 구간
        double blunderPerGame = totalBlunder / (double) gameCount;
        if (totalBlunder > 0) {
            String hotPhase = null; int hotCnt = -1;
            for (String p : PHASES) { int c = byPhase.get(p)[2]; if (c > hotCnt) { hotCnt = c; hotPhase = p; } }
            String where = (hotCnt > 0 && hotPhase != null)
                    ? " 특히 <b>" + hotPhase + "</b>에 몰려요(" + hotCnt + "회)."
                    : "";
            out.add(WeaknessInsight.builder()
                    .icon("⚠️")
                    .title("큰 실수 빈도")
                    .detail("한 판에 평균 <b>" + round1(blunderPerGame) + "번</b> 큰 실수(5집 이상 손해, 총 "
                            + totalBlunder + "회)가 나와요." + where)
                    .severity(blunderPerGame >= 2 ? "high" : blunderPerGame >= 1 ? "mid" : "good")
                    .build());
        }

        // 3) 집을 가장 많이 잃는 구간 (평균 집손해/수)
        String leakPhase = null; double leakAvg = -1;
        for (String p : PHASES) {
            int moves = byPhase.get(p)[0];
            if (moves < 5) continue;
            double avg = lossByPhase.get(p)[0] / moves;
            if (avg > leakAvg) { leakAvg = avg; leakPhase = p; }
        }
        if (leakPhase != null && leakAvg >= 1.0) {
            out.add(WeaknessInsight.builder()
                    .icon("💧")
                    .title(leakPhase + " 집 손실")
                    .detail("<b>" + leakPhase + "</b>에서 한 수당 평균 <b>" + round1(leakAvg)
                            + "집</b>씩 손해봐요. " + phaseTip(leakPhase))
                    .severity(leakAvg >= 3 ? "high" : "mid")
                    .build());
        }

        // 4) 잘하는 점(격려) — 표본 충분하고 최고 구간 유사도 높을 때
        if (bestPhase != null && bestMatch >= 55) {
            out.add(WeaknessInsight.builder()
                    .icon("👍")
                    .title("강점: " + bestPhase)
                    .detail("<b>" + bestPhase + "</b>은 AI 유사도 " + round1(bestMatch)
                            + "%로 안정적이에요. 이 감각을 다른 구간에도 옮겨 보세요.")
                    .severity("good")
                    .build());
        }
        return out;
    }

    /**
     * 오답노트 — 내 기보(non-pro) 전체에서 실수·악수로 분류된 수를 모아 집손해 큰 순으로 반환.
     * 재분석 없이 저장된 결과만 사용. 과도한 양 방지를 위해 상한(200) 적용.
     */
    public List<MistakeNote> getUserMistakeNotes() {
        List<SingleGameResult> games;
        try {
            games = singleGameService.listResultSummaries();
        } catch (Exception e) {
            return List.of();
        }

        List<MistakeNote> notes = new ArrayList<>();
        for (SingleGameResult g : games) {
            boolean isPro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);
            if (isPro || g.getMoves() == null) continue;
            for (MoveDetail m : g.getMoves()) {
                String grade = m.getGrade() == null ? "" : m.getGrade();
                if (!"실수".equals(grade) && !"악수".equals(grade)) continue;
                notes.add(MistakeNote.builder()
                        .gameId(g.getId())
                        .fileName(g.getFileName())
                        .blackPlayer(g.getBlackPlayer())
                        .whitePlayer(g.getWhitePlayer())
                        .analyzedAt(g.getAnalyzedAt())
                        .turnNumber(m.getTurnNumber())
                        .color(m.getColor())
                        .move(m.getMove())
                        .bestMove(m.getBestMove())
                        .grade(grade)
                        .scoreLoss(round1(Math.max(0, m.getScoreLoss())))
                        .phase(m.getPhase())
                        .build());
            }
        }
        notes.sort(Comparator.comparingDouble(MistakeNote::getScoreLoss).reversed());
        return notes.size() > 200 ? notes.subList(0, 200) : notes;
    }

    private String phaseTip(String phase) {
        return switch (phase == null ? "" : phase) {
            case "초반" -> "포석·정석 이후 방향 선택을 복기해 보세요.";
            case "중반" -> "전투·행마에서 무리한 수가 없었는지 살펴보세요.";
            case "종반" -> "끝내기 크기 비교·사활 마무리 연습이 도움돼요.";
            default -> "해당 구간 복기를 추천해요.";
        };
    }

    /**
     * 샘플 기보 갤러리 — 저장된 모든 복기 결과를 카드용 요약으로 변환.
     * 프로 기보 먼저, 그 안에서 분석 최신순.
     */
    public List<GalleryItem> getGalleryItems() {
        List<SingleGameResult> games;
        try {
            games = singleGameService.listResultSummaries();   // 파일명 기준 최신, 분석 최신순 정렬됨
        } catch (Exception e) {
            return List.of();
        }

        List<GalleryItem> pro = new ArrayList<>();
        List<GalleryItem> mine = new ArrayList<>();
        for (SingleGameResult g : games) {
            if (g.getMoves() == null || g.getMoves().isEmpty()) continue;
            boolean isPro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);

            // 전체 AI 유사도 = 구간 일치율의 수 가중 평균, 최고/최저 구간
            double mSum = 0; int cSum = 0;
            String bestPhase = null, worstPhase = null; double bestM = -1, worstM = 101;
            SingleGameResult.PhaseStats[] ps = { g.getOpening(), g.getMiddle(), g.getEndgame() };
            for (SingleGameResult.PhaseStats p : ps) {
                if (p == null || p.getMoveCount() == 0) continue;
                mSum += p.getMatchRate() * p.getMoveCount();
                cSum += p.getMoveCount();
                if (p.getMatchRate() > bestM)  { bestM = p.getMatchRate();  bestPhase = p.getPhase(); }
                if (p.getMatchRate() < worstM) { worstM = p.getMatchRate(); worstPhase = p.getPhase(); }
            }
            double matchRate = cSum > 0 ? round1(mSum / cSum) : 0;

            GalleryItem item = GalleryItem.builder()
                    .id(g.getId())
                    .title(galleryTitle(g, isPro))
                    .pro(isPro)
                    .dateText(g.getAnalyzedAt() != null && g.getAnalyzedAt().length() >= 10
                            ? g.getAnalyzedAt().substring(0, 10) : "")
                    .totalMoves(g.getTotalMoves())
                    .matchRate(matchRate)
                    .openingLoss(g.getOpening() != null ? g.getOpening().getAvgScoreLoss() : 0)
                    .middleLoss(g.getMiddle()  != null ? g.getMiddle().getAvgScoreLoss()  : 0)
                    .endgameLoss(g.getEndgame() != null ? g.getEndgame().getAvgScoreLoss() : 0)
                    .bestPhase(bestPhase)
                    .worstPhase(worstPhase)
                    .build();
            (isPro ? pro : mine).add(item);
        }
        pro.addAll(mine);   // 프로 먼저
        return pro;
    }

    private String galleryTitle(SingleGameResult g, boolean isPro) {
        String b = g.getBlackPlayer(), w = g.getWhitePlayer();
        if (b != null && !b.isBlank() && w != null && !w.isBlank()) return b + " vs " + w;
        String f = g.getFileName() == null ? "기보" : g.getFileName();
        return f.replaceFirst("(?i)\\.(gib|sgf)$", "");
    }

    private int countGames(boolean pro) {
        try {
            return (int) singleGameService.listResultSummaries().stream()
                    .filter(g -> (g.getFileName() != null && g.getFileName().contains(PRO_MARKER)) == pro)
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    private double round1(double v) { return Math.round(v * 10)   / 10.0; }
    private double round2(double v) { return Math.round(v * 100)  / 100.0; }
    private double round3(double v) { return Math.round(v * 1000) / 1000.0; }
}
