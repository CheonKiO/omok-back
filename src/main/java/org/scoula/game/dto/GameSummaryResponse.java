package org.scoula.game.dto;

import org.scoula.game.EndReason;
import org.scoula.game.WinnerColor;

import java.time.LocalDateTime;

/** 내 기보 목록 항목. myColor/result는 조회자 기준. */
public record GameSummaryResponse(
        Long id,
        String opponentName,
        WinnerColor myColor,
        String result, // "WIN" | "LOSS"
        EndReason endReason,
        LocalDateTime createdAt) {
}
