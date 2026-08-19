package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.UserAccount;
import com.example.badukanalyzer.dto.UserPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * remember-me 쿠키 재인증 시 아이디로 계정을 다시 로드 — 서버 재시작 후에도 자동 로그인 유지의 핵심.
 * 로컬 회원(UserAccountService) 기준. (OAuth 로그인은 별도 경로라 무관)
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserAccountService userAccounts;

    public AppUserDetailsService(UserAccountService userAccounts) {
        this.userAccounts = userAccounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount acc = userAccounts.find(username);
        if (acc == null) throw new UsernameNotFoundException("계정 없음: " + username);
        return new UserPrincipal(acc);
    }
}
