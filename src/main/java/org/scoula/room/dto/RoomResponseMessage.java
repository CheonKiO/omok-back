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
}
