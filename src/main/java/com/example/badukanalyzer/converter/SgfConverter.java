package com.example.badukanalyzer.converter;

import com.example.badukanalyzer.domain.Move;
import com.example.badukanalyzer.util.CoordinateConverter;

import java.util.List;

public class SgfConverter {

    public String convert(List<Move> moves) {

        StringBuilder sb = new StringBuilder();

        sb.append("(;");
        sb.append("GM[1]");
        sb.append("FF[4]");
        sb.append("SZ[19]");

        for (Move move : moves) {

            sb.append(";")
                    .append(move.getColor())
                    .append("[")
                    .append(CoordinateConverter.toSgfX(move.getX()))
                    .append(CoordinateConverter.toSgfY(move.getY()))
                    .append("]");
        }

        sb.append(")");

        return sb.toString();
    }
}