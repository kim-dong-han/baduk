package com.example.badukanalyzer.parser;

import com.example.badukanalyzer.domain.Move;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SgfParser {

    private static final int BOARD_SIZE = 19;
    private static final String SGF_CHARS = "abcdefghijklmnopqrs";

    public List<Move> parse(String filePath) throws IOException {
        String content = Files.readString(Path.of(filePath));
        content = content.replaceAll("\\s+", "");

        int mainStart = content.indexOf('(');
        int mainEnd = content.lastIndexOf(')');
        if (mainStart < 0 || mainEnd < 0) {
            throw new IOException("Invalid SGF: missing parentheses");
        }
        content = content.substring(mainStart, mainEnd + 1);

        content = stripVariations(content);
        content = stripProperties(content);

        List<Move> moves = new ArrayList<>();
        int i = 0;
        while (i < content.length()) {
            if (content.charAt(i) == ';') {
                i++;
                continue;
            }
            if ((content.charAt(i) == 'B' || content.charAt(i) == 'W')
                    && i + 1 < content.length() && content.charAt(i + 1) == '[') {
                String color = String.valueOf(content.charAt(i));
                int close = content.indexOf(']', i + 2);
                if (close < 0) break;
                String coordStr = content.substring(i + 2, close);
                if (coordStr.length() >= 2) {
                    char colChar = coordStr.charAt(0);
                    char rowChar = coordStr.charAt(1);
                    int colIdx = SGF_CHARS.indexOf(colChar);
                    int rowIdx = SGF_CHARS.indexOf(rowChar);
                    if (colIdx >= 0 && rowIdx >= 0) {
                        int x = colIdx;
                        int y = BOARD_SIZE - 1 - rowIdx;
                        moves.add(new Move(color, x, y));
                    }
                }
                i = close + 1;
            } else {
                i++;
            }
        }

        return moves;
    }

    private String stripVariations(String s) {
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder mainLine = new StringBuilder();
        int pos = 0;
        while (pos < s.length()) {
            while (pos < s.length() && s.charAt(pos) != '(' && s.charAt(pos) != ')') {
                mainLine.append(s.charAt(pos));
                pos++;
            }
            if (pos >= s.length()) break;
            if (s.charAt(pos) == ')') { pos++; continue; }
            int depth = 1;
            int childStart = pos;
            pos++;
            while (pos < s.length() && depth > 0) {
                if (s.charAt(pos) == '(') depth++;
                else if (s.charAt(pos) == ')') depth--;
                pos++;
            }
            int childEnd = pos;
            while (pos < s.length() && s.charAt(pos) == '(') {
                depth = 1;
                pos++;
                while (pos < s.length() && depth > 0) {
                    if (s.charAt(pos) == '(') depth++;
                    else if (s.charAt(pos) == ')') depth--;
                    pos++;
                }
            }
            s = s.substring(childStart + 1, childEnd - 1);
            pos = 0;
        }
        return mainLine.toString();
    }
    private String stripProperties(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ';') {
                sb.append(c);
                i++;
                continue;
            }
            if ((c == 'B' || c == 'W') && i + 1 < s.length() && s.charAt(i + 1) == '[') {
                sb.append(c);
                i++;
                int close = findCloseBracket(s, i);
                sb.append(s, i, close + 1);
                i = close + 1;
                continue;
            }
            i++;
        }
        return sb.toString();
    }

    private int findCloseBracket(String s, int start) {
        int i = start + 1;
        while (i < s.length()) {
            if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                i += 2;
            } else if (s.charAt(i) == ']') {
                return i;
            } else {
                i++;
            }
        }
        return s.length() - 1;
    }
}
