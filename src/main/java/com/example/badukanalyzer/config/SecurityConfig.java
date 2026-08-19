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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 로그인 게이트. 모든 페이지는 인증 필요, /login·/register·정적 리소스만 공개.
 * - 구글·네이버: CLIENT_ID/SECRET 이 있으면 실제 OAuth2 로그인 활성화.
 * - 아이디/비밀번호: 회원가입(/register)한 실제 계정만 /login-local 로 검증(AuthController+UserAccountService).
 * CSRF 는 끈다 — 로컬 단일 사용자 포트폴리오이고 기존 fetch/폼 POST(/api/**, /game/analyze 등)가
 *   토큰을 보내지 않으므로. (공개 배포 시 재검토)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** remember-me 토큰 서명 키(고정) — 재시작해도 같은 키라 기존 쿠키가 계속 유효. */
    private static final String REMEMBER_KEY = "baduk-analyzer-remember-me-v1";

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${google.oauth.client-secret:}")
    private String googleClientSecret;

    @Value("${naver.oauth.client-id:}")
    private String naverClientId;

    @Value("${naver.oauth.client-secret:}")
    private String naverClientSecret;

    public boolean isGoogleEnabled() {
        return StringUtils.hasText(googleClientId) && StringUtils.hasText(googleClientSecret);
    }

    public boolean isNaverEnabled() {
        return StringUtils.hasText(naverClientId) && StringUtils.hasText(naverClientSecret);
    }

    /**
     * remember-me(로그인 유지) 토큰 서비스. 쿠키에 서명 토큰만 담는 무상태 방식(DB 불필요).
     * - 서명에 사용자 BCrypt 해시(파일 영속)+고정 키 사용 → 서버 재시작해도 쿠키로 자동 재로그인.
     * - alwaysRemember=false 가 중요: 이 서비스는 OAuth2 로그인 필터에도 공유되는데, true 면 구글/네이버
     *   로그인 성공 직후에도 쿠키를 발급하려다 OAuth principal(비번 없음, name=구글 sub)로 로컬 계정을
     *   조회 → UsernameNotFoundException → OAuth 로그인이 통째로 실패한다. 그래서 파라미터가 있을 때만
     *   발급하도록 두고, 로컬 로그인 폼에만 숨은 remember-me=true 필드를 실어 항상 유지되게 한다.
     */
    @Bean
    TokenBasedRememberMeServices rememberMeServices(UserDetailsService userDetailsService) {
        TokenBasedRememberMeServices rms = new TokenBasedRememberMeServices(
            REMEMBER_KEY, userDetailsService,
            TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256);
        rms.setMatchingAlgorithm(TokenBasedRememberMeServices.RememberMeTokenAlgorithm.SHA256);
        rms.setAlwaysRemember(false);                     // OAuth 로그인 깨짐 방지(위 설명)
        rms.setTokenValiditySeconds(60 * 60 * 24 * 14);   // 14일(일반 웹사이트 표준·스프링 기본값)
        return rms;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            TokenBasedRememberMeServices rememberMeServices) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                .requestMatchers("/login", "/login-local", "/register", "/error", "/favicon.ico").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.disable())
            .rememberMe(rm -> rm
                .rememberMeServices(rememberMeServices)
                .key(REMEMBER_KEY))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
            .csrf(csrf -> csrf.disable())
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("remember-me", "JSESSIONID")
                .permitAll());

        List<ClientRegistration> registrations = new ArrayList<>();
        if (isGoogleEnabled()) {
            registrations.add(CommonOAuth2Provider.GOOGLE
                .getBuilder("google")
                .clientId(googleClientId)
                .clientSecret(googleClientSecret)
                .build());
        }
        if (isNaverEnabled()) {
            registrations.add(naverRegistration());
        }
        if (!registrations.isEmpty()) {
            ClientRegistrationRepository repo = new InMemoryClientRegistrationRepository(registrations);
            http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .clientRegistrationRepository(repo)
                .defaultSuccessUrl("/", true));
        }

        return http.build();
    }

    /**
     * 네이버 OAuth2 클라이언트 등록. 네이버는 CommonOAuth2Provider 에 없어 수동 구성.
     * - 토큰 발급 시 client_id/secret 을 폼 파라미터로(POST) 전달.
     * - 사용자 정보 응답이 { resultcode, message, response:{id,email,name,profile_image} } 형태라
     *   식별자 속성명을 "response" 로 지정(중첩 값은 GlobalUserAdvice 에서 풀어 읽음).
     */
    private ClientRegistration naverRegistration() {
        return ClientRegistration.withRegistrationId("naver")
            .clientId(naverClientId)
            .clientSecret(naverClientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
            .tokenUri("https://nid.naver.com/oauth2.0/token")
            .userInfoUri("https://openapi.naver.com/v1/nid/me")
            .userNameAttributeName("response")
            .clientName("Naver")
            .build();
    }
}
