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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@Service
public class KataGoService {

    @Value("${katago.path}")
    private String kataGoPath;

    @Value("${katago.model}")
    private String modelPath;

    @Value("${katago.config}")
    private String configPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record HybridGameResult(List<JsonNode> winrateNodes, List<JsonNode> qualityNodes) {}

    /**
     * 하이브리드 분석: 한 KataGo 세션에서 두 종류 쿼리를 전송
     *  - 매 수, visits=1  → 형세 변동 (winrateNodes)
     *  - 10수마다, visits=30 → 일치율 (qualityNodes)
     */
    public List<HybridGameResult> analyzeMultipleGamesHybrid(List<List<Move>> allGames) throws IOException {
        List<String> gameIds = new ArrayList<>();
        int totalAnalyzeTurns = 0;

        System.out.println("KataGo 하이브리드 분석 시작 (" + allGames.size() + "개 기보)");
        ProcessBuilder pb = new ProcessBuilder(kataGoPath, "analysis", "-model", modelPath, "-config", configPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        for (int gi = 0; gi < allGames.size(); gi++) {
            List<Move> moves = allGames.get(gi);
            String gameId = UUID.randomUUID().toString();
            gameIds.add(gameId);

            int totalMoves = moves.size();

            // 공통 moves 배열
            ArrayNode movesArray = objectMapper.createArrayNode();
            for (Move move : moves) {
                ArrayNode entry = movesArray.addArray();
                entry.add(move.getColor());
                entry.add(CoordinateConverter.toGtpCoord(move));
            }

            // 쿼리 1: 매 수, visits=1 (형세 변동용)
            List<Integer> allTurns = new ArrayList<>();
            for (int t = 0; t <= totalMoves; t++) allTurns.add(t);
            writer.write(buildQuery("wr_" + gameId, movesArray, allTurns, 1).toString());
            writer.newLine();
            totalAnalyzeTurns += allTurns.size();

            // 쿼리 2: 10수마다, visits=10 (일치율용)
            List<Integer> checkTurns = new ArrayList<>();
            for (int t = 0; t <= totalMoves; t += 10) checkTurns.add(t);
            if (!checkTurns.contains(totalMoves)) checkTurns.add(totalMoves);
            writer.write(buildQuery("q_" + gameId, movesArray, checkTurns, 30).toString());
            writer.newLine();
            totalAnalyzeTurns += checkTurns.size();

            System.out.println("  [" + (gi + 1) + "/" + allGames.size() + "] 쿼리 전송 (" + totalMoves + "수, wr:" + allTurns.size() + " q:" + checkTurns.size() + "): " + gameId);
        }
        writer.flush();
        writer.close();

        System.out.println("전체 쿼리 전송 완료, 결과 수신 대기 중...");
        List<JsonNode> allJsonLines = collectResults(process);

        int timeoutSeconds = Math.max(60, totalAnalyzeTurns * 2 + 30);
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("KataGo 타임아웃 (" + timeoutSeconds + "초)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("KataGo 인터럽트", e);
        }

        System.out.println("KataGo 결과 수신 완료 (" + allJsonLines.size() + "개 JSON 라인)");

        List<HybridGameResult> results = new ArrayList<>();
        for (String gid : gameIds) {
            List<JsonNode> wrNodes = new ArrayList<>();
            List<JsonNode> qNodes  = new ArrayList<>();
            String wrId = "wr_" + gid, qId = "q_" + gid;
            for (JsonNode node : allJsonLines) {
                if (!node.has("id")) continue;
                String nodeId = node.get("id").asText();
                if (wrId.equals(nodeId))     wrNodes.add(node);
                else if (qId.equals(nodeId)) qNodes.add(node);
            }
            results.add(new HybridGameResult(wrNodes, qNodes));
        }
        return results;
    }

    private ObjectNode buildQuery(String id, ArrayNode movesArray, List<Integer> analyzeTurns, int maxVisits) {
        ObjectNode query = objectMapper.createObjectNode();
        query.put("id", id);
        query.put("boardXSize", 19);
        query.put("boardYSize", 19);
        query.put("rules", "chinese");
        query.put("komi", 7.5);
        query.set("moves", movesArray.deepCopy());
        query.set("analyzeTurns", objectMapper.valueToTree(analyzeTurns));
        query.put("maxVisits", maxVisits);
        return query;
    }

    private List<JsonNode> collectResults(Process process) throws IOException {
        List<JsonNode> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("{")) {
                    try { lines.add(objectMapper.readTree(line)); }
                    catch (Exception e) { System.err.println("JSON 파싱 에러: " + line); }
                } else {
                    System.out.println("KataGo: " + line);
                }
            }
        }
        return lines;
    }

    public List<List<JsonNode>> analyzeMultipleGames(List<List<Move>> allGames) throws IOException {
        List<HybridGameResult> hybrid = analyzeMultipleGamesHybrid(allGames);
        List<List<JsonNode>> result = new ArrayList<>();
        for (HybridGameResult h : hybrid) result.add(h.winrateNodes());
        return result;
    }

    public List<JsonNode> analyzeMoves(List<Move> moves) throws IOException {
        return analyzeMultipleGames(List.of(moves)).getFirst();
    }

    // 단일 기보 전수 분석 - 진행률 콜백으로 실시간 % 보고
    public List<JsonNode> analyzeAllMoves(List<Move> moves, IntConsumer progressCallback) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(kataGoPath, "analysis", "-model", modelPath, "-config", configPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        ArrayNode movesArray = objectMapper.createArrayNode();
        for (Move move : moves) {
            ArrayNode entry = movesArray.addArray();
            entry.add(move.getColor());
            entry.add(CoordinateConverter.toGtpCoord(move));
        }

        List<Integer> allTurns = new ArrayList<>();
        for (int t = 0; t <= moves.size(); t++) allTurns.add(t);
        int totalTurns = allTurns.size();

        String queryId = UUID.randomUUID().toString();
        writer.write(buildQuery(queryId, movesArray, allTurns, 200).toString());
        writer.newLine();
        writer.flush();
        writer.close();

        System.out.println("단일 기보 전수 분석 시작 (" + moves.size() + "수, visits=200)");

        // 결과를 한 줄씩 받으면서 progress 갱신
        List<JsonNode> allLines = new ArrayList<>();
        int received = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("{")) {
                    try {
                        allLines.add(objectMapper.readTree(line));
                        received++;
                        if (progressCallback != null && totalTurns > 0) {
                            progressCallback.accept(Math.min(99, received * 100 / totalTurns));
                        }
                    } catch (Exception e) { System.err.println("JSON 파싱 에러: " + line); }
                } else {
                    System.out.println("KataGo: " + line);
                }
            }
        }

        int timeoutSeconds = Math.max(120, moves.size() * 4 + 60);
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("KataGo 타임아웃 (" + timeoutSeconds + "초)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("KataGo 인터럽트", e);
        }

        return allLines.stream()
                .filter(n -> n.has("id") && queryId.equals(n.get("id").asText()))
                .filter(n -> n.has("turnNumber"))
                .sorted(java.util.Comparator.comparingInt(n -> n.get("turnNumber").asInt()))
                .collect(java.util.stream.Collectors.toList());
    }

    public List<JsonNode> analyzeAllMoves(List<Move> moves) throws IOException {
        return analyzeAllMoves(moves, null);
    }
}
