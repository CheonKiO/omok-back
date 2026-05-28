package org.scoula.room.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isPlaying")
    private boolean isPlaying;
    private int ready;

    public void initGame(String blackPlayer){
        board = new int[15][15];
        turn = 1;
        ready = 0;
        isPlaying = true;
        this.blackPlayer = blackPlayer;
    }
}

