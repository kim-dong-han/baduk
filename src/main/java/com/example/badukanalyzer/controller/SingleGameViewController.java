package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/game")
public class SingleGameViewController {

    private final SingleGameService singleGameService;

    public SingleGameViewController(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    @GetMapping
    public String index(Model model) throws Exception {
        model.addAttribute("files", singleGameService.listGameFiles());
        model.addAttribute("proFiles", singleGameService.listProGameFiles());
        model.addAttribute("results", singleGameService.listResults());
        model.addAttribute("resultMap", singleGameService.getResultMap());
        return "game/index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String fileName, Model model) {
        try {
            var result = singleGameService.analyze(fileName);
            return "redirect:/game/result/" + result.getId();
        } catch (Exception e) {
            model.addAttribute("error", "분석 실패: " + e.getMessage());
            try { model.addAttribute("files", singleGameService.listGameFiles()); } catch (Exception ignored) {}
            return "game/index";
        }
    }

    @GetMapping("/result/{id}")
    public String result(@PathVariable String id, Model model) {
        try {
            model.addAttribute("result", singleGameService.getResult(id));
            return "game/result";
        } catch (Exception e) {
            return "redirect:/game";
        }
    }
}
