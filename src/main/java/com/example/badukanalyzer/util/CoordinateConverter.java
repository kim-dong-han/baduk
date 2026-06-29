package com.example.badukanalyzer.util;

import com.example.badukanalyzer.domain.Move;

public class CoordinateConverter {

    private static final int BOARD_SIZE = 19;
    private static final char[] SGF_CHARS = "abcdefghijklmnopqrs".toCharArray();

    public static String toSgfCoord(Move move) {
        return toSgfCoord(move.getX(), move.getY());
    }

    public static String toSgfCoord(int x, int y) {
        if (x < 0 || y < 0 || x >= BOARD_SIZE || y >= BOARD_SIZE) {
            return "";
        }
        return "" + SGF_CHARS[x] + SGF_CHARS[BOARD_SIZE - 1 - y];
    }

    public static char toSgfX(int x) {
        if (x < 0 || x >= BOARD_SIZE) return 0;
        return SGF_CHARS[x];
    }

    public static char toSgfY(int y) {
        if (y < 0 || y >= BOARD_SIZE) return 0;
        return SGF_CHARS[BOARD_SIZE - 1 - y];
    }

    public static String toGtpCoord(Move move) {
        return toGtpCoord(move.getX(), move.getY());
    }

    public static String toGtpCoord(int x, int y) {
        if (x < 0 || y < 0 || x >= BOARD_SIZE || y >= BOARD_SIZE) {
            return "pass";
        }
        char col = (x < 8) ? (char) ('A' + x) : (char) ('A' + x + 1);
        int row = y + 1;
        return "" + col + row;
    }

    public static Move fromGtp(String color, String gtp) {
        if (gtp == null || gtp.isEmpty() || "pass".equalsIgnoreCase(gtp)) {
            return new Move(color, -1, -1);
        }
        char col = Character.toUpperCase(gtp.charAt(0));
        int x = col - 'A';
        if (x >= 8) x--;
        int y = Integer.parseInt(gtp.substring(1)) - 1;
        return new Move(color, x, y);
    }
}
