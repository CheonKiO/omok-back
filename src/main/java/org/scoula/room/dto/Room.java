package org.scoula.room.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
public class Room {
    private String title;
    private String roomId;
    private List<Player> players;
    private String blackPlayer;
    private int turn;
    private int[][] board;
    private boolean isPlaying;
    private int ready;

    private long turnTimerStartTime;  // 현재 턴이 시작된 시각 (밀리초)
    private int turnLimit;    // 턴마다 정해진 타이머 길이 (예: 30000ms)

    public void initGame(String blackPlayer){
        board = new int[15][15];
        turn = 1;
        ready = 0;
        isPlaying = true;
        this.blackPlayer = blackPlayer;
    }
}

