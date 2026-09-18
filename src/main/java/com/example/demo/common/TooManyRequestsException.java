package com.example.demo.common;

public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(long remainingSeconds) {
        super("너무 많은 요청을 보냈습니다. " + remainingSeconds + "초 후 다시 시도해주세요");
    }
}
