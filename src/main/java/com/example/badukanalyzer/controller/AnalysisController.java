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