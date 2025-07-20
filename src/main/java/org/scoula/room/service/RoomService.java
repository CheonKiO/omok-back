package org.scoula.room.service;

import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;

public interface RoomService {
    public Room createRoom(String title);
    public boolean joinRoom(String roomId, Player player);
    public Room getRoom(String roomId);
    public java.util.List<Room> getRoomList();
    public boolean leaveRoom(String roomId, String playerId);
}
