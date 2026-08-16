package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.RoomResponseMessage;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GAME_END 메시지는 승자를 winner 필드로 명시한다(D3).
 * 프론트가 마지막 돌 색으로 승자를 추론하면 off-turn 기권/타임아웃에서 반대로 표시된다.
 */
class GameEndWinnerTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private RoomSocketService service;

    private static final String ROOM_ID = "room-1";
    private static final String BLACK = "user:2";
    private static final String WHITE = "user:3";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        service = new RoomSocketService(
                roomBroadcaster,
                roomService,
                new GameService(new RenjuRuleEngine()),
                mock(org.scoula.game.GameArchiveService.class),
                mock(org.springframework.scheduling.TaskScheduler.class));
    }

    private Room startedRoom() {
        Player pb = new Player("p-black", "흑돌");
        Player pw = new Player("p-white", "백돌");
        Room room = Room.builder()
                .roomId(ROOM_ID)
                .players(new ArrayList<>(List.of(pb, pw)))
                .board(new int[15][15])
                .turn(1)
                .isPlaying(true)
                .ready(0)
                .build();
        room.bindMember(BLACK, "p-black");
        room.bindMember(WHITE, "p-white");
        room.setBlackPlayer("p-black");
        room.setBlackPrincipal(BLACK);
        room.setWhitePrincipal(WHITE);
        return room;
    }

    private RoomResponseMessage captureBroadcast() {
        ArgumentCaptor<RoomResponseMessage> captor = ArgumentCaptor.forClass(RoomResponseMessage.class);
        verify(roomBroadcaster).broadcast(eq(ROOM_ID), captor.capture());
        return captor.getValue();
    }

    @Test
    void 흑이_기권하면_백이_승자로_실린다() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        service.processSurrender(ROOM_ID, BLACK);

        RoomResponseMessage msg = captureBroadcast();
        assertEquals(MessageType.GAME_END, msg.getType());
        assertEquals("WHITE", msg.getWinner());
    }

    @Test
    void 백이_시간초과하면_흑이_승자로_실린다() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        service.processTimeout(ROOM_ID, WHITE);

        RoomResponseMessage msg = captureBroadcast();
        assertEquals(MessageType.GAME_END, msg.getType());
        assertEquals("BLACK", msg.getWinner());
    }

    /**
     * 5목 승리 경로의 승자 극성. 기권/시간초과는 "행위자가 패"지만 착수는 "착수자가 승"이라
     * 삼항의 방향이 반대다. 붙어 있는 비슷한 코드라 뒤집혀도 눈에 띄지 않으므로 고정한다.
     */
    @Test
    void 흑이_오목을_완성하면_흑이_승자로_실린다() {
        Room room = startedRoom();
        // 돌은 색 상수가 아니라 착수 순번으로 저장된다(홀수=흑, 짝수=백).
        // 흑 4수: (7,0)(7,1)(7,2)(7,3) / 백 4수는 승부와 무관한 먼 자리.
        int[][] board = room.getBoard();
        board[7][0] = 1; board[7][1] = 3; board[7][2] = 5; board[7][3] = 7;
        board[12][0] = 2; board[12][1] = 4; board[12][2] = 6; board[12][3] = 8;
        room.setTurn(9); // 홀수 = 흑 차례
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        // (7,4)에 다섯째 돌 → 장목/사사 아닌 정확한 5목이라 금수가 아니다.
        service.processMove(ROOM_ID, BLACK, 7 * 15 + 4);

        RoomResponseMessage msg = captureBroadcast();
        assertEquals(MessageType.GAME_END, msg.getType());
        assertEquals("BLACK", msg.getWinner());
    }
}
