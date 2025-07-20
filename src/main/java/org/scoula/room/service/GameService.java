package org.scoula.room.service;

import org.scoula.room.dto.Room;
import org.springframework.stereotype.Service;

@Service
public class GameService {
    private static final int BOARD_SIZE = 15;
    private static final int[][] DIRECTIONS = {
            {1, 0},  // →
            {0, 1},  // ↓
            {1, 1},  // ↘
            {1, -1}  // ↙
    };

    public enum StoneColor {
        EMPTY(0), BLACK(1), WHITE(2);

        private final int value;

        StoneColor(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static StoneColor fromValue(int value) {
            if (value == 0) return EMPTY;
            return (value % 2 == 1) ? BLACK : WHITE;
        }

        public boolean isBlack() {
            return this == BLACK;
        }

        public boolean isEmpty() {
            return this == EMPTY;
        }
    }

    private static class Position {
        final int x, y;

        Position(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Position move(int dx, int dy, int distance) {
            return new Position(x + dx * distance, y + dy * distance);
        }

        boolean isValid() {
            return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE;
        }
    }

    private static class LineAnalysis {
        final int consecutiveCount;
        final boolean hasOverline;
        final boolean hasFive;

        LineAnalysis(int consecutiveCount, boolean hasOverline, boolean hasFive) {
            this.consecutiveCount = consecutiveCount;
            this.hasOverline = hasOverline;
            this.hasFive = hasFive;
        }
    }

    private Position indexToPosition(int index) {
        return new Position(index % BOARD_SIZE, index / BOARD_SIZE);
    }

    private StoneColor getStoneAt(int[][] board, Position pos) {
        if (!pos.isValid()) return StoneColor.EMPTY;
        return StoneColor.fromValue(board[pos.y][pos.x]);
    }

    public boolean checkGameEnd(Room room, int index) {
        Position pos = indexToPosition(index);
        int[][] board = room.getBoard();
        StoneColor color = getStoneAt(board, pos);

        if (color.isEmpty()) return false;

        return hasWinningLine(board, pos, color);
    }

    private boolean hasWinningLine(int[][] board, Position pos, StoneColor color) {
        for (int[] dir : DIRECTIONS) {
            int count = countConsecutiveStones(board, pos, dir[0], dir[1], color);
            if (count == 5) return true;
        }
        return false;
    }

    private int countConsecutiveStones(int[][] board, Position center, int dx, int dy, StoneColor targetColor) {
        int count = 1; // 현재 위치 포함

        // 양방향으로 확장하여 연속된 돌 개수 세기
        for (int direction : new int[]{-1, 1}) {
            Position current = center.move(dx, dy, direction);
            while (current.isValid() && getStoneAt(board, current) == targetColor) {
                count++;
                current = current.move(dx, dy, direction);
            }
        }

        return count;
    }

    private LineAnalysis analyzeLineForOverline(int[][] board, Position pos, int dx, int dy) {
        int count = countConsecutiveStones(board, pos, dx, dy, StoneColor.BLACK);
        boolean hasOverline = count >= 6;
        boolean hasFive = count == 5;

        return new LineAnalysis(count, hasOverline, hasFive);
    }

    public int countFour(int[][] board, int index) {
        Position pos = indexToPosition(index);
        int fourCount = 0;

        for (int[] dir : DIRECTIONS) {
            fourCount += countFourInDirection(board, pos, dir[0], dir[1]);
        }

        return fourCount;
    }

    private int countFourInDirection(int[][] board, Position pos, int dx, int dy) {
        int count = 0;

        // 4개 패턴 체크를 위한 다양한 위치 조합
        Pattern[] patterns = {
                new Pattern(
                        new int[]{0, 1, 2, 3},
                        new boolean[]{true, true, true, true}
                ),
                new Pattern(
                        new int[]{0, 1, 2, 3, 4},
                        new boolean[]{true, true, false, true, true}
                ),
                new Pattern(
                        new int[]{0, 1, 2, 3, 4},
                        new boolean[]{false, true, true, false, true}
                ),
                new Pattern(
                        new int[]{0, 1, 2, 3, 4},
                        new boolean[]{true, false, true, true, true}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2},
                        new boolean[]{true, true, true, true}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2, 3},
                        new boolean[]{true, true, false, true, true}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2, 3},
                        new boolean[]{true, true, true, false, true}
                ),
                new Pattern(
                        new int[]{-2, -1, 0, 1, 2},
                        new boolean[]{true, true, true, false, true}
                )
        };

