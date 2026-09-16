package com.example.demo.auth;

public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException(long remainingSeconds) {
        super("로그인 시도가 너무 많습니다. " + remainingSeconds + "초 후 다시 시도해주세요");
    }
}
