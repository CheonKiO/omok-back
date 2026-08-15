package org.scoula.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, Long> {

    /** 내가 흑 또는 백으로 참여한 기보를 최신순으로. */
    List<Game> findByBlackUserIdOrWhiteUserIdOrderByCreatedAtDesc(Long blackUserId, Long whiteUserId);
}
