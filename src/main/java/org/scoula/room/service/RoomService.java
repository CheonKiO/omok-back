package org.scoula.room.service;

import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

public interface RoomService {
    public Room createRoom(String title, String password);
    /** 전체 방 수가 상한에 도달했는지(방 생성 DoS로 인한 OOM 방어). */
    public boolean isAtCapacity();
    /**
     * 1=성공, 0=방없음/꽉참, -1=비밀번호 불일치.
     * principal은 인증된 호출자의 신원(JWT subject). 성공 시 자리에 기록된다.
     */
    public int joinRoom(String roomId, Player player, String password, String principal);
    public Room getRoom(String roomId);
    public java.util.List<Room> getRoomList();
    public boolean leaveRoom(String roomId, String playerId);
    /** 빈 방 GC(EmptyRoomCleaner)가 TTL 경과 빈 방을 rooms 맵에서 제거할 때 사용. */
    public void removeRoom(String roomId);
}
