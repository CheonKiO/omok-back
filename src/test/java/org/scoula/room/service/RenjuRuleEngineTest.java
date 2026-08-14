package org.scoula.room.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RenjuRuleEngine 직접 검증. GameServiceTest 픽스처를 미러하되 Room 없이 board/index만으로 엔진을 겨눈다.
 * 좌표계: index = y*15 + x, 흑=1(홀수), 백=2(짝수), 빈칸=0.
 */
class RenjuRuleEngineTest {

    private static final int SIZE = 15;
    private final RenjuRuleEngine engine = new RenjuRuleEngine();

    /** 빈 15x15 보드. */
    private int[][] board() {
        return new int[SIZE][SIZE];
    }

    private void black(int[][] b, int x, int y) {
        b[y][x] = 1;
    }

    private int idx(int x, int y) {
        return y * SIZE + x;
    }

    // ── 오탐: 장목이 되는 four는 four가 아니다 ──
    // 행: ●●●_●● (x4,x6,x8,x9 흑). x5에 착수 시 x7을 채우면 6목(장목) → four 아님 → 4-4 금수 아님.
    @Test
    void overlineGapIsNotDoubleFour() {
        int[][] b = board();
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 8, 7);
        black(b, 9, 7);
        assertFalse(engine.isForbidden(b, idx(5, 7)),
                "장목으로만 완성되는 갭은 four가 아니므로 4-4 금수가 아니다");
    }

    // ── 가드: 진짜 4-4는 금수 유지 ──
    @Test
    void genuineDoubleFourIsForbidden() {
        int[][] b = board();
        black(b, 5, 7);
        black(b, 6, 7);
        black(b, 8, 7);
        black(b, 7, 5);
        black(b, 7, 6);
        black(b, 7, 8);
        assertTrue(engine.isForbidden(b, idx(7, 7)),
                "가로 four + 세로 four = 진짜 4-4, 금수여야 한다");
    }

    // ── 오탐: 사(four)인 모양은 삼(three)으로 세지 않는다 ──
    // 행: ●_●●● (x2,x4,x5,x6 흑). x3을 채우면 5목 → 사이므로 open-three로 세면 안 된다.
    @Test
    void fourShapeIsNotCountedAsOpenThree() {
        int[][] b = board();
        black(b, 2, 7);
        black(b, 4, 7);
        black(b, 5, 7);
        black(b, 6, 7);
        assertEquals(0, engine.countOpenThrees(b, idx(5, 7)),
                "빈칸 채우면 5목이 되는 사는 삼으로 세지 않는다");
    }

    // ── 가드: 진짜 3-3은 금수 유지 ──
    @Test
    void genuineDoubleThreeIsForbidden() {
        int[][] b = board();
        black(b, 6, 7);
        black(b, 8, 7);
        black(b, 7, 6);
        black(b, 7, 8);
        assertTrue(engine.isForbidden(b, idx(7, 7)),
                "가로 삼 + 세로 삼 = 진짜 3-3, 금수여야 한다");
    }

    // ── 가드: 정확히 5목을 만드는 수는 승리 우선, 금수가 아니다 ──
    @Test
    void movingToFiveIsNeverForbidden() {
        int[][] b = board();
        black(b, 3, 7);
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 7, 7);
        assertFalse(engine.isForbidden(b, idx(5, 7)),
                "5목 완성 수는 금수가 아니다(승리 우선)");
    }

    // ── 가드: 흑의 6목(장목)은 금수 ──
    @Test
    void overlineIsForbiddenForBlack() {
        int[][] b = board();
        black(b, 2, 7);
        black(b, 3, 7);
        black(b, 4, 7);
        black(b, 6, 7);
        black(b, 7, 7);
        assertTrue(engine.isForbidden(b, idx(5, 7)), "흑 6목(장목)은 금수");
    }
}
