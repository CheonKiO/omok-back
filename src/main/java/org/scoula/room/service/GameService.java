package org.scoula.room.service;

import lombok.RequiredArgsConstructor;
import org.scoula.room.dto.Room;
import org.springframework.stereotype.Service;

/**
 * Room 상태(board·turn)와 순수 규칙 엔진(RenjuRuleEngine) 사이의 위임체.
 * 렌주 판정 로직 자체는 RenjuRuleEngine이 담당하고, 여기서는 Room↔board 변환과 turn 갱신만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class GameService {
    private static final int BOARD_SIZE = 15;

    private final RenjuRuleEngine engine;

    public boolean checkGameEnd(Room room, int index) {
        return engine.isWin(room.getBoard(), index, room.getTurn() % 2 == 1);
    }

    public int countOpenThrees(int[][] board, int index) {
        return engine.countOpenThrees(board, index);
    }

    public void applyMove(Room room, int index) {
        int x = index % BOARD_SIZE;
        int y = index / BOARD_SIZE;
        int[][] board = room.getBoard();
        int turn = room.getTurn();

        board[y][x] = turn;
        room.setTurn(turn + 1);
    }

    public boolean isForbiddenMove(Room room, int index) {
        return engine.isForbidden(room.getBoard(), index);
    }
}
