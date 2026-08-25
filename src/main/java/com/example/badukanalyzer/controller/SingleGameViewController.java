package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.service.AnalysisJobStore;
import com.example.badukanalyzer.service.KataGoService;
import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/game")
public class SingleGameViewController {

    private final SingleGameService singleGameService;
    private final AnalysisJobStore jobStore;
    private final KataGoService kataGoService;   // 대기 화면 visits 표시용

    public SingleGameViewController(SingleGameService singleGameService, AnalysisJobStore jobStore,
                                    KataGoService kataGoService) {
        this.singleGameService = singleGameService;
        this.jobStore = jobStore;
        this.kataGoService = kataGoService;
    }

    @GetMapping
    public String index(Model model) throws Exception {
        List<String> files = singleGameService.listGameFiles();
        List<String> proFiles = singleGameService.listProGameFiles();
        List<SingleGameResult> results = singleGameService.listResultSummaries();

        // 목록 표에서 파일명으로 바로 결과를 찾도록 색인(이미 파일명 기준 최신만 남은 목록).
        Map<String, SingleGameResult> resultByFile = new LinkedHashMap<>();
        Map<String, Double> accuracyByFile = new LinkedHashMap<>();
        double accSum = 0;
        long durationMs = 0;
        for (SingleGameResult r : results) {
            if (r.getFileName() == null || resultByFile.containsKey(r.getFileName())) continue;
            resultByFile.put(r.getFileName(), r);
            Double acc = overallMatchRate(r);
            if (acc != null) {
                accuracyByFile.put(r.getFileName(), acc);
                accSum += acc;
            }
            if (r.getAnalysisDurationMs() != null) durationMs += r.getAnalysisDurationMs();
        }

        model.addAttribute("files", files);
        model.addAttribute("proFiles", proFiles);
        model.addAttribute("results", results);
        model.addAttribute("resultMap", singleGameService.getResultMap());
        // 아래 4개는 목록 화면 표시용(서비스·DTO 변경 없이 위 결과에서 계산)
        model.addAttribute("resultByFile", resultByFile);
        model.addAttribute("accuracyByFile", accuracyByFile);
        model.addAttribute("avgAccuracy", accuracyByFile.isEmpty() ? null : accSum / accuracyByFile.size());
        model.addAttribute("totalAnalysisMinutes", Math.round(durationMs / 60000.0));
        return "game/index";
    }

    /** 구간(초·중·종반) 최선수 일치율을 수 개수로 가중평균 — 한 판의 전체 일치율. */
    private static Double overallMatchRate(SingleGameResult r) {
        SingleGameResult.PhaseStats[] phases = { r.getOpening(), r.getMiddle(), r.getEndgame() };
        double num = 0, den = 0;
        for (SingleGameResult.PhaseStats p : phases) {
            if (p == null || p.getMoveCount() <= 0) continue;
            num += p.getMatchRate() * p.getMoveCount();
            den += p.getMoveCount();
        }
        return den == 0 ? null : num / den;
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
        // 대기 화면 표시용 — 진행률(%)만으로는 "몇 수째"를 알 수 없어 총 수를 함께 넘긴다.
        int totalMoves = 0;
        try {
            totalMoves = singleGameService.getRawMoves(fileName).size();
        } catch (Exception ignored) {
            // 파싱 실패해도 대기 화면은 그대로 뜬다(총 수만 숨김)
        }
        model.addAttribute("totalMoves", totalMoves);
        model.addAttribute("analysisVisits", kataGoService.getAnalysisVisits());
        model.addAttribute("deepVisits", kataGoService.getDeepVisits());
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
