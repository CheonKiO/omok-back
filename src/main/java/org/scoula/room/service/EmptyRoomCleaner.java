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

    private final RoomService roomService;

    public EmptyRoomCleaner(RoomService roomService) {
        this.roomService = roomService;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        for (Room room : roomService.getRoomList()) {
            if (room.getPlayers().isEmpty() && now - room.getCreatedAt() >= TTL_MILLIS) {
                roomService.removeRoom(room.getRoomId());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[ROOM_GC] removed={} empty rooms past TTL", removed);
        }
    }
}
