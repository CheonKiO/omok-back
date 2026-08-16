package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.MessageType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS 게임 액션 인가 검증 (#1): 착수/기권은 payload sender가 아니라 세션 principal의
 * 멤버십·자리(턴 소유)로만 허용된다. 비멤버·틀린 자리는 상태 무변경으로 거부된다.
 * RoomBroadcaster는 mock, GameService는 실제 렌주 엔진으로 실동작 검증.
 */
class RoomAuthorizationTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private org.scoula.game.GameArchiveService gameArchiveService;
    private RoomSocketService service;

    private static final String ROOM_ID = "room-1";
    private static final String BLACK = "user:2";   // 흑 자리 principal
    private static final String WHITE = "user:3";   // 백 자리 principal
    private static final String INTRUDER = "intruder";

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        GameService gameService = new GameService(new RenjuRuleEngine());
        gameArchiveService = org.mockito.Mockito.mock(org.scoula.game.GameArchiveService.class);
        service = new RoomSocketService(roomBroadcaster, roomService, gameService,
                gameArchiveService,
                org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class));
    }

    /** 흑=user:2, 백=user:3 배정된 진행중(흑 차례) 방. */
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

    private int filledCells(Room room) {
        int count = 0;
        for (int[] row : room.getBoard()) {
            for (int cell : row) {
                if (cell != 0) count++;
            }
        }
        return count;
    }

    // ── 정상 기권 → 기보 저장 1회 ──
    @Test
    void surrenderWhilePlayingArchivesOnce() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);
        service.processSurrender(ROOM_ID, BLACK);
        verify(gameArchiveService, times(1)).archive(eq(room), any(), any());
    }

    // ── 이미 끝난 게임에 기권 → 중복 저장/처리 없음 (isPlaying 가드) ──
    @Test
    void surrenderAfterGameEndDoesNotArchive() {
        Room room = startedRoom();
        room.setPlaying(false); // 이미 종료
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);
        service.processSurrender(ROOM_ID, BLACK);
        verify(gameArchiveService, never()).archive(any(), any(), any());
        verify(roomBroadcaster, never()).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.GAME_END));
    }

    @Test
    void nonMemberMoveRejected() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        service.processMove(ROOM_ID, INTRUDER, 112);

        assertEquals(1, room.getTurn(), "비멤버 착수는 turn을 바꾸지 않아야 한다");
        assertEquals(0, filledCells(room), "비멤버 착수는 board에 반영되지 않아야 한다");
        verify(roomBroadcaster).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.ERROR && "권한이 없습니다.".equals(m.getMessage())));
    }

    @Test
    void wrongTurnSeatMoveRejected() {
        Room room = startedRoom(); // turn=1 → 흑 차례
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        // 백 자리 principal이 흑 차례에 착수 → 거부
        service.processMove(ROOM_ID, WHITE, 112);

        assertEquals(1, room.getTurn(), "틀린 자리 착수는 turn을 바꾸지 않아야 한다");
        assertEquals(0, filledCells(room), "틀린 자리 착수는 board에 반영되지 않아야 한다");
        verify(roomBroadcaster).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.ERROR && "현재 당신의 차례가 아닙니다.".equals(m.getMessage())));
    }

    @Test
    void memberMoveAccepted() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        // 흑 자리 principal이 흑 차례에 정상 착수
        service.processMove(ROOM_ID, BLACK, 112);

        assertEquals(2, room.getTurn(), "정상 착수 후 turn은 1→2");
        assertEquals(1, filledCells(room), "정상 착수는 board에 반영된다");
        verify(roomBroadcaster).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.ACTION && m.getIndex() != null && m.getIndex() == 112));
    }

    @Test
    void memberSurrenderAccepted_nonMemberIgnored() {
        Room room = startedRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        // 비멤버 기권 → 무시(상태 무변경, 방송 없음)
        service.processSurrender(ROOM_ID, INTRUDER);
        assertTrue(room.isPlaying(), "비멤버 기권은 게임을 종료시키지 않아야 한다");
        verify(roomBroadcaster, never()).broadcast(eq(ROOM_ID), any());

        // 멤버 기권 → 게임 종료 + GAME_END 방송
        service.processSurrender(ROOM_ID, BLACK);
        assertFalse(room.isPlaying(), "멤버 기권은 게임을 종료시킨다");
        verify(roomBroadcaster, times(1)).broadcast(eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.GAME_END));
    }
}
