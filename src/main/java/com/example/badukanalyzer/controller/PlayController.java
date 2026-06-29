package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.service.PlayService;
import com.example.badukanalyzer.util.CoordinateConverter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

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
