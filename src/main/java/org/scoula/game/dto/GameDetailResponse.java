package org.scoula.game.dto;

import org.scoula.game.EndReason;
import org.scoula.game.WinnerColor;

import java.time.LocalDateTime;
import java.util.List;

/** 복기용 기보 상세. moves는 착수 index를 놓인 순서대로. */
public record GameDetailResponse(
        Long id,
        String blackName,
        String whiteName,
        WinnerColor winner,
        EndReason endReason,
        List<Integer> moves,
        LocalDateTime createdAt) {
}
