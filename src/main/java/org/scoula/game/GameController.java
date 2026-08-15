package org.scoula.game;

import lombok.RequiredArgsConstructor;
import org.scoula.game.dto.GameDetailResponse;
import org.scoula.game.dto.GameSummaryResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameQueryService gameQueryService;

    /** 내 기보 목록. 회원만 데이터 있음(게스트=빈 목록). */
    @GetMapping("")
    public ResponseEntity<List<GameSummaryResponse>> myGames(Authentication authentication) {
        Long userId = memberUserId(authentication.getName());
        return ResponseEntity.ok(gameQueryService.myGames(userId));
    }

    /** 기보 상세(복기). 참가자 본인만, 아니면 404(존재 미노출). */
    @GetMapping("/{id}")
    public ResponseEntity<GameDetailResponse> detail(@PathVariable Long id, Authentication authentication) {
        Long userId = memberUserId(authentication.getName());
        return gameQueryService.detailForUser(id, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 회원 principal(=user id 숫자)이면 Long, 게스트("guest-*")면 null. */
    private Long memberUserId(String principal) {
        if (principal != null && principal.matches("\\d+")) {
            return Long.parseLong(principal);
        }
        return null;
    }
}
