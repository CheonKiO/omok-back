package org.scoula.room.service;

import lombok.extern.slf4j.Slf4j;
import org.scoula.room.domain.Room;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 빈 방 누수 정리 (#6): 아무도 join 하지 않아 players가 빈 채로 남은 방을 주기적으로 제거한다.
 * createRoom은 players가 빈 상태로 방을 만들므로, join 없이 방치되면 rooms 맵에 영구 잔존한다.
 * 플레이어가 있는 방(활성)과 생성 후 TTL이 지나지 않은 방은 보존한다.
 */
@Slf4j
@Component
public class EmptyRoomCleaner {

    /** 생성 후 이 시간(ms)이 지난 빈 방을 GC 대상으로 본다. */
    static final long TTL_MILLIS = 60_000;
    /**
     * 진행 중이 아닌 방(2명이 안 찼거나 대국이 안 도는 방)이 이만큼 활동이 없으면 GC 대상.
     * 게스트 무한 발급으로 만든 1인 방이 rooms 맵에 영구 잔존(OOM)하는 것을 막는다.
     * '상대 기다리는 방'을 성급히 지우지 않도록 넉넉히 잡는다(입장·착수 시 lastActiveAt 갱신).
     */
    static final long IDLE_TTL_MILLIS = 30 * 60_000L;

    private final RoomService roomService;

    public EmptyRoomCleaner(RoomService roomService) {
        this.roomService = roomService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Room room : roomService.getRoomList()) {
            if (isEvictable(room, now)) {
                roomService.removeRoom(room.getRoomId());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[ROOM_GC] removed={} stale rooms", removed);
        }
    }

    private boolean isEvictable(Room room, long now) {
        // 생성 후 방치된 빈 방(아무도 입장 안 함).
        if (room.getPlayers().isEmpty()) {
            return now - room.getCreatedAt() >= TTL_MILLIS;
        }
        // 진행 중 대국은 절대 건드리지 않는다.
        if (room.isPlaying()) return false;
        // 2명 미만이거나 대국이 끝난 방이 오래 유휴 → 회수.
        return now - room.getLastActiveAt() >= IDLE_TTL_MILLIS;
    }
}
