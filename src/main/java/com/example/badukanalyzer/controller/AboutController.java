package com.example.badukanalyzer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AboutController {

    private final com.example.badukanalyzer.service.AnalysisService analysisService;
    private final com.example.badukanalyzer.service.TsumegoService tsumegoService;
    private final com.example.badukanalyzer.service.KataGoService kataGoService;

    public AboutController(com.example.badukanalyzer.service.AnalysisService analysisService,
                           com.example.badukanalyzer.service.TsumegoService tsumegoService,
                           com.example.badukanalyzer.service.KataGoService kataGoService) {
        this.analysisService = analysisService;
        this.tsumegoService = tsumegoService;
        this.kataGoService = kataGoService;
    }

    @GetMapping("/about")
    public String about(org.springframework.ui.Model model) {
        // 소개 화면 통계 — 지어내지 않고 실제 저장/번들된 수치를 그대로 쓴다
        var gallery = analysisService.getGalleryItems();
        model.addAttribute("analyzedGames", gallery.size());
        model.addAttribute("tsumegoCount", tsumegoService.count());
        model.addAttribute("mistakeCount", analysisService.getUserMistakeNotes().size());
        model.addAttribute("avgMatchRate", gallery.isEmpty() ? 0.0
                : Math.round(gallery.stream().mapToDouble(g -> g.getMatchRate()).average().orElse(0) * 10) / 10.0);
        model.addAttribute("engineVisits", kataGoService.getAnalysisVisits());
        model.addAttribute("deepVisits", kataGoService.getDeepVisits());
        return "about";
    }
}
