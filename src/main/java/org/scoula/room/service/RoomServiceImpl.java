package org.scoula.room.service;

import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class RoomServiceImpl implements RoomService {

    private final Map<String, Room> rooms;

    public RoomServiceImpl() {
        rooms = new ConcurrentHashMap<>();
    }

    @Override
    public Room createRoom(String title, String password) {
        int SIZE = 15;
        int[][] board = new int[SIZE][SIZE];
        Room room = Room.builder()
                .title(title)
                .roomId(UUID.randomUUID().toString())
                .password(password != null && !password.isBlank() ? password : null)
                .players(new CopyOnWriteArrayList<>())
                .turn(1)
                .board(board)
                .isPlaying(false)
                .build();
        rooms.put(room.getRoomId(), room);
        return room;
    }

    @Override
    public int joinRoom(String roomId, Player player, String password, String principal) {
        Room room = rooms.get(roomId);
        if (room == null) return 0;

        // 게임 뮤테이터(RoomSocketService)와 동일한 room 모니터로 직렬화한다.
        // 락이 없으면 자리 수 검사(check-then-act)가 원자적이지 않아 동시 입장이
        // 2자리 캡을 넘기거나(3명 소프트락), 게임 진행 중 상태 변경과 경합한다.
        synchronized (room) {
            if (room.getPlayers().size() == 2) return 0;

            // 비밀방 비밀번호 검증
            if (room.getPassword() != null) {
                if (password == null || !room.getPassword().equals(password)) return -1;
            }

            // 표시용 player.id 중복 방지: 이미 다른 principal이 소유한 id면 거부(자리 오염 차단).
            String owner = room.principalOf(player.id());
            if (owner != null && !owner.equals(principal)) return 0;

            if (room.getPlayers().contains(player)) {
                room.bindMember(principal, player.id()); // 재입장: principal 재확인
                return 1; // 이미 참여
            }
            room.getPlayers().add(player);
            // 인증 principal을 자리에 기록. payload player.id/name은 표시용일 뿐.
            room.bindMember(principal, player.id());
            return 1;
        }
    }

    @Override
    public boolean leaveRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) return false;

        // joinRoom·게임 뮤테이터와 동일 모니터. isPlaying/players/자리 변경을 락 밖에서
        // 하면 processMove가 락 안에서 읽는 상태에 happens-before가 성립하지 않는다.
        // expireGrace가 room 락을 쥔 채 호출해도 재진입 락이라 안전하다.
        synchronized (room) {
            String principal = room.principalOf(playerId);
            boolean removed = room.getPlayers().removeIf(p -> p.id().equals(playerId));
            room.setReady(0);
            if (room.getPlayers().isEmpty()) {
                rooms.remove(roomId);
            } else {
                room.setPlaying(false); // 플레이어가 나가면 게임 중지
                if (removed) {
                    // 유령 멤버 방지: 자리를 안 지우면 bindMember의 2자리 캡이
                    // 새 입장자를 영구 거부하는 소프트락이 생긴다.
                    room.unbindMember(principal);
                }
            }
            return removed;
        }
    }
    @Override
    public List<Room> getRoomList() {
        return new ArrayList<>(rooms.values());
    }

    @Override
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }

    @Override
    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }


}

