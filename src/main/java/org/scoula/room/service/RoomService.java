package org.scoula.room.service;

import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;

public interface RoomService {
    public Room createRoom(String title, String password);
    /** 1=성공, 0=방없음/꽉참, -1=비밀번호 불일치 */
    public int joinRoom(String roomId, Player player, String password);
    public Room getRoom(String roomId);
    public java.util.List<Room> getRoomList();
    public boolean leaveRoom(String roomId, String playerId);
}
