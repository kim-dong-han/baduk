package com.example.badukanalyzer.parser;

import com.example.badukanalyzer.dto.TsumegoProblem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사활 SGF 한 개를 TsumegoProblem으로 변환한다.
 *  - 초기 배치: AB[](흑) / AW[](백)
 *  - 차례: PL[]  (없으면 첫 수 색)
 *  - 정답 첫 수: 메인 라인의 첫 착수
 *  - 정해: 메인 라인 착수열 (첫 변화도 '(' 이전까지)
 *  - region: 돌들의 바운딩 박스를 코너로 확장(확대 표시용)
 */
public class TsumegoSgfParser {

    private static final String COLS = "ABCDEFGHJKLMNOPQRST";

    public TsumegoProblem parse(String content, String id) {
        List<TsumegoProblem.Stone> stones = new ArrayList<>();
        List<int[]> grids = new ArrayList<>();   // region 계산용 [x,y]

        for (String v : propValues(content, "AB")) addStone(stones, grids, "B", v);
        for (String v : propValues(content, "AW")) addStone(stones, grids, "W", v);

        // 메인 라인 착수 (첫 변화도 이전까지)
        String moveSeg = mainLineSegment(content);
        List<String> moveColors = new ArrayList<>();
        List<String> moveGtp = new ArrayList<>();
        Matcher mv = Pattern.compile(";\\s*([BW])\\[([a-s]{2})\\]").matcher(moveSeg);
        while (mv.find()) {
            moveColors.add(mv.group(1));
            String gtp = sgfToGtp(mv.group(2));
            moveGtp.add(gtp);
            int[] g = sgfToGrid(mv.group(2));
            if (g != null) grids.add(g);
        }

        String toPlay = firstValue(content, "PL");
        if (toPlay == null || !(toPlay.equalsIgnoreCase("B") || toPlay.equalsIgnoreCase("W"))) {
            toPlay = moveColors.isEmpty() ? "B" : moveColors.get(0);
        }
        toPlay = toPlay.toUpperCase();

        // 정답 = 메인 라인 첫 착수, 정해 = 메인 라인 전체
        List<String> answers = new ArrayList<>();
        if (!moveGtp.isEmpty() && moveGtp.get(0) != null) answers.add(moveGtp.get(0));

        String prompt = firstValue(content, "C");
        if (prompt == null || prompt.isBlank()) {
            prompt = "B".equals(toPlay) ? "흑 차례입니다" : "백 차례입니다";
        }

        return TsumegoProblem.builder()
                .id(id)
                .difficulty("쉬움")
                .toPlay(toPlay)
                .prompt(prompt.trim())
                .stones(stones)
                .answers(answers)
                .solution(moveGtp)
                .region(computeRegion(grids))
                .build();
    }

    private void addStone(List<TsumegoProblem.Stone> stones, List<int[]> grids, String color, String sgf) {
        String gtp = sgfToGtp(sgf);
        if (gtp == null) return;
        stones.add(new TsumegoProblem.Stone(color, gtp));
        int[] g = sgfToGrid(sgf);
        if (g != null) grids.add(g);
    }

    // KEY[..][..].. 의 모든 값 (KEY 앞이 알파벳이면 다른 속성의 꼬리이므로 제외)
    private List<String> propValues(String content, String key) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("(?<![A-Za-z])" + key + "((?:\\s*\\[[^\\]]*\\])+)").matcher(content);
        if (m.find()) {
            Matcher v = Pattern.compile("\\[([^\\]]*)\\]").matcher(m.group(1));
            while (v.find()) out.add(v.group(1));
        }
        return out;
    }

    private String firstValue(String content, String key) {
        List<String> vals = propValues(content, key);
        return vals.isEmpty() ? null : vals.get(0);
    }

    // 첫 착수부터 첫 변화도 '(' 이전까지 = 메인 라인
    private String mainLineSegment(String content) {
        Matcher first = Pattern.compile(";\\s*[BW]\\[[a-s]{2}\\]").matcher(content);
        if (!first.find()) return "";
        int start = first.start();
        int branch = content.indexOf('(', start);
        return branch < 0 ? content.substring(start) : content.substring(start, branch);
    }

    private static String sgfToGtp(String s) {
        if (s == null || s.length() < 2) return null;
        int col = s.charAt(0) - 'a';
        int rowIdx = s.charAt(1) - 'a';
        if (col < 0 || col > 18 || rowIdx < 0 || rowIdx > 18) return null;
        return "" + COLS.charAt(col) + (19 - rowIdx);
    }

    // SGF → 격자 [x(0..18 좌→우), y(0..18 하단기준)]
    private static int[] sgfToGrid(String s) {
        if (s == null || s.length() < 2) return null;
        int col = s.charAt(0) - 'a';
        int rowIdx = s.charAt(1) - 'a';
        if (col < 0 || col > 18 || rowIdx < 0 || rowIdx > 18) return null;
        return new int[]{col, 18 - rowIdx};
    }

    // 바운딩 박스 + 1칸 여유, 가장자리 근처(≤2)는 변까지 스냅
    private int[] computeRegion(List<int[]> grids) {
        if (grids.isEmpty()) return new int[]{0, 0, 18, 18};
        int xMin = 18, yMin = 18, xMax = 0, yMax = 0;
        for (int[] g : grids) {
            xMin = Math.min(xMin, g[0]); xMax = Math.max(xMax, g[0]);
            yMin = Math.min(yMin, g[1]); yMax = Math.max(yMax, g[1]);
        }
        xMin = (xMin <= 2) ? 0 : xMin - 1;
        yMin = (yMin <= 2) ? 0 : yMin - 1;
        xMax = (xMax >= 16) ? 18 : xMax + 1;
        yMax = (yMax >= 16) ? 18 : yMax + 1;
        return new int[]{xMin, yMin, xMax, yMax};
    }
}
