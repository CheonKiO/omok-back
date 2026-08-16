package org.scoula.room.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * processMove의 방어 로직 검증:
 *  - move index 범위(0~224) 밖 착수가 예외 없이 ERROR로 처리되는지 (#7)
 *  - 같은 방에 동시 MOVE가 들어와도 상태가 손상되지 않는지 (#2)
 * RoomBroadcaster는 Mockito mock, GameService는 실제 렌주 엔진을 물려 실동작 검증한다.
 */
class RoomSocketServiceConcurrencyTest {

    private RoomBroadcaster roomBroadcaster;
    private RoomService roomService;
    private RoomSocketService service;

    private static final String ROOM_ID = "room-1";
    private static final String BLACK_ID = "black-1";
    private static final String BLACK_PRINCIPAL = "user:black";
    private final Player black = new Player(BLACK_ID, "흑돌");

    @BeforeEach
    void setUp() {
        roomBroadcaster = mock(RoomBroadcaster.class);
        roomService = mock(RoomService.class);
        GameService gameService = new GameService(new RenjuRuleEngine());
        service = new RoomSocketService(roomBroadcaster, roomService, gameService,
                org.mockito.Mockito.mock(org.scoula.game.GameArchiveService.class),
                org.mockito.Mockito.mock(org.springframework.scheduling.TaskScheduler.class));
    }

    /** 흑 차례(turn=1)로 진행중인 빈 방. 흑 자리는 세션 principal로 배정됨. */
    private Room playingRoom() {
        Room room = Room.builder()
                .roomId(ROOM_ID)
                .players(java.util.List.of(black))
                .blackPlayer(BLACK_ID)
                .turn(1)
                .board(new int[15][15])
                .isPlaying(true)
                .build();
        room.bindMember(BLACK_PRINCIPAL, BLACK_ID);
        room.setBlackPrincipal(BLACK_PRINCIPAL);
        return room;
    }

    @Test
    void moveIndexOutOfRangeIsRejectedWithoutException() {
        Room room = playingRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        assertDoesNotThrow(() -> service.processMove(ROOM_ID, BLACK_PRINCIPAL, 225));
        assertDoesNotThrow(() -> service.processMove(ROOM_ID, BLACK_PRINCIPAL, -1));

        verify(roomBroadcaster, org.mockito.Mockito.times(2)).broadcast(
                eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.ERROR));
    }

    @Test
    void concurrentValidMovesLeaveRoomStateConsistent() throws InterruptedException {
        Room room = playingRoom();
        when(roomService.getRoom(ROOM_ID)).thenReturn(room);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        // 흑 차례에 두 착수를 동시 발사: 임계구역이 없으면 둘 다 반영되어 turn/board가 손상된다.
        Runnable move = wrapMove(112, ready, fire, done);
        Runnable other = wrapMove(113, ready, fire, done);

        new Thread(move).start();
        new Thread(other).start();

        ready.await();
        fire.countDown();
        done.await();

        // 정확히 한 수만 반영: turn은 1→2, 채워진 칸은 1개.
        assertEquals(2, room.getTurn(), "동시 MOVE 중 하나만 반영되어야 turn=2");
        assertEquals(1, filledCells(room), "동시 MOVE 중 하나만 board에 반영되어야 한다");

        // 나머지 하나는 턴 에러로 거절.
        verify(roomBroadcaster).broadcast(
                eq(ROOM_ID),
                argThat(m -> m.getType() == MessageType.ERROR
                        && "현재 당신의 차례가 아닙니다.".equals(m.getMessage())));
    }

    private Runnable wrapMove(int index, CountDownLatch ready, CountDownLatch fire, CountDownLatch done) {
        return () -> {
            ready.countDown();
            try {
                fire.await();
                service.processMove(ROOM_ID, BLACK_PRINCIPAL, index);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
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
}
