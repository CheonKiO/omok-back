package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.scoula.game.EndReason;
import org.scoula.game.GameArchiveService;
import org.scoula.game.WinnerColor;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.RoomResponseMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 30초 유예 만료 몰수 경로(WebSocketEventListener#expireGrace) 검증.
 * 이 경로는 스케줄러 람다 안에 있어 그동안 어떤 테스트도 실행하지 못했다.
 * 승자 극성(끊긴 쪽이 패), 이미 끝난 게임의 이중 GAME_END 차단,
 * 그리고 archive가 leaveRoom(자리 principal unbind)보다 먼저라는 순서를 고정한다.
 */
class GraceExpiryTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private GameArchiveService gameArchiveService;
    private WebSocketEventListener listener;

    private static final String ROOM_ID = "room-1";
    private static final String BLACK = "user:2";
    private static final String WHITE = "user:3";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        gameArchiveService = mock(GameArchiveService.class);
        listener = new WebSocketEventListener(roomBroadcaster, roomService, gameArchiveService);
    }

    private Room playingRoom() {
        Room room = Room.builder()
                .roomId(ROOM_ID)
                .players(new ArrayList<>(List.of(new Player("p-black", "흑돌"), new Player("p-white", "백돌"))))
                .board(new int[15][15])
                .turn(1)
                .isPlaying(true)
                .build();
        room.bindMember(BLACK, "p-black");
        room.bindMember(WHITE, "p-white");
        room.setBlackPrincipal(BLACK);
        room.setWhitePrincipal(WHITE);
        return room;
    }

    /** 이 방으로 나간 브로드캐스트 중 GAME_END 메시지를 꺼낸다. */
    private RoomResponseMessage captureGameEnd() {
        ArgumentCaptor<RoomResponseMessage> captor = ArgumentCaptor.forClass(RoomResponseMessage.class);
        verify(roomBroadcaster, atLeastOnce()).broadcast(eq(ROOM_ID), captor.capture());
        return captor.getAllValues().stream()
                .filter(m -> m.getType() == MessageType.GAME_END)
                .findFirst().orElseThrow();
    }

    @Test
    void 흑이_유예를_넘기면_백이_승자로_실린다() {
        Room room = playingRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        listener.expireGrace(ROOM_ID, "p-black", BLACK);

        RoomResponseMessage end = captureGameEnd();
        assertEquals("WHITE", end.getWinner());
        assertEquals(ROOM_ID, end.getRoomId()); // 다른 세 GAME_END 경로와 동일한 계약
        verify(gameArchiveService).archive(eq(room), eq(WinnerColor.WHITE), eq(EndReason.DISCONNECT));
    }

    @Test
    void 백이_유예를_넘기면_흑이_승자로_실린다() {
        Room room = playingRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        listener.expireGrace(ROOM_ID, "p-white", WHITE);

        RoomResponseMessage end = captureGameEnd();
        assertEquals("BLACK", end.getWinner());
        verify(gameArchiveService).archive(eq(room), eq(WinnerColor.BLACK), eq(EndReason.DISCONNECT));
    }

    /** 유예 창 안에서 상대가 기권해 게임이 이미 끝났다면 GAME_END를 두 번 보내지 않는다. */
    @Test
    void 이미_종료된_게임은_LEAVE만_보내고_기보를_다시_저장하지_않는다() {
        Room room = playingRoom();
        room.setPlaying(false);
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        listener.expireGrace(ROOM_ID, "p-black", BLACK);

        verify(roomBroadcaster, never()).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.GAME_END));
        verify(roomBroadcaster).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.LEAVE));
        verify(gameArchiveService, never()).archive(any(), any(), any());
        verify(roomService).leaveRoom(ROOM_ID, "p-black");
    }

    /** leaveRoom이 자리 principal을 unbind하므로 archive가 반드시 먼저여야 한다(순서가 곧 기보 내용). */
    @Test
    void 기보_저장이_퇴장_처리보다_먼저다() {
        Room room = playingRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        listener.expireGrace(ROOM_ID, "p-black", BLACK);

        InOrder order = inOrder(gameArchiveService, roomService);
        order.verify(gameArchiveService).archive(eq(room), eq(WinnerColor.WHITE), eq(EndReason.DISCONNECT));
        order.verify(roomService).leaveRoom(ROOM_ID, "p-black");
    }
}
