package org.scoula.game;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.domain.Room;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

/**
 * 게임 종료 훅 — 종료된 대국을 기보로 원샷 저장한다.
 * 4개 종료 경로(승리/기권/시간초과/끊김몰수)가 모두 이 지점을 통과한다.
 * (향후 ELO 레이팅 갱신·통계도 이 훅에 얹는다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameArchiveService {

    private final GameRepository gameRepository;

    /**
     * 종료된 대국을 기보로 저장한다. 회원이 한 명이라도 있을 때만 저장한다
     * (게스트끼리는 미저장). 서버 중단으로 이 메서드에 도달하지 못하면 자연히 미저장.
     */
    // 기보 저장 실패가 게임 종료 흐름(GAME_END broadcast)을 깨지 않도록 예외를 삼킨다(best-effort).
    @Transactional
    public void archive(Room room, WinnerColor winner, EndReason reason) {
        try {
            String blackPrincipal = room.blackPrincipal();
            String whitePrincipal = room.whitePrincipal();
            if (blackPrincipal == null || whitePrincipal == null) {
                log.warn("[KIFU_SKIP] 자리 principal 누락 roomId={} black={} white={}",
                        room.getRoomId(), blackPrincipal, whitePrincipal);
                return;
            }

            Long blackUserId = memberUserId(blackPrincipal);
            Long whiteUserId = memberUserId(whitePrincipal);
            if (blackUserId == null && whiteUserId == null) {
                log.info("[KIFU_SKIP] 게스트 대국 미저장 roomId={}", room.getRoomId());
                return;
            }

            String moves = room.getMoveHistory().stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            Game game = Game.builder()
                    .blackUserId(blackUserId)
                    .whiteUserId(whiteUserId)
                    .blackName(nameOr(room, blackPrincipal))
                    .whiteName(nameOr(room, whitePrincipal))
                    .winner(winner)
                    .endReason(reason)
                    .moves(moves)
                    .build();
            gameRepository.save(game);
            log.info("[KIFU_SAVED] roomId={} winner={} reason={} moves={}",
                    room.getRoomId(), winner, reason, room.getMoveHistory().size());
        } catch (Exception e) {
            log.error("[KIFU_SAVE_FAIL] roomId={} : {}", room.getRoomId(), e.getMessage(), e);
        }
    }

    /** 회원 principal(=user id 숫자)이면 Long, 게스트("guest-*")면 null. */
    private Long memberUserId(String principal) {
        if (principal != null && principal.matches("\\d+")) {
            return Long.parseLong(principal);
        }
        return null;
    }

    private String nameOr(Room room, String principal) {
        String name = room.playerNameOf(principal);
        if (name == null || name.isBlank()) return "알수없음";
        // black_name/white_name은 VARCHAR(50). 클라 제공 이름이라 길이 무검증 → 방어적 truncate.
        return name.length() > 50 ? name.substring(0, 50) : name;
    }
}
