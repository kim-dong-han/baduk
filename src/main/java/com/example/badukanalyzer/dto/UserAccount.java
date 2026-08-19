package com.example.badukanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원가입으로 만들어지는 로컬 계정. users-file(JSON)에 저장.
 * passwordHash 는 BCrypt 해시 — 평문 비밀번호는 저장하지 않는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    private String username;      // 로그인 아이디(고유)
    private String email;         // 선택
    private String displayName;   // 화면 표시 이름(비우면 username)
    private String passwordHash;  // BCrypt 해시
    private String createdAt;     // ISO-8601
}
