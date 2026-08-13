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
            boolean isWin = color.isBlack() ? count == 5 : count >= 5;
            if (isWin) return true;
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
            if (hasFourInDirection(board, pos, dir[0], dir[1])) {
                fourCount++;
            }
        }

        return fourCount;
    }

    // 이 방향으로 '사(four)'가 성립하는지 검사.
    // 사 = 빈 칸 하나를 채우면 '정확히 5목'이 되는 모양. 6목(장목)이 되는 갭이나
    // 양끝이 막혀 오목이 될 수 없는 모양은 사가 아니다. 한 방향에 최대 1개로 센다.
    private boolean hasFourInDirection(int[][] board, Position pos, int dx, int dy) {
        for (int d = -4; d <= 4; d++) {
            if (d == 0) continue;
            Position empty = pos.move(dx, dy, d);
            if (!empty.isValid()) continue;
            if (!getStoneAt(board, empty).isEmpty()) continue;

            // 빈 칸에 임시로 흑을 놓아 pos를 지나는 연속 목수를 센다.
            board[empty.y][empty.x] = StoneColor.BLACK.getValue();
            int run = countConsecutiveStones(board, pos, dx, dy, StoneColor.BLACK);
            board[empty.y][empty.x] = StoneColor.EMPTY.getValue();

            if (run == 5) return true; // 정확히 5목 완성 → 사
        }
        return false;
    }

    public int countOpenThrees(int[][] board, int index) {
        Position pos = indexToPosition(index);
        int openThreeCount = 0;

        for (int[] dir : DIRECTIONS) {
            if (hasOpenThreeInDirection(board, pos, dir[0], dir[1])) {
                openThreeCount++;
            }
        }

        return openThreeCount;
    }

    // 이 방향으로 '열린 삼'이 성립하는지 검사.
    // 열린 삼 = 빈 칸 하나를 채우면 '열린 사(_●●●●_)'가 되는 모양.
    // 단, 이미 사(four)인 모양은 삼으로 세지 않는다(사 > 삼).
    private boolean hasOpenThreeInDirection(int[][] board, Position pos, int dx, int dy) {
        if (hasFourInDirection(board, pos, dx, dy)) return false;

        for (int d = -4; d <= 4; d++) {
            if (d == 0) continue;
            Position empty = pos.move(dx, dy, d);
            if (!empty.isValid()) continue;
            if (!getStoneAt(board, empty).isEmpty()) continue;

            board[empty.y][empty.x] = StoneColor.BLACK.getValue();
            boolean openFour = formsOpenFour(board, pos, dx, dy);
            board[empty.y][empty.x] = StoneColor.EMPTY.getValue();

            if (openFour) return true;
        }
        return false;
    }

    // pos를 지나는 연속 흑이 정확히 4목이고, 양끝(연속 바로 바깥)이 모두
    // 판 안의 빈 칸이면 '열린 사'. 벽에 막힌 4는 열린 사가 아니다.
    private boolean formsOpenFour(int[][] board, Position pos, int dx, int dy) {
        int count = 1;

        Position forward = pos.move(dx, dy, 1);
        while (getStoneAt(board, forward).isBlack()) {
            count++;
            forward = forward.move(dx, dy, 1);
        }
        Position backward = pos.move(dx, dy, -1);
        while (getStoneAt(board, backward).isBlack()) {
            count++;
            backward = backward.move(dx, dy, -1);
        }

        if (count != 4) return false;
        return isEmptyInBounds(board, forward) && isEmptyInBounds(board, backward);
    }

    private boolean isEmptyInBounds(int[][] board, Position p) {
        return p.isValid() && getStoneAt(board, p).isEmpty();
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