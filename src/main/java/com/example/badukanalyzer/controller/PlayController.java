package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.service.PlayService;
import com.example.badukanalyzer.util.CoordinateConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class PlayController {

    private final PlayService playService;

    public PlayController(PlayService playService) {
        this.playService = playService;
    }

    @GetMapping("/play")
    public String playPage() {
        return "game/play";
    }

    @PostMapping("/api/play/new")
    @ResponseBody
    public Map<String, Object> newGame(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userColor = body.getOrDefault("userColor", "B");
            playService.newGame(userColor);
            String aiMove = playService.getAiFirstMove();
            result.put("ok", true);
            result.put("aiMove", aiMove);
            result.put("gameOver", false);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /** 복기 화면에서 넘어온 국면으로 이어두기 시작. body: {userColor, moves:[[color,gtp],...]} */
    @PostMapping("/api/play/from")
    @ResponseBody
    public Map<String, Object> newGameFrom(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String userColor = String.valueOf(body.getOrDefault("userColor", "B"));
            Object rawMoves = body.getOrDefault("moves", List.of());
            List<Move> setup = new ArrayList<>();
            for (Object o : (List<?>) rawMoves) {
                List<?> pair = (List<?>) o;               // [color, gtp]
                String color = String.valueOf(pair.get(0));
                String gtp   = String.valueOf(pair.get(1));
                setup.add(CoordinateConverter.fromGtp(color, gtp));
            }
            playService.newGameFrom(setup, userColor);
            String aiMove = playService.getAiMoveIfTurn();  // 유저 차례면 null
            List<Map<String, String>> hist = playService.getHistory().stream()
                .map(m -> Map.of("color", m.getColor(), "gtp", CoordinateConverter.toGtpCoord(m)))
                .collect(Collectors.toList());
            result.put("ok", true);
            result.put("history", hist);
            result.put("aiMove", aiMove);
            result.put("userColor", playService.getUserColor());
            result.put("gameOver", playService.isGameOver());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/play/move")
    @ResponseBody
    public Map<String, Object> playMove(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String gtp = body.get("gtp");
            String aiMove = playService.playUserMove(gtp);
            result.put("ok", true);
            result.put("aiMove", aiMove);
            result.put("gameOver", playService.isGameOver());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/play/pass")
    @ResponseBody
    public Map<String, Object> passMove() {
        Map<String, Object> result = new HashMap<>();
        try {
            String aiMove = playService.playUserMove("pass");  // pass 처리(2연속 pass면 종료)
            result.put("ok", true);
            result.put("aiMove", aiMove);
            result.put("gameOver", playService.isGameOver());
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/play/hint")
    @ResponseBody
    public Map<String, Object> hint() {
        Map<String, Object> result = new HashMap<>();
        try {
            result.put("ok", true);
            result.put("hint", playService.getHint());   // 최선수 GTP (null=종료)
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/play/undo")
    @ResponseBody
    public Map<String, Object> undoMove() {
        playService.undo();
        List<Map<String, String>> hist = playService.getHistory().stream()
            .map(m -> Map.of("color", m.getColor(), "gtp", CoordinateConverter.toGtpCoord(m)))
            .collect(Collectors.toList());
        return Map.of("ok", true, "history", hist, "gameOver", false);
    }
}
