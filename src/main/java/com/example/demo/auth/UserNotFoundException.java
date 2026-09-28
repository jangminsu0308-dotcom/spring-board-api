package com.example.demo.auth;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String username) {
        super("사용자를 찾을 수 없습니다: " + username);
    }
}
