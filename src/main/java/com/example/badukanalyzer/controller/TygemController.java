package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.service.TygemCrawlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tygem")
@RequiredArgsConstructor
public class TygemController {

    private final TygemCrawlerService tygemCrawlerService;

    @PostMapping("/fetch")
    public ResponseEntity<?> fetch(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "아이디와 비밀번호를 입력해주세요."));
        }

        boolean started = tygemCrawlerService.startFetch(username, password);
        if (!started) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 실행 중입니다."));
        }

        return ResponseEntity.ok(Map.of("started", true));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of(
            "running", tygemCrawlerService.isRunning(),
            "statusMessage", tygemCrawlerService.getStatusMessage(),
            "downloadCount", tygemCrawlerService.getDownloadCount(),
            "errorMessage", tygemCrawlerService.getErrorMessage() != null ? tygemCrawlerService.getErrorMessage() : ""
        ));
    }
}