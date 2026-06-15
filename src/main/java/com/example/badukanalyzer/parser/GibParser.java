package com.example.badukanalyzer.parser;

import com.example.badukanalyzer.domain.Move;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GibParser {

    public List<Move> parse(String filePath) throws IOException {

        List<Move> moves = new ArrayList<>();

        try (
                BufferedReader br =
                        new BufferedReader(
                                new InputStreamReader(
                                        new FileInputStream(filePath),
                                        Charset.forName("MS949")
                                )
                        )
        ) {

            String line;

            while ((line = br.readLine()) != null) {

                if (!line.startsWith("STO")) {
                    continue;
                }

                String[] parts = line.trim().split("\\s+");

                int player = Integer.parseInt(parts[3]);
                int x = Integer.parseInt(parts[4]);
                int y = Integer.parseInt(parts[5]);

                String color = player == 1 ? "B" : "W";

                moves.add(new Move(color, x, y));
            }
        }

        return moves;
    }
}