package com.example.badukanalyzer.converter;

import com.example.badukanalyzer.domain.Move;

import java.util.List;

public class SgfConverter {

    private static final char[] SGF =
            "abcdefghijklmnopqrs".toCharArray();

    public String convert(List<Move> moves) {

        StringBuilder sb = new StringBuilder();

        sb.append("(;");
        sb.append("GM[1]");
        sb.append("FF[4]");
        sb.append("SZ[19]");

        for (Move move : moves) {

            char x = SGF[move.getX()];

            // 타이젬 -> SGF 변환
            char y = SGF[18 - move.getY()];

            sb.append(";")
                    .append(move.getColor())
                    .append("[")
                    .append(x)
                    .append(y)
                    .append("]");
        }

        sb.append(")");

        return sb.toString();
    }
}