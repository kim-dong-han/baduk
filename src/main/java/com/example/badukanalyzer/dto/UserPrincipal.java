package com.example.badukanalyzer.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 인증 주체(로그인 세션·remember-me 재인증에서 principal 로 사용).
 * 순수 DTO 인 {@link UserAccount} 를 감싸 UserDetails 계약만 제공 — users.json 저장 포맷은 건드리지 않는다.
 * getPassword()=BCrypt 해시(파일에 영속) → remember-me 토큰 서명이 재시작 후에도 유효.
 */
public class UserPrincipal implements UserDetails {

    private final UserAccount account;

    public UserPrincipal(UserAccount account) {
        this.account = account;
    }

    public UserAccount getAccount() {
        return account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getUsername();
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
