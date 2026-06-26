package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.service.AnalysisJobStore;
import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/game")
public class SingleGameViewController {

    private final SingleGameService singleGameService;
    private final AnalysisJobStore jobStore;

    public SingleGameViewController(SingleGameService singleGameService, AnalysisJobStore jobStore) {
        this.singleGameService = singleGameService;
        this.jobStore = jobStore;
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
    public String analyze(@RequestParam String fileName, RedirectAttributes redirectAttrs) {
        String jobId = UUID.randomUUID().toString();
        jobStore.put(jobId, AnalysisJobStore.Job.running());
        singleGameService.analyzeAsync(jobId, fileName);
        redirectAttrs.addAttribute("fileName", fileName);
        return "redirect:/game/waiting/" + jobId;
    }

    @GetMapping("/waiting/{jobId}")
    public String waiting(@PathVariable String jobId, @RequestParam String fileName, Model model) {
        model.addAttribute("jobId", jobId);
        model.addAttribute("fileName", fileName);
        return "game/waiting";
    }

    @GetMapping("/status/{jobId}")
    @ResponseBody
    public ResponseEntity<Map<String, String>> status(@PathVariable String jobId) {
        AnalysisJobStore.Job job = jobStore.get(jobId);
        if (job == null) {
            return ResponseEntity.ok(Map.of("status", "UNKNOWN"));
        }
        return switch (job.status) {
            case RUNNING -> ResponseEntity.ok(Map.of("status", "RUNNING", "progress", String.valueOf(job.progress)));
            case DONE    -> ResponseEntity.ok(Map.of("status", "DONE", "resultId", job.resultId));
            case ERROR   -> ResponseEntity.ok(Map.of("status", "ERROR", "error", job.error != null ? job.error : "알 수 없는 오류"));
        };
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
