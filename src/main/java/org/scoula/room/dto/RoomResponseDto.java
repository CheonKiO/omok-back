package org.scoula.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        int[][] board2d = room.getBoard();
        int[] board1d = new int[15 * 15];
        for (int i = 0; i < 15; i++) {
            System.arraycopy(board2d[i], 0, board1d, i * 15, 15);
        }
        return new RoomResponseDto(
                room.getTitle(),
                room.getRoomId(),
                room.getPassword() != null && !room.getPassword().isBlank(),
                room.getPlayers(),
                room.getTurn(),
                board1d,
                room.isPlaying(),
                room.getBlackPlayer()
        );
    }
}
