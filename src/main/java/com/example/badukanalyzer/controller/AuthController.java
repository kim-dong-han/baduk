package com.example.badukanalyzer.controller;

import com.example.badukanalyzer.config.SecurityConfig;
import com.example.badukanalyzer.dto.UserAccount;
import com.example.badukanalyzer.dto.UserPrincipal;
import com.example.badukanalyzer.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class AuthController {

    private final SecurityConfig securityConfig;
    private final UserAccountService userAccounts;
    private final TokenBasedRememberMeServices rememberMeServices;

    // 소셜 로그인(구글·네이버) 버튼 노출 스위치. 도메인 확정 전까지 false(아이디 로그인만 노출).
    // 나중에 고정 도메인+리디렉션 등록 끝나면 application.yaml 에서 true 로 바꾸고 재시작.
    @Value("${app.social-login-enabled:false}")
    private boolean socialLoginEnabled;

    public AuthController(SecurityConfig securityConfig, UserAccountService userAccounts,
                          TokenBasedRememberMeServices rememberMeServices) {
        this.securityConfig = securityConfig;
        this.userAccounts = userAccounts;
        this.rememberMeServices = rememberMeServices;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("googleEnabled", socialLoginEnabled && securityConfig.isGoogleEnabled());
        model.addAttribute("naverEnabled", socialLoginEnabled && securityConfig.isNaverEnabled());
        return "login";
    }

    /** 아이디/비밀번호 로그인 — 회원가입한 실제 계정만 통과. */
    @PostMapping("/login-local")
    public String loginLocal(@RequestParam String username,
                             @RequestParam String password,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        UserAccount acc = userAccounts.authenticate(username, password);
        if (acc == null) {
            return "redirect:/login?error";
        }
        // principal=UserPrincipal(UserDetails) — 세션 로그인과 remember-me 재인증의 주체 타입을 일치시킴.
        var principal = new UserPrincipal(acc);
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        // 로그인 유지 쿠키(14일) 발급 — 서버 재시작·브라우저 종료 후에도 자동 로그인.
        rememberMeServices.loginSuccess(request, response, auth);
        return "redirect:/";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    /** 회원가입 처리 → 성공 시 로그인 화면으로, 실패 시 입력값 유지한 채 오류 표시. */
    @PostMapping("/register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           @RequestParam(required = false) String passwordConfirm,
                           @RequestParam(required = false) String email,
                           @RequestParam(required = false) String displayName,
                           Model model) {
        if (passwordConfirm != null && !password.equals(passwordConfirm)) {
            return registerError(model, "비밀번호가 서로 일치하지 않습니다.", username, email, displayName);
        }
        try {
            userAccounts.register(username, password, email, displayName);
        } catch (IllegalArgumentException e) {
            return registerError(model, e.getMessage(), username, email, displayName);
        }
        return "redirect:/login?registered";
    }

    private String registerError(Model model, String msg, String username, String email, String displayName) {
        model.addAttribute("error", msg);
        model.addAttribute("username", username);
        model.addAttribute("email", email);
        model.addAttribute("displayName", displayName);
        return "register";
    }
}
