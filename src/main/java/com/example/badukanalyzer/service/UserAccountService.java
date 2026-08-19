package com.example.badukanalyzer.service;

import com.example.badukanalyzer.dto.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 로컬 회원 계정 저장소(파일 기반). users-file(JSON)에 계정 목록을 두고,
 * 회원가입/로그인 검증을 담당. 비밀번호는 BCrypt 해시로만 보관.
 * DB가 없는 포트폴리오 앱이라 인메모리 맵 + JSON 파일 영속으로 단순 구현.
 */
@Service
public class UserAccountService {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, UserAccount> accounts = new LinkedHashMap<>(); // key=username(소문자)

    @Value("${app.users-file:C:/KataGo/users.json}")
    private String usersFile;

    @PostConstruct
    public synchronized void load() {
        File f = new File(usersFile);
        if (!f.exists()) return;
        try {
            List<UserAccount> list = mapper.readValue(f, new TypeReference<List<UserAccount>>() {});
            for (UserAccount a : list) accounts.put(key(a.getUsername()), a);
        } catch (IOException e) {
            // 파일 손상 시 빈 상태로 시작(로그인만 막힘, 앱 기동은 유지)
            System.err.println("users-file 로드 실패: " + e.getMessage());
        }
    }

    private synchronized void save() {
        try {
            File f = new File(usersFile);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            mapper.writeValue(f, new ArrayList<>(accounts.values()));
        } catch (IOException e) {
            throw new RuntimeException("회원 정보 저장 실패: " + e.getMessage(), e);
        }
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public synchronized boolean exists(String username) {
        return accounts.containsKey(key(username));
    }

    /** 회원가입. 실패 시 사용자용 메시지를 담은 IllegalArgumentException. */
    public synchronized UserAccount register(String username, String password,
                                             String email, String displayName) {
        String u = username == null ? "" : username.trim();
        if (u.length() < 3) throw new IllegalArgumentException("아이디는 3자 이상이어야 합니다.");
        if (!u.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("아이디는 영문·숫자·_.- 만 사용할 수 있습니다.");
        if (password == null || password.length() < 4) throw new IllegalArgumentException("비밀번호는 4자 이상이어야 합니다.");
        if (exists(u)) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");

        String dn = (displayName == null || displayName.isBlank()) ? u : displayName.trim();
        UserAccount acc = UserAccount.builder()
            .username(u)
            .email(email == null || email.isBlank() ? null : email.trim())
            .displayName(dn)
            .passwordHash(encoder.encode(password))
            .createdAt(LocalDateTime.now().toString())
            .build();
        accounts.put(key(u), acc);
        save();
        return acc;
    }

    /** 아이디로 계정 조회(없으면 null). remember-me 재인증·UserDetailsService 용. */
    public synchronized UserAccount find(String username) {
        return accounts.get(key(username));
    }

    /** 아이디/비밀번호 검증. 성공 시 계정, 실패 시 null. */
    public synchronized UserAccount authenticate(String username, String password) {
        UserAccount acc = accounts.get(key(username));
        if (acc == null || password == null) return null;
        return encoder.matches(password, acc.getPasswordHash()) ? acc : null;
    }
}
