package org.scoula.room.dto;

import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Room;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 종료된 방을 조회하면 이전 대국판이 아니라 빈 판이 내려온다(D2).
 * 새 입장자가 남의 지난 대국을 보는 문제를 막는다.
 */
class RoomResponseDtoTest {

    private Room roomWithStones(boolean playing) {
        int[][] board = new int[15][15];
        board[7][7] = 1;
        board[7][8] = 2;
        Room room = Room.builder()
                .roomId("r1")
                .title("방")
                .players(new ArrayList<>())
                .board(board)
                .turn(3)
                .isPlaying(playing)
                .build();
        return room;
    }

    @Test
    void 진행중인_방은_보드를_그대로_내려준다() {
        RoomResponseDto dto = RoomResponseDto.from(roomWithStones(true));

        assertEquals(1, dto.board()[7 * 15 + 7]);
        assertEquals(2, dto.board()[7 * 15 + 8]);
        assertEquals(3, dto.turn());
    }

    @Test
    void 종료된_방은_빈_보드와_턴1을_내려준다() {
        RoomResponseDto dto = RoomResponseDto.from(roomWithStones(false));

        assertEquals(225, dto.board().length);
        for (int v : dto.board()) {
            assertEquals(0, v);
        }
        assertEquals(1, dto.turn());
    }
}
