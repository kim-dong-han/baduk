package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.service.AnalysisJobStore;
import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
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
        model.addAttribute("results", singleGameService.listResultSummaries());
        model.addAttribute("resultMap", singleGameService.getResultMap());
        return "game/index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String fileName, RedirectAttributes redirectAttrs) {
        String jobId = UUID.randomUUID().toString();
        jobStore.put(jobId, AnalysisJobStore.Job.running(fileName));
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

    @GetMapping("/running-jobs")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> runningJobs() {
        List<Map<String, Object>> list = jobStore.getRunningJobs().stream()
                .map(j -> Map.<String, Object>of("fileName", j.fileName, "progress", j.progress))
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(list);
    }

    /** 저장된 모든 기보를 현재 visits 설정으로 일괄 재분석 시작(백그라운드). 이미 실행 중이면 무시. */
    @PostMapping("/reanalyze-all")
    public String reanalyzeAll() {
        singleGameService.startBulkReanalyze();
        return "redirect:/game?bulk=1";
    }

    @GetMapping("/reanalyze-all/status")
    @ResponseBody
    public Map<String, Object> bulkStatus() {
        SingleGameService.BulkStatus b = singleGameService.getBulkStatus();
        Map<String, Object> m = new HashMap<>();
        m.put("running", b.running);
        m.put("total", b.total);
        m.put("done", b.done);
        m.put("current", b.current);
        m.put("message", b.message);
        return m;
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

    /** 분석 결과를 주석 SGF로 내려받기 (등급·집손해·최선수 코멘트 포함) */
    @GetMapping("/result/{id}/sgf")
    @ResponseBody
    public ResponseEntity<byte[]> exportSgf(@PathVariable String id) {
        try {
            String sgf = singleGameService.buildAnnotatedSgf(singleGameService.getResult(id));
            byte[] bytes = sgf.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String fname = "baduk-review-" + (id.length() >= 8 ? id.substring(0, 8) : id) + ".sgf";
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fname + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/x-go-sgf"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
