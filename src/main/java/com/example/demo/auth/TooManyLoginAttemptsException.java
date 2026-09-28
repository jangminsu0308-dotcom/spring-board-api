package com.example.demo.auth;

/** 로그인 실패 제한뿐 아니라, 같은 방식(아이디별 실패 횟수 제한)을 재사용하는
 *  비밀번호 찾기의 보안 질문 답변 확인(AuthService.verifySecurityAnswer)에도 쓰인다 —
 *  그래서 메시지에 "로그인"을 못 박지 않는다. */
public class TooManyLoginAttemptsException extends RuntimeException {
    public TooManyLoginAttemptsException(long remainingSeconds) {
        super("시도가 너무 많습니다. " + remainingSeconds + "초 후 다시 시도해주세요");
    }
}
