-- 초기 스키마. 기존에 이미 이 테이블들을 hibernate.ddl-auto=update로 만들어둔
-- 환경(로컬/VM)에서는 spring.flyway.baseline-on-migrate=true 설정 덕분에 이 파일이
-- 실제로 실행되지는 않고 "이미 적용됨"으로 기록만 된다. 반대로 CI처럼 완전히 빈 DB에서는
-- 이 파일이 그대로 실행되어 지금 엔티티가 기대하는 스키마를 만든다.

CREATE TABLE users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(255) NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE posts (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    title      VARCHAR(200) NOT NULL,
    content    TEXT,
    user_id    BIGINT NOT NULL,
    created_at DATETIME(6),
    updated_at DATETIME(6),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comments (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    content    VARCHAR(1000) NOT NULL,
    post_id    BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    token      VARCHAR(100) NOT NULL,
    user_id    BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked    BIT(1) NOT NULL,
    created_at DATETIME(6),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
