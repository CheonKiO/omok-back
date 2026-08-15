package org.scoula.game;

import org.junit.jupiter.api.Test;
import org.scoula.game.dto.GameDetailResponse;
import org.scoula.game.dto.GameSummaryResponse;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameQueryServiceTest {

    private final GameRepository repo = mock(GameRepository.class);
    private final GameQueryService service = new GameQueryService(repo);

    private Game sampleGame() {
        return Game.builder()
                .id(10L)
                .blackUserId(2L).whiteUserId(3L)
                .blackName("흑돌").whiteName("백돌")
                .winner(WinnerColor.BLACK)
                .endReason(EndReason.WIN_5)
                .moves("112,113,96")
                .build();
    }

    @Test
    void summaryFromBlackPerspective() {
        when(repo.findByBlackUserIdOrWhiteUserIdOrderByCreatedAtDesc(2L, 2L))
                .thenReturn(List.of(sampleGame()));
        List<GameSummaryResponse> list = service.myGames(2L);
        assertEquals(1, list.size());
        GameSummaryResponse s = list.get(0);
        assertEquals(WinnerColor.BLACK, s.myColor());
        assertEquals("WIN", s.result());       // 흑이 이겼고 내가 흑
        assertEquals("백돌", s.opponentName());
    }

    @Test
    void summaryFromWhitePerspectiveIsLoss() {
        when(repo.findByBlackUserIdOrWhiteUserIdOrderByCreatedAtDesc(3L, 3L))
                .thenReturn(List.of(sampleGame()));
        GameSummaryResponse s = service.myGames(3L).get(0);
        assertEquals(WinnerColor.WHITE, s.myColor());
        assertEquals("LOSS", s.result());       // 흑이 이겼는데 내가 백
        assertEquals("흑돌", s.opponentName());
    }

    @Test
    void guestUserIdGetsEmpty() {
        assertTrue(service.myGames(null).isEmpty());
        verifyNoInteractions(repo);
    }

    @Test
    void detailParsesMovesForParticipant() {
        when(repo.findById(10L)).thenReturn(Optional.of(sampleGame()));
        GameDetailResponse d = service.detailForUser(10L, 2L).orElseThrow();
        assertEquals(List.of(112, 113, 96), d.moves());
        assertEquals("흑돌", d.blackName());
    }

    @Test
    void detailEmptyForNonParticipant() {
        when(repo.findById(10L)).thenReturn(Optional.of(sampleGame()));
        assertTrue(service.detailForUser(10L, 99L).isEmpty()); // 참가자 아님
    }
}
