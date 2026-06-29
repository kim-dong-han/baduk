package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.TsumegoProblem;
import com.example.badukanalyzer.parser.TsumegoSgfParser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TsumegoService {

    private final TsumegoSgfParser parser = new TsumegoSgfParser();
    private volatile List<TsumegoProblem> problems = List.of();

    @PostConstruct
    public void load() {
        List<TsumegoProblem> loaded = new ArrayList<>();
        try {
            // 번들된 사활 SGF (src/main/resources/tsumego/*.sgf). 사용자가 공개 사활집을 더 넣으면 자동 인식.
            Resource[] res = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:tsumego/*.sgf");
            for (Resource r : res) {
                try {
                    String content = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    String id = r.getFilename() == null ? "tsume" : r.getFilename().replaceAll("\\.sgf$", "");
                    TsumegoProblem p = parser.parse(content, id);
                    if (p.getAnswers() != null && !p.getAnswers().isEmpty()) loaded.add(p);
                    else log.warn("사활 문제 정답 추출 실패, 건너뜀: {}", id);
                } catch (Exception e) {
                    log.warn("사활 SGF 파싱 실패 {}: {}", r.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("사활 SGF 로드 실패: {}", e.getMessage());
        }
        problems = loaded;
        log.info("사활 문제 {}개 로드됨", problems.size());
    }

    public int count() { return problems.size(); }

    public TsumegoProblem random() {
        return random(null, null);
    }

    /**
     * 난이도로 거르고(없으면 전체), 직전 문제(excludeId)는 제외해 항상 새 문제를 준다.
     * 필터/제외 후 후보가 없으면 단계적으로 완화한다. (문제 수에 인위적 제한 없음)
     */
    public TsumegoProblem random(String difficulty, String excludeId) {
        if (problems.isEmpty()) return null;

        boolean filterDiff = difficulty != null && !difficulty.isBlank() && !"전체".equals(difficulty);
        List<TsumegoProblem> pool = problems.stream()
                .filter(p -> !filterDiff || difficulty.equals(p.getDifficulty()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (pool.isEmpty()) pool = new ArrayList<>(problems);   // 해당 난이도 없음 → 전체로 완화

        if (excludeId != null && pool.size() > 1) {
            pool.removeIf(p -> excludeId.equals(p.getId()));    // 직전 문제 제외 (단, 마지막 1개면 유지)
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
