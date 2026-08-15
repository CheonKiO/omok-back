package org.scoula.game;

import lombok.RequiredArgsConstructor;
import org.scoula.game.dto.GameDetailResponse;
import org.scoula.game.dto.GameSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameQueryService {

    private final GameRepository gameRepository;

    /** 내가 흑/백으로 참여한 기보 목록(최신순). userId 없으면(게스트) 빈 목록. */
    @Transactional(readOnly = true)
    public List<GameSummaryResponse> myGames(Long userId) {
        if (userId == null) return List.of();
        return gameRepository.findByBlackUserIdOrWhiteUserIdOrderByCreatedAtDesc(userId, userId).stream()
                .map(g -> toSummary(g, userId))
                .collect(Collectors.toList());
    }

    /** 참가자 본인만 조회. 미존재/비참가자면 empty(컨트롤러에서 404 — 존재 여부 미노출). */
    @Transactional(readOnly = true)
    public Optional<GameDetailResponse> detailForUser(Long id, Long userId) {
        return gameRepository.findById(id)
                .filter(g -> userId != null
                        && (userId.equals(g.getBlackUserId()) || userId.equals(g.getWhiteUserId())))
                .map(this::toDetail);
    }

    private GameSummaryResponse toSummary(Game g, Long userId) {
        boolean iamBlack = userId.equals(g.getBlackUserId());
        WinnerColor myColor = iamBlack ? WinnerColor.BLACK : WinnerColor.WHITE;
        String opponent = iamBlack ? g.getWhiteName() : g.getBlackName();
        String result = (g.getWinner() == myColor) ? "WIN" : "LOSS";
        return new GameSummaryResponse(g.getId(), opponent, myColor, result, g.getEndReason(), g.getCreatedAt());
    }

    private GameDetailResponse toDetail(Game g) {
        List<Integer> moves = g.getMoves().isBlank()
                ? List.of()
                : Arrays.stream(g.getMoves().split(",")).map(Integer::parseInt).collect(Collectors.toList());
        return new GameDetailResponse(g.getId(), g.getBlackName(), g.getWhiteName(),
                g.getWinner(), g.getEndReason(), moves, g.getCreatedAt());
    }
}
