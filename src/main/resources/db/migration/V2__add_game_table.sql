-- 종료된 대국 기보 저장 테이블. 게임 종료 순간 원샷 insert.
-- black/white_user_id는 회원이면 users.id, 게스트면 NULL. moves는 index CSV.

CREATE TABLE game (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    black_user_id BIGINT       NULL,
    white_user_id BIGINT       NULL,
    black_name    VARCHAR(50)  NOT NULL,
    white_name    VARCHAR(50)  NOT NULL,
    winner        VARCHAR(10)  NOT NULL,
    end_reason    VARCHAR(20)  NOT NULL,
    moves         TEXT         NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_game_black_user (black_user_id),
    INDEX idx_game_white_user (white_user_id)
) ENGINE=InnoDB;
