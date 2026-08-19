package com.example.badukanalyzer.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * 로그인한 사용자 정보를 모든 뷰 모델에 주입 — 상단 사용자 메뉴(아바타·이름·이메일)용.
 * OAuth2(구글)는 name/email/picture 속성을, 네이버는 중첩 response{name,email,profile_image}를,
 * 데모 로그인은 표시명을 사용.
 */
@ControllerAdvice
public class GlobalUserAdvice {

    @ModelAttribute
    public void addCurrentUser(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = null, email = null, picture = null;

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            Object principal = auth.getPrincipal();
            if (principal instanceof OAuth2User ou) {
                // 네이버는 사용자 정보가 response 맵 안에 중첩됨 → 있으면 그쪽을 우선 사용
                Object resp = ou.getAttributes().get("response");
                if (resp instanceof Map<?, ?> nv) {
                    name = str(nv.get("name"));
                    email = str(nv.get("email"));
                    picture = str(nv.get("profile_image"));
                } else {
                    name = attr(ou, "name");
                    email = attr(ou, "email");
                    picture = attr(ou, "picture");
                }
                if (name == null || name.isBlank()) name = email;
            } else if (principal instanceof com.example.badukanalyzer.dto.UserPrincipal up) {
                name = up.getAccount().getDisplayName();
                email = up.getAccount().getEmail();
            } else if (principal instanceof com.example.badukanalyzer.dto.UserAccount ua) {
                name = ua.getDisplayName();
                email = ua.getEmail();
            } else {
                name = auth.getName();
            }
        }
        if (name == null || name.isBlank()) name = "사용자";

        model.addAttribute("currentUserName", name);
        model.addAttribute("currentUserEmail", email);
        model.addAttribute("currentUserPicture", picture);
        model.addAttribute("currentUserInitial", name.trim().substring(0, 1).toUpperCase());
    }

    private String attr(OAuth2User u, String key) {
        return str(u.getAttributes().get(key));
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }
}
