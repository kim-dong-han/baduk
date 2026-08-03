package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.config.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class AuthController {

    private final SecurityConfig securityConfig;

    public AuthController(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleEnabled", securityConfig.isGoogleEnabled());
        return "login";
    }

    /** 아이디 입력·소셜(구글 제외) 데모 로그인 — 세션에 데모 사용자 인증을 심는다. */
    @PostMapping("/demo-login")
    public String demoLogin(@RequestParam(required = false) String username,
                            @RequestParam(required = false) String provider,
                            HttpServletRequest request) {
        String name;
        if (StringUtils.hasText(username)) {
            name = username;
        } else if (StringUtils.hasText(provider)) {
            name = provider + " 데모";
        } else {
            name = "데모 사용자";
        }

        var auth = new UsernamePasswordAuthenticationToken(
            name, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return "redirect:/";
    }
}
