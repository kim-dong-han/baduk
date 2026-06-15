package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.AnalysisResponse;
import com.example.badukanalyzer.service.AnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @GetMapping("/analysis/batch")
    public List<AnalysisResponse> analyzeBatch() throws Exception {
        return analysisService.analyzeBatch();
    }
}