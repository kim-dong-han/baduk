package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.util.CoordinateConverter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class PlayService {

    private final KataGoService kataGoService;

    private final List<Move> history = Collections.synchronizedList(new ArrayList<>());
    private volatile String userColor = "B";
    private volatile boolean gameOver = false;
    private volatile int consecutivePasses = 0;
    private volatile Double lastUserLoss = null;  // 직전 사용자 착수의 '최선 대비 집손해' (실시간 평가)

    public PlayService(KataGoService kataGoService) {
        this.kataGoService = kataGoService;
    }

    public synchronized void newGame(String userColor) {
        this.history.clear();
        this.userColor = userColor;
        this.gameOver = false;
        this.consecutivePasses = 0;
    }

    /** 복기 국면에서 이어두기: setup 수순으로 판을 채우고 userColor로 대국 시작 */
    public synchronized void newGameFrom(List<Move> setup, String userColor) {
        this.history.clear();
        this.history.addAll(setup);
        this.userColor = userColor;
        this.gameOver = false;
        this.consecutivePasses = 0;
    }

    /** AI 차례면 한 수 두고 반환, 아니면 null (이어두기 직후 차례 판정용) */
    public synchronized String getAiMoveIfTurn() throws Exception {
        return addAiMove();
    }

    /** 유저가 백일 때 AI 선착 */
    public synchronized String getAiFirstMove() throws Exception {
        if (!"W".equals(userColor) || !history.isEmpty()) return null;
        return addAiMove();
    }

    /** 유저 착수 → AI 응답 반환 (null = 게임 종료) */
    public synchronized String playUserMove(String gtp) throws Exception {
        if (gameOver) throw new IllegalStateException("게임 종료");
        String color = currentColor();
        if (!color.equals(userColor)) throw new IllegalStateException("AI 차례입니다");

        lastUserLoss = null;
        boolean isPass = "pass".equalsIgnoreCase(gtp);
        // 착수 직전 국면의 최선 기대값(흑 기준) — 실착 평가 기준. 패스는 평가 생략.
        Double beforeLead = null;
        if (!isPass) {
            try { beforeLead = kataGoService.getBestMoveEval(new ArrayList<>(history)).rootScoreLead(); }
            catch (Exception ignored) {}
        }

        history.add(CoordinateConverter.fromGtp(color, gtp));
        if (isPass) {
            consecutivePasses++;
            if (consecutivePasses >= 2) { gameOver = true; return null; }
        } else {
            consecutivePasses = 0;
        }

        // AI 응답 = 착수 후 국면 분석. rootInfo.scoreLead로 실착 손해를 완성.
        String aiColor = "B".equals(userColor) ? "W" : "B";
        if (!currentColor().equals(aiColor)) return null;
        KataGoService.MoveEval post = kataGoService.getBestMoveEval(new ArrayList<>(history));
        if (beforeLead != null) {
            boolean isBlack = "B".equals(color);
            double raw = isBlack ? beforeLead - post.rootScoreLead()
                                 : post.rootScoreLead() - beforeLead;
            lastUserLoss = Math.max(0, raw);
        }

        String move = post.move();
        history.add(CoordinateConverter.fromGtp(aiColor, move));
        if ("pass".equalsIgnoreCase(move)) {
            consecutivePasses++;
            if (consecutivePasses >= 2) gameOver = true;
        } else {
            consecutivePasses = 0;
        }
        return move;
    }

    public Double getLastUserLoss() { return lastUserLoss; }

    /** 현재 국면 형세 판단/계가 (흑 기준 집차·승률·집 영역) */
    public synchronized KataGoService.PositionEval estimate() throws Exception {
        return kataGoService.evaluatePosition(new ArrayList<>(history));
    }

    /** 무르기: 마지막 2수(AI+유저) 제거 */
    public synchronized void undo() {
        int removeCount = Math.min(2, history.size());
        // 유저가 백이면 AI가 선착이므로 1수는 남긴다
        if ("W".equals(userColor) && history.size() - removeCount < 1) removeCount = history.size() - 1;
        for (int i = 0; i < removeCount && !history.isEmpty(); i++) {
            history.remove(history.size() - 1);
        }
        gameOver = false;
        consecutivePasses = 0;
    }

    public List<Move> getHistory()   { return new ArrayList<>(history); }
    public String getUserColor()     { return userColor; }
    public boolean isGameOver()      { return gameOver; }

    /** 현재 국면(둘 차례)의 최선수 GTP. 힌트용 — 판을 바꾸지 않는다. */
    public synchronized String getHint() throws Exception {
        if (gameOver) return null;
        return kataGoService.getBestMove(new ArrayList<>(history));
    }

    // 차례 = 마지막 착수의 반대 색 (빈 판이면 흑).
    // 파리티(짝=흑) 대신 실제 마지막 색 기준 → 백선착·접바둑·복기 이어두기 국면에서도 정확.
    private String currentColor() {
        if (history.isEmpty()) return "B";
        return "B".equals(history.get(history.size() - 1).getColor()) ? "W" : "B";
    }

    private String addAiMove() throws Exception {
        String aiColor = "B".equals(userColor) ? "W" : "B";
        if (!currentColor().equals(aiColor)) return null;

        String move = kataGoService.getBestMove(new ArrayList<>(history));
        history.add(CoordinateConverter.fromGtp(aiColor, move));
        if ("pass".equalsIgnoreCase(move)) {
            consecutivePasses++;
            if (consecutivePasses >= 2) gameOver = true;
        } else {
            consecutivePasses = 0;
        }
        return move;
    }
}
