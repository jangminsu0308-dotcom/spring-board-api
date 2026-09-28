-- 비밀번호 찾기(재설정)를 위한 보안 질문/답변, 그리고 재설정용 임시 토큰 테이블.
-- 이메일 발송 서버가 없는 환경이라, 이메일 링크 대신 "가입 시 등록한 보안 질문에
-- 맞게 답하면 짧게 유효한 재설정 토큰을 내주는" 방식을 쓴다.

ALTER TABLE users
    ADD COLUMN security_question VARCHAR(200),
    ADD COLUMN security_answer_hash VARCHAR(255);

CREATE TABLE password_reset_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    token      VARCHAR(100) NOT NULL,
    user_id    BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used       BIT(1) NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uk_password_reset_tokens_token UNIQUE (token),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
