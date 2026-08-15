-- Baseline: 현재 스키마(users, refresh_tokens). ddl-auto=update로 만들어진 상태를 명시화.
-- prod(HeatWave)에는 baseline-on-migrate로 채택되어 이 파일은 실행되지 않음(기존 테이블 인정).
-- 신규/빈 DB에서만 실제 실행됨. 엔티티(User, RefreshToken)와 일치.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(50)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token)
) ENGINE=InnoDB;
