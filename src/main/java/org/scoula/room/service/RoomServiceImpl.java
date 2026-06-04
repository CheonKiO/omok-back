package org.scoula.room.service;

import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;
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
    public int joinRoom(String roomId, Player player, String password) {
        Room room = rooms.get(roomId);
        if (room == null || room.getPlayers().size() == 2) return 0;

        // 비밀방 비밀번호 검증
        if (room.getPassword() != null) {
            if (password == null || !room.getPassword().equals(password)) return -1;
        }

        if (room.getPlayers().contains(player)) return 1; // 이미 참여
        room.getPlayers().add(player);
        return 1;
    }

    @Override
    public boolean leaveRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) return false;

        boolean removed = room.getPlayers().removeIf(p -> p.id().equals(playerId));
        room.setReady(0);
        if(room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
        } else {
            room.setPlaying(false); // 플레이어가 나가면 게임 중지
        }
        return removed;
    }
    @Override
    public List<Room> getRoomList() {
        return new ArrayList<>(rooms.values());
    }

    @Override
    public Room getRoom(String roomId) {
        return rooms.get(roomId);
    }


}

