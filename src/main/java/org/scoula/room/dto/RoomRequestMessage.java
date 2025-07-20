package org.scoula.room.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequestMessage {
    private Player sender;
    private String roomId;
    private MessageType type;
    private Integer index;

}
