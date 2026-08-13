package org.scoula.room.service;

import org.junit.jupiter.api.Test;
import org.scoula.room.dto.Room;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 렌주 금수 판정 정확성 테스트.
 * 좌표계: index = y*15 + x, 흑=1(홀수), 백=2(짝수), 빈칸=0.
 * isForbiddenMove는 turn 값을 index에 임시로 놓고 판정하므로 board에는 해당 칸을 비워두고 turn=1(흑)로 둔다.
 */
class GameServiceTest {

    private static final int SIZE = 15;
    private final GameService gameService = new GameService();

    /** 빈 15x15 보드. */
    private int[][] board() {
        return new int[SIZE][SIZE];
    }

    private void black(int[][] b, int x, int y) {
        b[y][x] = 1;
    }

    private void white(int[][] b, int x, int y) {
        b[y][x] = 2;
    }

    private int idx(int x, int y) {
        return y * SIZE + x;
    }

    /** 흑 차례(turn=1)에 index에 착수했다고 보고 금수 여부 판정. */
    private boolean forbidden(int[][] b, int x, int y) {
        Room room = Room.builder().board(b).turn(1).build();
        return gameService.isForbiddenMove(room, idx(x, y));
    }

    // ── 오탐: 장목이 되는 four는 four가 아니다 (BLOCKER1) ──
    // 행: ●●●_●● (x4,x5,x6 흑, x7 빈, x8,x9 흑). x5에 착수.
    // x7을 채우면 x4~x9 = 6목(장목) → 진짜 four 아님. 실제 four 0개 → 합법수.
    @Test
    void gapMoveThatWouldFormOverlineIsNotDoubleFour() {
        int[][] b = board();
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 8, 7);
        black(b, 9, 7);
        // x5(=idx110)에 착수 시 오탐으로 4-4 금수 처리되면 안 된다.
        assertFalse(forbidden(b, 5, 7),
                "장목으로만 완성되는 갭은 four가 아니므로 4-4 금수가 아니다");
    }

    // ── 가드: 진짜 4-4는 금수 유지 ──
    // 가로 four(x5,x6,_,x8 → x7 착수로 x5~x8) + 세로 four(y5,y6,_,y8 → x7 착수로 y5~y8).
    @Test
    void genuineDoubleFourIsForbidden() {
        int[][] b = board();
        // 가로
        black(b, 5, 7);
        black(b, 6, 7);
        black(b, 8, 7);
        // 세로
        black(b, 7, 5);
        black(b, 7, 6);
        black(b, 7, 8);
        assertTrue(forbidden(b, 7, 7),
                "가로 four + 세로 four = 진짜 4-4, 금수여야 한다");
    }

    // ── 가드: 주변에 돌 하나뿐인 평범한 착수는 합법 ──
    @Test
    void ordinaryMoveIsLegal() {
        int[][] b = board();
        black(b, 7, 7);
        assertFalse(forbidden(b, 5, 5), "고립된 착수는 금수가 아니다");
    }

    private int openThrees(int[][] b, int x, int y) {
        return gameService.countOpenThrees(b, idx(x, y));
    }

    // ── 오탐: 사(four)인 모양은 삼(three)으로 세지 않는다 (BLOCKER3) ──
    // 행: ●_●●● (x2, 빈 x3, x4,x5,x6). x3을 채우면 x2~x6 = 정확히 5목 → 이건 사다.
    // 사이므로 open-three로 세면 안 된다.
    @Test
    void fourShapeIsNotCountedAsOpenThree() {
        int[][] b = board();
        black(b, 2, 7);
        black(b, 4, 7);
        black(b, 5, 7);
        black(b, 6, 7);
        assertEquals(0, openThrees(b, 5, 7),
                "빈칸 채우면 5목이 되는 사는 삼으로 세지 않는다");
    }

    // ── 가드: 진짜 열린 삼은 1개로 센다 ──
    // 행: _●●●_ (x4,x5,x6 흑, x3·x7 빈). x3 또는 x7에 두면 열린 사가 된다.
    @Test
    void genuineOpenThreeCountsAsOne() {
        int[][] b = board();
        black(b, 4, 7);
        black(b, 5, 7);
        black(b, 6, 7);
        assertEquals(1, openThrees(b, 5, 7), "고립된 열린 삼은 정확히 1개");
    }

    // ── 가드: 진짜 3-3은 금수 유지 ──
    // 가로 _●C●_ 와 세로 _●C●_ 가 착수점에서 교차 → 열린 삼 2개.
    @Test
    void genuineDoubleThreeIsForbidden() {
        int[][] b = board();
        black(b, 6, 7);
        black(b, 8, 7); // 가로: x6 _ x8, x7 착수 → _●●●_
        black(b, 7, 6);
        black(b, 7, 8); // 세로: y6 _ y8, y7 착수 → _●●●_
        assertTrue(forbidden(b, 7, 7), "가로 삼 + 세로 삼 = 진짜 3-3, 금수여야 한다");
    }

    // ── 가드: 정확히 5목을 만드는 수는 금수보다 승리 우선 ──
    @Test
    void movingToFiveIsNeverForbidden() {
        int[][] b = board();
        black(b, 3, 7);
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 7, 7); // x3,x4,_,x6,x7 → x5 착수 시 x3~x7 = 5목
        assertFalse(forbidden(b, 5, 7), "5목 완성 수는 금수가 아니다(승리 우선)");
    }

    // ── 가드: 흑의 6목(장목)은 금수 ──
    @Test
    void overlineIsForbiddenForBlack() {
        int[][] b = board();
        black(b, 2, 7);
        black(b, 3, 7);
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 7, 7); // x2,x3,x4,_,x6,x7 → x5 착수 시 x2~x7 = 6목
        assertTrue(forbidden(b, 5, 7), "흑 6목(장목)은 금수");
    }
}
