package org.scoula.game;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class GameArchiveServiceTest {

    private final GameRepository repo = mock(GameRepository.class);
    private final GameArchiveService service = new GameArchiveService(repo);

    /** 흑=user:2, 백=user:3, 착수 [112,113] 인 종료된 방. */
    private Room roomWith(String blackPrincipal, String whitePrincipal) {
        Room room = Room.builder()
                .roomId("room-1")
                .players(new ArrayList<>(List.of(new Player("uuidB", "흑돌"), new Player("uuidW", "백돌"))))
                .board(new int[15][15])
                .moveHistory(new ArrayList<>(List.of(112, 113)))
                .build();
        room.bindMember(blackPrincipal, "uuidB", "흑돌");
        room.bindMember(whitePrincipal, "uuidW", "백돌");
        room.setBlackPrincipal(blackPrincipal);
        room.setWhitePrincipal(whitePrincipal);
        return room;
    }

    @Test
    void savesGameWhenBothMembers() {
        Room room = roomWith("2", "3");
        service.archive(room, WinnerColor.BLACK, EndReason.WIN_5);

        ArgumentCaptor<Game> cap = ArgumentCaptor.forClass(Game.class);
        verify(repo).save(cap.capture());
        Game g = cap.getValue();
        assertEquals(2L, g.getBlackUserId());
        assertEquals(3L, g.getWhiteUserId());
        assertEquals("흑돌", g.getBlackName());
        assertEquals("백돌", g.getWhiteName());
        assertEquals(WinnerColor.BLACK, g.getWinner());
        assertEquals(EndReason.WIN_5, g.getEndReason());
        assertEquals("112,113", g.getMoves());
    }

    @Test
    void savesWhenOneMemberOneGuest() {
        Room room = roomWith("2", "guest-abc"); // 회원 vs 게스트
        service.archive(room, WinnerColor.WHITE, EndReason.SURRENDER);

        ArgumentCaptor<Game> cap = ArgumentCaptor.forClass(Game.class);
        verify(repo).save(cap.capture());
        Game g = cap.getValue();
        assertEquals(2L, g.getBlackUserId());
        assertNull(g.getWhiteUserId()); // 게스트 자리 null
    }

    @Test
    void skipsGuestVsGuest() {
        Room room = roomWith("guest-a", "guest-b");
        service.archive(room, WinnerColor.BLACK, EndReason.WIN_5);
        verify(repo, never()).save(any());
    }

    @Test
    void skipsWhenSeatPrincipalMissing() {
        Room room = roomWith("2", "3");
        room.setWhitePrincipal(null); // 비정상 상태
        service.archive(room, WinnerColor.BLACK, EndReason.WIN_5);
        verify(repo, never()).save(any());
    }
}
