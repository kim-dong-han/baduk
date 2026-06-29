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

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis/batch")
    public String analyzeBatch(Model model) {
        model.addAttribute("userResults", analysisService.getUserResults());
        model.addAttribute("proResults", analysisService.getProResults());
        model.addAttribute("userGameCount", analysisService.getUserGameCount());
        model.addAttribute("proGameCount", analysisService.getProGameCount());
        model.addAttribute("proWinrateTrend", analysisService.getProWinrateTrend());
        model.addAttribute("userWinrateTrend", analysisService.getUserWinrateTrend());
        model.addAttribute("error", analysisService.getErrorMessage());
        model.addAttribute("running", analysisService.isRunning());
        return "analysis/batch";
    }

    @GetMapping("/analysis/batch/api")
    @ResponseBody
    public Map<String, Object> analyzeBatchApi() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("userResults", analysisService.getUserResults());
        map.put("proResults", analysisService.getProResults());
        map.put("proWinrateTrend", analysisService.getProWinrateTrend());
        map.put("userWinrateTrend", analysisService.getUserWinrateTrend());
        map.put("error", analysisService.getErrorMessage());
        map.put("running", analysisService.isRunning());
        return map;
    }
}