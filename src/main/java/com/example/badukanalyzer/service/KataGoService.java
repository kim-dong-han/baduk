package com.example.badukanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class KataGoService {

    @Value("${katago.path}")
    private String kataGoPath;

    @Value("${katago.model}")
    private String modelPath;

    @Value("${katago.config}")
    private String configPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<JsonNode> analyzeSgf(String sgfContent, int totalTurns) throws IOException {
        List<JsonNode> results = new ArrayList<>();

        ProcessBuilder pb = new ProcessBuilder(
                kataGoPath, "analysis",
                "-model", modelPath,
                "-config", configPath
        );

        // stderr도 캡처하여 디버깅에 도움
        pb.redirectErrorStream(true);

        Process process = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            // JSON Query for KataGo Analysis Engine
            ObjectNode query = objectMapper.createObjectNode();
            query.put("id", UUID.randomUUID().toString());
            query.put("initialSgf", sgfContent);
            query.put("boardXSize", 19);
            query.put("boardYSize", 19);
            
            // 모든 수 분석 요청
            List<Integer> analyzeTurns = new ArrayList<>();
            for (int i = 0; i <= totalTurns; i++) {
                analyzeTurns.add(i);
            }
            query.set("analyzeTurns", objectMapper.valueToTree(analyzeTurns));
            query.put("maxVisits", 100);

            String queryStr = query.toString();
            System.out.println("KataGo Query: " + queryStr);

            writer.write(queryStr);
            writer.newLine();
            writer.flush();
            writer.close();

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                if (line.startsWith("{")) {
                    try {
                        results.add(objectMapper.readTree(line));
                    } catch (Exception e) {
                        System.err.println("JSON 파싱 에러: " + line);
                    }
                } else {
                    System.out.println("KataGo Output: " + line);
                }
            }
        }

        return results;
    }
}