        for (Pattern pattern : patterns) {
            for (int sign : new int[]{-1, 1}) {
                if (hasPattern(board, pos, dx * sign, dy * sign, pattern)) {
                    count++;
                }
            }
        }

        return count;
    }

    public int countOpenThrees(int[][] board, int index) {
        Position pos = indexToPosition(index);
        int openThreeCount = 0;

        for (int[] dir : DIRECTIONS) {
            openThreeCount += countOpenThreeInDirection(board, pos, dir[0], dir[1]);
        }

        return openThreeCount;
    }

    private int countOpenThreeInDirection(int[][] board, Position pos, int dx, int dy) {
        int count = 0;

        Pattern[] patterns = {
                new Pattern(
                        new int[]{-2, -1, 0, 1, 2},
                        new boolean[]{false, true, true, true, false}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2, 3},
                        new boolean[]{false, true, true, true, false}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2, 3, 4},
                        new boolean[]{false, true, true, false, true, false}
                ),
                new Pattern(
                        new int[]{-1, 0, 1, 2, 3, 4},
                        new boolean[]{false, true, false, true, true, false}
                ),
                new Pattern(
                        new int[]{-2, -1, 0, 1, 2},
                        new boolean[]{false, true, true, false, true, false}
                )
        };
        for (Pattern pattern : patterns) {
            for (int sign : new int[]{-1, 1}) {
                if (hasPattern(board, pos, dx * sign, dy * sign, pattern)) {
                    count++;
                }
            }
        }

        return count;
    }

    private static class Pattern {
        final int[] offsets;
        final boolean[] shouldBeBlack;

        Pattern(int[] offsets, boolean[] shouldBeBlack) {
            this.offsets = offsets;
            this.shouldBeBlack = shouldBeBlack;
        }
    }

    private boolean hasPattern(int[][] board, Position center, int dx, int dy, Pattern pattern) {

        for (int i = 0; i < pattern.offsets.length; i++) {
            Position checkPos = center.move(dx, dy, pattern.offsets[i]);
            StoneColor stone = getStoneAt(board, checkPos);

            if (pattern.shouldBeBlack[i]) { // 검은 돌이어야만 한다.
                if (!stone.isBlack()) return false;
            } else {
                if (!stone.isEmpty()) return false;
            }
        }
        return true;
    }

    public void applyMove(Room room, int index) {
        Position pos = indexToPosition(index);
        int[][] board = room.getBoard();
        int turn = room.getTurn();

        board[pos.y][pos.x] = turn;
        room.setTurn(turn + 1);
    }

    public boolean isForbiddenMove(Room room, int index) {
        Position pos = indexToPosition(index);
        int[][] board = room.getBoard();

        // 임시로 돌을 놓고 계산
        int originalValue = board[pos.y][pos.x];
        board[pos.y][pos.x] = room.getTurn();

        try {
            boolean hasOverline = hasOverlineAfterMove(board, pos);
            boolean hasFive = hasFiveAfterMove(board, pos);
            int openThrees = countOpenThrees(board, index);
            int fours = countFour(board, index);
            System.out.println("hasOverline: " + hasOverline + ", hasFive: " + hasFive + ", openThrees: " + openThrees + ", fours: " + fours);
            return (hasOverline || openThrees >= 2 || fours >= 2) && !hasFive;
        } finally {
            // 원래 상태로 복원
            board[pos.y][pos.x] = originalValue;
        }
    }

    private boolean hasOverlineAfterMove(int[][] board, Position pos) {
        for (int[] dir : DIRECTIONS) {
            LineAnalysis analysis = analyzeLineForOverline(board, pos, dir[0], dir[1]);
            if (analysis.hasOverline) return true;
        }
        return false;
    }

    private boolean hasFiveAfterMove(int[][] board, Position pos) {
        for (int[] dir : DIRECTIONS) {
            LineAnalysis analysis = analyzeLineForOverline(board, pos, dir[0], dir[1]);
            if (analysis.hasFive) return true;
        }
        return false;
    }
}