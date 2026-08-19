package com.example.badukanalyzer.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 앱 시작 후 실시간 분석용 KataGo(play) 프로세스를 백그라운드로 예열.
 * TensorRT 백엔드는 첫 쿼리 때 엔진 초기화(~20초)가 걸려 첫 클릭이 느리다 →
 * 시작 시 빈 판을 한 번 분석해 미리 초기화해 두면 사용자 첫 요청부터 즉시 응답.
 */
@Component
public class EngineWarmup {

    private final KataGoService kataGoService;

    public EngineWarmup(KataGoService kataGoService) {
        this.kataGoService = kataGoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        Thread t = new Thread(() -> {
            long s = System.currentTimeMillis();
            // 첫 호출은 TRT 초기화(~20초) 동안 10초 데드라인을 넘겨 빈 결과일 수 있음 →
            // 결과가 나올 때까지 몇 번 재시도해 프로세스가 완전히 준비됐는지 확인.
            for (int i = 0; i < 4; i++) {
                try {
                    if (!kataGoService.getTopMoves(List.of(), 1).isEmpty()) {
                        System.out.println("[EngineWarmup] 실시간 엔진 예열 완료 (" + (System.currentTimeMillis() - s) + "ms)");
                        return;
                    }
                } catch (Exception e) {
                    System.out.println("[EngineWarmup] 예열 시도 " + (i + 1) + " 실패: " + e.getMessage());
                }
            }
            System.out.println("[EngineWarmup] 예열 미완료(첫 실제 요청 때 초기화됨)");
        }, "engine-warmup");
        t.setDaemon(true);
        t.start();
    }
}
