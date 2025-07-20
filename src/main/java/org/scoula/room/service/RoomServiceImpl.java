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
    public Room createRoom(String title) {
        int SIZE = 15;
        int[][] board = new int[SIZE][SIZE]; // 자동 0 초기화
        Room room = Room.builder()
                .title(title)
                .roomId(UUID.randomUUID().toString())
                .players(new CopyOnWriteArrayList<>())
                .turn(1)
                .board(board)
                .isPlaying(false)
                .build();
        rooms.put(room.getRoomId(), room);

        return room;
    }
    @Override
    public boolean joinRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        // 방이 없거나, 꽉찼다면 false
        if (room == null || room.getPlayers().size() == 2) {
            return false;
        }

        if (room.getPlayers().contains(player)) {
            return true; // 이미 참여한 플레이어
        }
        room.getPlayers().add(player);
        if(room.getPlayers().size() == 2) {
            room.setPlaying(true);
        }
        return true;
    }

    @Override
    public boolean leaveRoom(String roomId, String playerId) {
        Room room = rooms.get(roomId);
        if (room == null) return false;

        boolean removed = room.getPlayers().removeIf(p -> p.getId().equals(playerId));
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

