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
        if (problems.isEmpty()) return null;
        return problems.get(ThreadLocalRandom.current().nextInt(problems.size()));
    }
}
