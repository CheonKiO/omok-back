package org.scoula.room.service;

import lombok.RequiredArgsConstructor;
import org.scoula.room.dto.RoomResponseMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomBroadcaster {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String roomId, RoomResponseMessage message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, message);
    }
}
