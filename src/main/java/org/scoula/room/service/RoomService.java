package org.scoula.room.service;

import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

public interface RoomService {
    public Room createRoom(String title, String password);
    /**
     * 1=성공, 0=방없음/꽉참, -1=비밀번호 불일치.
     * principal은 인증된 호출자의 신원(JWT subject). 성공 시 자리에 기록된다.
     */
    public int joinRoom(String roomId, Player player, String password, String principal);
    public Room getRoom(String roomId);
    public java.util.List<Room> getRoomList();
    public boolean leaveRoom(String roomId, String playerId);
}
