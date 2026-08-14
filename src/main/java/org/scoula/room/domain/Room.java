package org.scoula.room.domain;

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

    // 신원(A''): 자리 소유는 표시용 player.id가 아니라 인증 principal(JWT subject)로 기록한다.
    // playerId(표시/reconnect 라벨) → principal. bindMember는 인증 경로에서만 호출해야 한다.
    @Builder.Default
    private Map<String, String> principalByPlayerId = new ConcurrentHashMap<>();
    // 게임 시작 시 지정되는 자리별 소유 principal.
    private String blackPrincipal;
    private String whitePrincipal;

    public void initGame(String blackPlayer){
        board = new int[15][15];
        turn = 1;
        ready = 0;
        isPlaying = true;
        this.blackPlayer = blackPlayer;
    }

    /** HTTP join 시 자리에 인증 principal을 기록한다(최대 2). playerId/name은 표시용. */
    public void bindMember(String principal, String playerId, String name) {
        if (principal == null || playerId == null) return;
        if (!principalByPlayerId.containsKey(playerId) && principalByPlayerId.size() >= 2) return;
        principalByPlayerId.put(playerId, principal);
    }

    /** principal이 이 방의 자리 소유자인지. payload로 온 id가 아니라 인증 principal만 인정. */
    public boolean isMember(String principal) {
        return principal != null && principalByPlayerId.containsValue(principal);
    }

    /** playerId(표시용)에 매핑된 소유 principal. 미바인딩이면 null. */
    public String principalOf(String playerId) {
        return principalByPlayerId.get(playerId);
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

