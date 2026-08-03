package com.example.badukanalyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.util.StringUtils;

/**
 * 로그인 게이트. 모든 페이지는 인증 필요, /login·정적 리소스만 공개.
 * - 구글: GOOGLE_CLIENT_ID/SECRET 이 있으면 실제 OAuth2 로그인 활성화.
 * - 나머지 소셜/아이디 입력: /demo-login 으로 데모 사용자 세션 발급(AuthController).
 * CSRF 는 끈다 — 로컬 단일 사용자 포트폴리오이고 기존 fetch/폼 POST(/api/**, /game/analyze 등)가
 *   토큰을 보내지 않으므로. (공개 배포 시 재검토)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${google.oauth.client-secret:}")
    private String googleClientSecret;

    public boolean isGoogleEnabled() {
        return StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers("/login", "/demo-login", "/error", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.disable())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
            .csrf(csrf -> csrf.disable())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .permitAll());

        if (isGoogleEnabled()) {
            ClientRegistration google = CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .build();
            ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(google);
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .clientRegistrationRepository(repo)
                .defaultSuccessUrl("/", true));
        }

        return http.build();
    }
}
