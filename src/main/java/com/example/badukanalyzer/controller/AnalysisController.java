package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.service.AnalysisService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
public class AnalysisController {

    private final AnalysisService analysisService;
    private final com.example.badukanalyzer.service.TsumegoService tsumegoService;

    public AnalysisController(AnalysisService analysisService,
                              com.example.badukanalyzer.service.TsumegoService tsumegoService) {
        this.analysisService = analysisService;
        this.tsumegoService = tsumegoService;
    }

    @GetMapping("/analysis/batch")
    public String analyzeBatch(Model model) {
        model.addAttribute("userResults", analysisService.getUserResults());
        model.addAttribute("proResults", analysisService.getProResults());
        model.addAttribute("userGameCount", analysisService.getUserGameCount());
        model.addAttribute("proGameCount", analysisService.getProGameCount());
        model.addAttribute("userWeaknesses", analysisService.getUserWeaknesses());
        model.addAttribute("proWinrateTrend", analysisService.getProWinrateTrend());
        model.addAttribute("userWinrateTrend", analysisService.getUserWinrateTrend());
        model.addAttribute("error", analysisService.getErrorMessage());
        model.addAttribute("running", analysisService.isRunning());

        // 판 단위 요약 — 실력 변화 추이 그래프 + "최근 분석 기보" 표
        model.addAttribute("userGames", analysisService.getUserGameRows());

        // 구간 카드의 "프로 기보 대비" 한 줄 — proResults 를 구간→일치율 로 색인만 한다
        Map<String, Double> proMatchByPhase = new java.util.LinkedHashMap<>();
        for (AnalysisResponse r : analysisService.getProResults()) {
            proMatchByPhase.put(r.getPhase(), r.getMatchRate());
        }
        model.addAttribute("proMatchByPhase", proMatchByPhase);

        // 히어로 바둑판에 띄울 실제 국면: 집손해가 가장 컸던 내 실수(오답노트 1위)
        List<com.example.badukanalyzer.dto.MistakeNote> notes = analysisService.getUserMistakeNotes();
        model.addAttribute("focusNote", notes.isEmpty() ? null : notes.get(0));
        model.addAttribute("mistakeTotal", notes.size());
        return "analysis/batch";
    }

    @GetMapping("/notes")
    public String mistakeNotes(Model model) {
        model.addAttribute("notes", analysisService.getUserMistakeNotes());
        return "analysis/notes";
    }

    @GetMapping("/gallery")
    public String gallery(Model model) {
        model.addAttribute("items", analysisService.getGalleryItems());
        // 번들된 사활 문제도 같은 갤러리에서 카드로 보여준다(별도 API 없이 목록 그대로 사용)
        model.addAttribute("tsumegos", tsumegoService.all());
        return "analysis/gallery";
    }

    @GetMapping("/analysis/batch/api")
    @ResponseBody
    public Map<String, Object> analyzeBatchApi() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("userResults", analysisService.getUserResults());
        map.put("proResults", analysisService.getProResults());
        map.put("userWeaknesses", analysisService.getUserWeaknesses());
        map.put("proWinrateTrend", analysisService.getProWinrateTrend());
        map.put("userWinrateTrend", analysisService.getUserWinrateTrend());
        map.put("error", analysisService.getErrorMessage());
        map.put("running", analysisService.isRunning());
        return map;
    }
}