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
import static org.mockito.ArgumentMatchers.any;
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
        room.bindMember(BLACK, "p-black", "흑돌");
        room.bindMember(WHITE, "p-white", "백돌");
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
}
