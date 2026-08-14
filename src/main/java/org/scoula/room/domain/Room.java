package org.scoula.room.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Builder
public class Room {
    private String title;
    private String roomId;
    private String password;
    private List<Player> players;
    private String blackPlayer;
    private int turn;
    private int[][] board;
    @JsonProperty("isPlaying")
    private boolean isPlaying;
    private int ready;

    // 신원(A''): 자리 소유는 인증 principal(JWT subject)을 '키'로 기록한다.
    // 키가 principal이므로 클라가 보낸 player.id로는 남의 자리를 덮어쓰거나 탈취할 수 없다.
    // 값(playerId)은 표시/reconnect 라벨일 뿐 신원이 아니다. bindMember는 인증 경로에서만 호출.
    @JsonIgnore
    @Builder.Default
    private Map<String, String> playerIdByPrincipal = new ConcurrentHashMap<>();
    // 게임 시작 시 지정되는 자리별 소유 principal.
    @JsonIgnore
    private String blackPrincipal;
    @JsonIgnore
    private String whitePrincipal;

    public void initGame(String blackPlayer){
        board = new int[15][15];
        turn = 1;
        ready = 0;
        isPlaying = true;
        this.blackPlayer = blackPlayer;
    }

    /**
     * HTTP join 시 인증 principal을 키로 자리를 기록한다(서로 다른 principal 최대 2).
     * 같은 principal 재호출은 표시 라벨만 갱신하고, 이미 2자리가 찬 방에 새 principal이면 거부한다.
     * 키가 principal이라 클라가 보낸 playerId로는 남의 자리를 건드릴 수 없다. 인증 경로에서만 호출.
     */
    public void bindMember(String principal, String playerId, String name) {
        if (principal == null || playerId == null) return;
        if (!playerIdByPrincipal.containsKey(principal) && playerIdByPrincipal.size() >= 2) return;
        playerIdByPrincipal.put(principal, playerId);
    }

    /** principal이 이 방의 자리 소유자인지. payload로 온 id가 아니라 인증 principal만 인정. */
    public boolean isMember(String principal) {
        return principal != null && playerIdByPrincipal.containsKey(principal);
    }

    /** 표시용 playerId에 대응하는 소유 principal 역조회. 미바인딩이면 null. */
    public String principalOf(String playerId) {
        if (playerId == null) return null;
        for (Map.Entry<String, String> e : playerIdByPrincipal.entrySet()) {
            if (playerId.equals(e.getValue())) return e.getKey();
        }
        return null;
    }

    public String blackPrincipal() {
        return blackPrincipal;
    }

    public String whitePrincipal() {
        return whitePrincipal;
    }

    /** turn 홀수=흑 자리, 짝=백 자리 principal과 일치하는지. */
    public boolean isTurnOwner(String principal, int turn) {
        if (principal == null) return false;
        String seat = (turn % 2 == 1) ? blackPrincipal : whitePrincipal;
        return principal.equals(seat);
    }
}

