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

    public PlayService(KataGoService kataGoService) {
        this.kataGoService = kataGoService;
    }

    public synchronized void newGame(String userColor) {
        this.history.clear();
        this.userColor = userColor;
        this.gameOver = false;
        this.consecutivePasses = 0;
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

        history.add(CoordinateConverter.fromGtp(color, gtp));
        if ("pass".equalsIgnoreCase(gtp)) {
            consecutivePasses++;
            if (consecutivePasses >= 2) { gameOver = true; return null; }
        } else {
            consecutivePasses = 0;
        }
        return addAiMove();
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

    private String currentColor() {
        return history.size() % 2 == 0 ? "B" : "W";
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
