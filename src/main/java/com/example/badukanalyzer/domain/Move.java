package com.example.badukanalyzer.domain;

public class Move {

    private final String color;
    private final int x;
    private final int y;

    public Move(String color, int x, int y) {
        this.color = color;
        this.x = x;
        this.y = y;
    }

    public String getColor() {
        return color;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}