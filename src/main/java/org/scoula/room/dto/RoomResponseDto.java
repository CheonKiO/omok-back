package org.scoula.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

import java.util.List;

public record RoomResponseDto(
        String title,
        String roomId,
        boolean hasPassword,
        List<Player> players,
        int turn,
        int[] board,
        @JsonProperty("isPlaying") boolean isPlaying,
        String blackPlayer
) {
    public static RoomResponseDto from(Room room) {
        int[] board1d = new int[15 * 15];
        int turn = 1;
        // 진행 중일 때만 실제 판을 노출한다. 종료된 방은 빈 판으로 내려
        // 새 입장자가 이전 대국의 잔존 화면을 보지 않게 한다(기보는 이미 DB에 저장됨).
        if (room.isPlaying()) {
            int[][] board2d = room.getBoard();
            for (int i = 0; i < 15; i++) {
                System.arraycopy(board2d[i], 0, board1d, i * 15, 15);
            }
            turn = room.getTurn();
        }
        return new RoomResponseDto(
                room.getTitle(),
                room.getRoomId(),
                room.getPassword() != null && !room.getPassword().isBlank(),
                room.getPlayers(),
                turn,
                board1d,
                room.isPlaying(),
                room.getBlackPlayer()
        );
    }
}
