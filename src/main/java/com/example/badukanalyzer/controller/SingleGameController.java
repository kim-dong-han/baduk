package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class SingleGameController {

    private final SingleGameService singleGameService;

    public SingleGameController(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    // 분석 가능한 기보 파일 목록
    @GetMapping("/files")
    public ResponseEntity<List<String>> listFiles() {
        return ResponseEntity.ok(singleGameService.listGameFiles());
    }

    // 단일 기보 분석 실행 (fileName: record-dir 내 파일명)
    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody Map<String, String> body) {
        String fileName = body.get("fileName");
        if (fileName == null || fileName.isBlank()) {
            return ResponseEntity.badRequest().body("fileName 필드가 필요합니다.");
        }
        try {
            SingleGameResult result = singleGameService.analyze(fileName);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("분석 실패: " + e.getMessage());
        }
    }

    // 저장된 분석 결과 목록
    @GetMapping("/results")
    public ResponseEntity<?> listResults() {
        try {
            return ResponseEntity.ok(singleGameService.listResults());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("목록 조회 실패: " + e.getMessage());
        }
    }

    // 특정 분석 결과 조회
    @GetMapping("/result/{id}")
    public ResponseEntity<?> getResult(@PathVariable String id) {
        try {
            return ResponseEntity.ok(singleGameService.getResult(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
