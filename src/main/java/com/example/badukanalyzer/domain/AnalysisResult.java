package com.example.badukanalyzer.domain;

import java.util.List;

public class AnalysisResult {

    private final String filename;
    private final List<Move> moves;
    private final OpeningPhase opening;
    private final MiddlePhase middle;
    private final EndgamePhase endgame;

    public AnalysisResult(String filename, List<Move> moves, OpeningPhase opening, MiddlePhase middle, EndgamePhase endgame) {
        this.filename = filename;
        this.moves = moves;
        this.opening = opening;
        this.middle = middle;
        this.endgame = endgame;
    }

    public String getFilename() {
        return filename;
    }

    public List<Move> getMoves() {
        return moves;
    }

    public OpeningPhase getOpening() {
        return opening;
    }

    public MiddlePhase getMiddle() {
        return middle;
    }

    public EndgamePhase getEndgame() {
        return endgame;
    }

    public static class OpeningPhase {
        private final double matchRate;
        private final double avgWinRateLoss;
        private final double avgScoreLoss;

        public OpeningPhase(double matchRate, double avgWinRateLoss, double avgScoreLoss) {
            this.matchRate = matchRate;
            this.avgWinRateLoss = avgWinRateLoss;
            this.avgScoreLoss = avgScoreLoss;
        }

        public double getMatchRate() { return matchRate; }
        public double getAvgWinRateLoss() { return avgWinRateLoss; }
        public double getAvgScoreLoss() { return avgScoreLoss; }
    }

    public static class MiddlePhase {
        private final double matchRate;
        private final double avgWinRateLoss;
        private final double avgScoreLoss;

        public MiddlePhase(double matchRate, double avgWinRateLoss, double avgScoreLoss) {
            this.matchRate = matchRate;
            this.avgWinRateLoss = avgWinRateLoss;
            this.avgScoreLoss = avgScoreLoss;
        }

        public double getMatchRate() { return matchRate; }
        public double getAvgWinRateLoss() { return avgWinRateLoss; }
        public double getAvgScoreLoss() { return avgScoreLoss; }
    }

    public static class EndgamePhase {
        private final double matchRate;
        private final double avgWinRateLoss;
        private final double avgScoreLoss;

        public EndgamePhase(double matchRate, double avgWinRateLoss, double avgScoreLoss) {
            this.matchRate = matchRate;
            this.avgWinRateLoss = avgWinRateLoss;
            this.avgScoreLoss = avgScoreLoss;
        }

        public double getMatchRate() { return matchRate; }
        public double getAvgWinRateLoss() { return avgWinRateLoss; }
        public double getAvgScoreLoss() { return avgScoreLoss; }
    }
}
