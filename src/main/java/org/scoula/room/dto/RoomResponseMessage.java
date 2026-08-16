package org.scoula.room.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값은 json에서 제외하기
public class RoomResponseMessage {
    private String sender;
    private String roomId;
    private MessageType type;
    private String message;
    private Integer index;
    private Integer turn;
    private String blackPlayer;
    /** GAME_END 전용. "BLACK" | "WHITE". 프론트가 승자 라벨을 추론하지 않도록 서버가 명시한다. */
    private String winner;
}
