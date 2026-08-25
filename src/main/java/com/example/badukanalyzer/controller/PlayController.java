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

    /** 실시간 분석판: AI 대국 없이 직접 흑·백을 놓으며 추천수·승률을 실시간 확인(빈 판 시작). */
    @GetMapping("/study")
    public String studyPage(org.springframework.ui.Model model) {
        // 분석 설정 표시용 — 엔진에 실제로 넘기는 값 그대로
        model.addAttribute("studyVisits", com.example.badukanalyzer.service.KataGoService.TOP_VISITS);
        model.addAttribute("komi", com.example.badukanalyzer.service.KataGoService.KOMI);
        return "game/study";
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
            result.put("userLoss", playService.getLastUserLoss());  // 방금 둔 수의 최선 대비 집손해
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
            List<com.example.badukanalyzer.service.KataGoService.Candidate> hints = playService.getHints(5);
            // KataGo winrate/scoreLead 는 '흑 기준'(실측 확인). 힌트는 내 차례에 요청되므로 내 색 관점으로 변환.
            boolean userBlack = "B".equals(playService.getUserColor());
            List<Map<String, Object>> list = hints.stream().map(c -> {
                double wr   = userBlack ? c.winrate()   : 1 - c.winrate();      // 내 관점 승률(0~1)
                double lead = userBlack ? c.scoreLead() : -c.scoreLead();       // 내 관점 집차
                Map<String, Object> m = new HashMap<>();
                m.put("move", c.move());
                m.put("winrate", Math.round(wr * 1000) / 10.0);   // 내 관점 승률 %(소수1자리)
                m.put("scoreLead", Math.round(lead * 10) / 10.0); // 내 관점 예상 집차
                return m;
            }).collect(Collectors.toList());
            result.put("ok", true);
            result.put("hints", list);                                    // 상위 5개(순위=배열 순서)
            result.put("hint", hints.isEmpty() ? null : hints.get(0).move()); // 하위호환(최선수 1개)
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /** 놓아보기(결과화면)용 무상태 분석. body:{moves:[[color,gtp],...]} → 둘 차례 관점 승률·상위 후보수. */
    @PostMapping("/api/analyze/top")
    @ResponseBody
    public Map<String, Object> analyzeTop(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Object rawMoves = body.getOrDefault("moves", List.of());
            List<Move> position = new ArrayList<>();
            for (Object o : (List<?>) rawMoves) {
                List<?> pair = (List<?>) o;
                position.add(CoordinateConverter.fromGtp(String.valueOf(pair.get(0)), String.valueOf(pair.get(1))));
            }
            String sideToMove = position.isEmpty() ? "B"
                : ("B".equals(position.get(position.size() - 1).getColor()) ? "W" : "B");
            // KataGo winrate/scoreLead 는 '흑 기준'(실측 확인) → 둘 차례(둘 쪽) 관점으로 변환해 내보낸다.
            boolean sideBlack = "B".equals(sideToMove);
            var top = playService.analyzeTop(position, 5);
            List<Map<String, Object>> cands = top.candidates().stream().map(c -> {
                double wr   = sideBlack ? c.winrate()   : 1 - c.winrate();      // 둘 차례 관점 승률(0~1)
                double lead = sideBlack ? c.scoreLead() : -c.scoreLead();
                Map<String, Object> m = new HashMap<>();
                m.put("move", c.move());
                m.put("winrate", Math.round(wr * 1000) / 10.0);      // 둘 차례 관점 승률 %(소수1자리)
                m.put("scoreLead", Math.round(lead * 10) / 10.0);
                m.put("pv", c.pv());                                  // 이 수 이후 예상 진행(참고도) GTP 수순
                return m;
            }).collect(Collectors.toList());
            double rootWr = sideBlack ? top.rootWinrate() : 1 - top.rootWinrate();
            result.put("ok", true);
            result.put("sideToMove", sideToMove);
            result.put("rootWinrate", Math.round(rootWr * 1000) / 10.0);  // 둘 차례 관점 승률 %
            result.put("rootScoreLead", Math.round(top.rootScoreLead() * 10) / 10.0);  // 흑 기준 예상 집차(+흑/−백)
            result.put("candidates", cands);
            result.put("ownership", top.ownership());                     // 집(영역) 예측 361칸(흑 기준 +흑/−백)
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    @PostMapping("/api/play/estimate")
    @ResponseBody
    public Map<String, Object> estimate() {
        Map<String, Object> result = new HashMap<>();
        try {
            com.example.badukanalyzer.service.KataGoService.PositionEval e = playService.estimate();
            result.put("ok", true);
            result.put("scoreLead", e.scoreLead());   // 흑 기준 집차
            result.put("winrate", e.winrate());        // 흑 승률
            result.put("ownership", e.ownership());    // 361칸, +흑/−백
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
