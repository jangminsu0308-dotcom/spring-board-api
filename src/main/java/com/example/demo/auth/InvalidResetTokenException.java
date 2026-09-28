package com.example.demo.auth;

public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("재설정 토큰이 유효하지 않거나 만료되었습니다");
    }
}
