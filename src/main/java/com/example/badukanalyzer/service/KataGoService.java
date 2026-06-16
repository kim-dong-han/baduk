package com.example.badukanalyzer.service;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.util.CoordinateConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class KataGoService {

    @Value("${katago.path}")
    private String kataGoPath;

    @Value("${katago.model}")
    private String modelPath;

    @Value("${katago.config}")
    private String configPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<List<JsonNode>> analyzeMultipleGames(List<List<Move>> allGames) throws IOException {
        List<String> gameIds = new ArrayList<>();
        int totalAnalyzeTurns = 0;

        System.out.println("KataGo 프로세스 시작 (" + allGames.size() + "개 기보)");
        ProcessBuilder pb = new ProcessBuilder(
                kataGoPath, "analysis",
                "-model", modelPath,
                "-config", configPath
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        for (int gi = 0; gi < allGames.size(); gi++) {
            List<Move> moves = allGames.get(gi);
            ObjectNode query = objectMapper.createObjectNode();
            String id = UUID.randomUUID().toString();
            gameIds.add(id);
            query.put("id", id);
            query.put("boardXSize", 19);
            query.put("boardYSize", 19);
            query.put("rules", "chinese");
            query.put("komi", 7.5);

            ArrayNode movesArray = query.putArray("moves");
            for (Move move : moves) {
                ArrayNode moveEntry = movesArray.addArray();
                moveEntry.add(move.getColor());
                moveEntry.add(CoordinateConverter.toGtpCoord(move));
            }

            int totalMoves = moves.size();
            List<Integer> analyzeTurns = new ArrayList<>();
            analyzeTurns.add(0);
            analyzeTurns.add(50);
            if (totalMoves > 100) analyzeTurns.add(100);
            int last = totalMoves / 20 * 20;
            if (last > 0 && !analyzeTurns.contains(last)) analyzeTurns.add(last);
            if (!analyzeTurns.contains(totalMoves)) analyzeTurns.add(totalMoves);

            query.set("analyzeTurns", objectMapper.valueToTree(analyzeTurns));
            query.put("maxVisits", 20);

            totalAnalyzeTurns += analyzeTurns.size();

            System.out.println("  [" + (gi + 1) + "/" + allGames.size() + "] 쿼리 전송 (" + moves.size() + "수, " + analyzeTurns.size() + "개 분석지점): " + id);

            writer.write(query.toString());
            writer.newLine();
        }
        writer.flush();
        writer.close();

        System.out.println("전체 쿼리 전송 완료, 결과 수신 대기 중...");
        List<JsonNode> allJsonLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("{")) {
                    try {
                        allJsonLines.add(objectMapper.readTree(line));
                    } catch (Exception e) {
                        System.err.println("JSON 파싱 에러: " + line);
                    }
                } else {
                    System.out.println("KataGo: " + line);
                }
            }
        }

        int timeoutSeconds = Math.max(60, totalAnalyzeTurns * 2 + 30);
        System.out.println("KataGo 분석 대기 중... (최대 " + timeoutSeconds + "초, " + totalAnalyzeTurns + "개 분석지점)");

        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("KataGo 프로세스 타임아웃 (" + timeoutSeconds + "초, " + allGames.size() + "개 기보, " + totalAnalyzeTurns + "개 분석지점)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("KataGo 분석 중 인터럽트 발생", e);
        }

        System.out.println("KataGo 결과 수신 완료 (" + allJsonLines.size() + "개 JSON 라인)");

        List<List<JsonNode>> allResults = new ArrayList<>();
        for (String gid : gameIds) {
            List<JsonNode> gameResults = new ArrayList<>();
            for (JsonNode node : allJsonLines) {
                if (node.has("id") && gid.equals(node.get("id").asText())) {
                    gameResults.add(node);
                }
            }
            allResults.add(gameResults);
        }
        return allResults;
    }
    
    public List<JsonNode> analyzeMoves(List<Move> moves) throws IOException {
        List<List<Move>> single = List.of(moves);
        return analyzeMultipleGames(single).getFirst();
    }
}
