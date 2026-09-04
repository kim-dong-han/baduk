package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.MoveDetail;
import com.example.badukanalyzer.dto.PlayerRating;
import com.example.badukanalyzer.dto.SingleGameResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 기력 인증(기획 A)과 성장률 리그(기획 B) 데이터 공급.
 *
 * 저장된 복기 결과(GameResults/*.json)를 <b>대국자 이름</b>으로 다시 묶는다.
 * 기존 AnalysisService 가 "내 기보 / 프로 기보" 두 덩어리로만 나눠 집계했다면, 여기서는
 * 한 판을 흑·백 두 사람의 기록으로 쪼개 사람 단위로 누적한다. 그래서 같은 저장소에서
 * 여러 명이 있는 순위표가 나온다(엔진 재실행 없음).
 */
@Service
public class RatingService {

    private static final String PRO_MARKER = "신진서 vs";

    /** 성장 추이를 계산하려면 앞·뒤 구간에 각각 이만큼은 있어야 한다. 적으면 한 판 운에 순위가 뒤집힌다. */
    private static final int MIN_GAMES_PER_HALF = 2;
    /** 기력을 '확정'으로 표시하는 최소 표본. 한 판짜리 추정을 기력이라 부르지 않기 위한 하한. */
    private static final int SETTLED_GAMES = 4;
    private static final int SETTLED_MOVES = 240;

    private final SingleGameService singleGameService;

    public RatingService(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    /** 전체 대국자 기력 — 표본이 많은 순. */
    public List<PlayerRating> getAllRatings() {
        Map<String, Acc> byName = new LinkedHashMap<>();

        List<SingleGameResult> games;
        try {
            games = singleGameService.listResultSummaries();
        } catch (Exception e) {
            return List.of();
        }

        // listResultSummaries 는 최신순 → 성장 추이가 과거에서 최근으로 읽히도록 뒤집는다.
        List<SingleGameResult> ordered = new ArrayList<>(games);
        java.util.Collections.reverse(ordered);

        for (SingleGameResult g : ordered) {
            if (g.getMoves() == null || g.getMoves().isEmpty()) continue;
            boolean pro = g.getFileName() != null && g.getFileName().contains(PRO_MARKER);

            for (String color : new String[]{"B", "W"}) {
                String name = playerName(g, color);
                if (name == null) continue;

                GameSlice s = slice(g, color);
                if (s.moves == 0) continue;

                Acc a = byName.computeIfAbsent(name, k -> new Acc(k));
                a.pro = a.pro && pro;          // 한 번이라도 내 기보에 나오면 프로 표시를 뗀다
                a.add(s, label(g));
            }
        }

        List<PlayerRating> out = new ArrayList<>();
        for (Acc a : byName.values()) out.add(a.build());
        out.sort(Comparator.comparingInt(PlayerRating::getGames).reversed()
                .thenComparing(PlayerRating::getAvgScoreLoss));
        return out;
    }

    /** 기력 인증서 기본 대상 — 프로가 아닌 대국자 중 판 수가 가장 많은 사람(= 대개 저장소 주인). */
    public PlayerRating getDefaultSubject(List<PlayerRating> all) {
        return all.stream().filter(r -> !r.isPro()).findFirst()
                .orElse(all.isEmpty() ? null : all.get(0));
    }

    public PlayerRating findByName(List<PlayerRating> all, String name) {
        if (name == null || name.isBlank()) return null;
        return all.stream().filter(r -> r.getName().equals(name)).findFirst().orElse(null);
    }

    /**
     * 성장률 리그 — 개선폭(집/수) 큰 순.
     *
     * 승패나 절대 기력으로 줄을 세우면 이미 강한 사람이 늘 위에 있고, 그게 취미로 시작한
     * 성인이 대회에서 이탈하는 이유였다. 여기서는 <b>최근 판들이 이전 판들보다 얼마나
     * 나아졌는지</b>로만 세운다. 이미 잘 두는 사람은 줄일 집손해가 없어 위로 못 간다.
     */
    public List<PlayerRating> getGrowthLeague() {
        List<PlayerRating> out = new ArrayList<>();
        for (PlayerRating r : getAllRatings()) if (r.isGrowthReady()) out.add(r);
        out.sort(Comparator.comparingDouble(PlayerRating::getGrowth).reversed());
        return out;
    }

    /* ════════ 내부 ════════ */

    private String playerName(SingleGameResult g, String color) {
        String n = "B".equals(color) ? g.getBlackPlayer() : g.getWhitePlayer();
        if (n == null) return null;
        n = n.trim();
        return n.isEmpty() ? null : n;
    }

    private String label(SingleGameResult g) {
        String at = g.getAnalyzedAt() == null ? "" : g.getAnalyzedAt();
        return at.length() >= 10 ? at.substring(5, 7) + "." + at.substring(8, 10) : "-";
    }

    /** 한 판에서 한쪽 대국자의 수만 뽑아 집계한다. */
    private GameSlice slice(SingleGameResult g, String color) {
        GameSlice s = new GameSlice();
        for (MoveDetail m : g.getMoves()) {
            if (!color.equals(m.getColor())) continue;
            double loss = Math.max(0, m.getScoreLoss());
            s.moves++;
            s.lossSum += loss;
            if (m.isMatchesBest()) s.match++;

            String phase = m.getPhase() == null ? "" : m.getPhase();
            PhaseAcc p = switch (phase) {
                case "초반" -> s.opening;
                case "중반" -> s.middle;
                case "종반" -> s.endgame;
                default -> null;
            };
            if (p != null) {
                p.moves++;
                p.lossSum += loss;
                if (m.isMatchesBest()) p.match++;
            }
        }
        return s;
    }

    /** 평균 집손해 → 기력 구간. 복기 화면(review-report.js)의 경계값과 같은 값을 쓴다. */
    private static String[] band(double avgLoss) {
        if (avgLoss < 0.6) return new String[]{"아마 최상위 · 프로급", "약 5단 이상", "#6d3fb8"};
        if (avgLoss < 1.0) return new String[]{"아마 고단자",         "약 1~4단",   "#2563c9"};
        if (avgLoss < 1.8) return new String[]{"아마 상급",           "약 1~5급",   "#0f8a7a"};
        if (avgLoss < 3.0) return new String[]{"아마 중급",           "약 6~12급",  "#27a978"};
        if (avgLoss < 5.0) return new String[]{"아마 초·중급",        "약 13~18급", "#e0a43b"};
        return new String[]{"입문 · 초급", "약 19급 이하", "#d95d4b"};
    }

    private static double r1(double v) { return Math.round(v * 10) / 10.0; }
    private static double r2(double v) { return Math.round(v * 100) / 100.0; }

    private static class PhaseAcc {
        final String phase; int moves, match; double lossSum;
        PhaseAcc(String phase) { this.phase = phase; }
        PlayerRating.PhaseRating build() {
            if (moves == 0) return null;
            double avg = lossSum / moves;
            String[] b = band(avg);
            return PlayerRating.PhaseRating.builder()
                    .phase(phase).moves(moves)
                    .avgScoreLoss(r2(avg))
                    .matchRate(r1(match * 100.0 / moves))
                    .band(b[0]).bandSub(b[1]).bandColor(b[2])
                    .barPct(r1(Math.min(100.0, avg / 5.0 * 100.0)))
                    .build();
        }
    }

    private static class GameSlice {
        int moves, match; double lossSum;
        final PhaseAcc opening = new PhaseAcc("초반");
        final PhaseAcc middle  = new PhaseAcc("중반");
        final PhaseAcc endgame = new PhaseAcc("종반");
    }

    /** 한 대국자의 누적치. */
    private static class Acc {
        final String name;
        boolean pro = true;
        int games, moves, match;
        double lossSum;
        final PhaseAcc opening = new PhaseAcc("초반");
        final PhaseAcc middle  = new PhaseAcc("중반");
        final PhaseAcc endgame = new PhaseAcc("종반");
        final List<Double> perGameLoss = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        Acc(String name) { this.name = name; }

        void add(GameSlice s, String label) {
            games++;
            moves   += s.moves;
            match   += s.match;
            lossSum += s.lossSum;
            merge(opening, s.opening);
            merge(middle,  s.middle);
            merge(endgame, s.endgame);
            perGameLoss.add(r2(s.lossSum / s.moves));
            labels.add(label);
        }

        private void merge(PhaseAcc dst, PhaseAcc src) {
            dst.moves += src.moves; dst.match += src.match; dst.lossSum += src.lossSum;
        }

        PlayerRating build() {
            double avg = lossSum / moves;
            String[] b = band(avg);

            PlayerRating.PhaseRating op = opening.build(), mid = middle.build(), end = endgame.build();

            // 가장 강한/약한 구간 — 세 구간이 모두 있을 때만 말이 된다.
            String strong = null, weak = null;
            if (op != null && mid != null && end != null) {
                List<PlayerRating.PhaseRating> ps = List.of(op, mid, end);
                strong = ps.stream().min(Comparator.comparingDouble(PlayerRating.PhaseRating::getAvgScoreLoss))
                        .map(PlayerRating.PhaseRating::getPhase).orElse(null);
                weak = ps.stream().max(Comparator.comparingDouble(PlayerRating.PhaseRating::getAvgScoreLoss))
                        .map(PlayerRating.PhaseRating::getPhase).orElse(null);
            }

            // 기복 — 판별 평균 집손해의 표준편차
            double vol = 0;
            if (perGameLoss.size() >= 2) {
                double m = perGameLoss.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                double sq = perGameLoss.stream().mapToDouble(v -> (v - m) * (v - m)).sum();
                vol = Math.sqrt(sq / perGameLoss.size());
            }
            String steady = perGameLoss.size() < 2 ? "판단 보류"
                    : vol < 0.5 ? "안정형" : vol < 1.2 ? "보통" : "기복형";

            // 성장 — 판 목록을 앞/뒤 절반으로 갈라 평균 집손해를 비교
            int n = perGameLoss.size();
            int half = n / 2;
            boolean ready = half >= MIN_GAMES_PER_HALF;
            double early = 0, recent = 0, growth = 0, growthPct = 0;
            if (ready) {
                early  = perGameLoss.subList(0, half).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                recent = perGameLoss.subList(n - half, n).stream().mapToDouble(Double::doubleValue).average().orElse(0);
                growth = early - recent;                       // 양수 = 집손해가 줄었다 = 좋아졌다
                growthPct = early == 0 ? 0 : growth / early * 100.0;
            }

            String conf, note;
            if (games >= SETTLED_GAMES && moves >= SETTLED_MOVES) {
                conf = "확정"; note = games + "판 " + moves + "수로 산출";
            } else if (games >= 2) {
                conf = "잠정"; note = games + "판뿐이라 흔들릴 수 있음 (확정까지 " + Math.max(0, SETTLED_GAMES - games) + "판)";
            } else {
                conf = "표본 부족"; note = "1판 기준 — 기력이라기보다 이 판의 착수 정밀도";
            }

            return PlayerRating.builder()
                    .name(name).pro(pro)
                    .games(games).moves(moves)
                    .avgScoreLoss(r2(avg))
                    .matchRate(r1(match * 100.0 / moves))
                    .band(b[0]).bandSub(b[1]).bandColor(b[2])
                    .confidence(conf).confidenceNote(note)
                    .opening(op).middle(mid).endgame(end)
                    .strongPhase(strong).weakPhase(weak)
                    .volatility(r2(vol)).steadiness(steady)
                    .growthReady(ready)
                    .earlyLoss(r2(early)).recentLoss(r2(recent))
                    .growth(r2(growth)).growthPct(r1(growthPct))
                    .improved(growth > 0)
                    .growthAbs(r2(Math.abs(growth))).growthPctAbs(r1(Math.abs(growthPct)))
                    .earlyGames(half).recentGames(half)
                    .lossTrend(perGameLoss).gameLabels(labels)
                    .build();
        }
    }
}
