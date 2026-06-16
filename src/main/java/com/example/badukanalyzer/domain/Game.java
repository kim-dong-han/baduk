package com.example.badukanalyzer.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Game {

    private final String filename;
    private final List<Move> moves;

    public Game(String filename) {
        this.filename = filename;
        this.moves = new ArrayList<>();
    }

    public void addMove(Move move) {
        moves.add(move);
    }

    public String getFilename() {
        return filename;
    }

    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    public int getTotalMoves() {
        return moves.size();
    }
}
