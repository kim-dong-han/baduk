package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.SingleGameResult;
import com.example.badukanalyzer.service.SingleGameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final SingleGameService singleGameService;

    public HomeController(SingleGameService singleGameService) {
        this.singleGameService = singleGameService;
    }

    // 로그인 후 진입하는 메인(허브) 화면. 여기서 원하는 기능 페이지로 이동.
    @GetMapping("/")
    public String home(Model model) {
        List<SingleGameResult> recent = List.of();
        int totalGames = 0;
        try {
            List<SingleGameResult> all = singleGameService.listResultSummaries();
            totalGames = all.size();
            recent = all.stream().limit(4).toList();
        } catch (Exception ignored) {
            // 결과 폴더 접근 실패 시 최근 목록만 비움(메인 진입은 유지)
        }
        model.addAttribute("recent", recent);
        // 홈 '분석한 기보' 카운터용 — 최근 4건과 별개로 전체 개수. 목록은 위에서 이미 한 번만 읽는다.
        model.addAttribute("totalGames", totalGames);
        return "home";
    }
}
