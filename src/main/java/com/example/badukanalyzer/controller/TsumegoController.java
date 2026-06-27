package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.TsumegoProblem;
import com.example.badukanalyzer.service.TsumegoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tsumego")
public class TsumegoController {

    private final TsumegoService tsumegoService;

    public TsumegoController(TsumegoService tsumegoService) {
        this.tsumegoService = tsumegoService;
    }

    // 대기 위젯용 랜덤 사활 문제
    @GetMapping("/random")
    public ResponseEntity<TsumegoProblem> random() {
        TsumegoProblem p = tsumegoService.random();
        return p == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(p);
    }

    @GetMapping("/count")
    public Map<String, Integer> count() {
        return Map.of("count", tsumegoService.count());
    }
}
