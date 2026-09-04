package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.dto.PlayerRating;
import com.example.badukanalyzer.service.RatingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 기력 인증서(/rating)와 성장률 리그(/league) 화면. 요청 매핑만 하고 집계는 RatingService.
 */
@Controller
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /** 기획 A — 한 대국자의 누적 기력 인증서. player 가 없으면 내 기보에 가장 많이 나온 사람. */
    @GetMapping("/rating")
    public String rating(@RequestParam(required = false) String player, Model model) {
        List<PlayerRating> all = ratingService.getAllRatings();
        PlayerRating subject = ratingService.findByName(all, player);
        if (subject == null) subject = ratingService.getDefaultSubject(all);

        model.addAttribute("subject", subject);
        model.addAttribute("players", all);
        // 구간 3개를 리스트로 넘긴다 — 템플릿에서 인라인 리스트 리터럴을 쓰지 않기 위해서.
        model.addAttribute("phases", subject == null ? List.of()
                : java.util.Arrays.asList(subject.getOpening(), subject.getMiddle(), subject.getEndgame()));
        return "rating";
    }

    /** 기획 B — 개선폭 순위. 절대 기력 순위도 같이 넘겨 두 축을 비교할 수 있게 한다. */
    @GetMapping("/league")
    public String league(Model model) {
        List<PlayerRating> all = ratingService.getAllRatings();
        List<PlayerRating> growth = ratingService.getGrowthLeague();

        List<PlayerRating> strength = new java.util.ArrayList<>(all);
        strength.sort(java.util.Comparator.comparingDouble(PlayerRating::getAvgScoreLoss));

        model.addAttribute("growthLeague", growth);
        model.addAttribute("strengthLeague", strength);
        model.addAttribute("totalPlayers", all.size());
        return "league";
    }
}
