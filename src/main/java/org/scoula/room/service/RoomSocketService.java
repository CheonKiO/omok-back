package org.scoula.room.service;

import lombok.RequiredArgsConstructor;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;
import org.scoula.room.dto.RoomResponseMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@RequiredArgsConstructor
public class RoomSocketService {
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;
    public void notifyGameStart(String roomId) {
        Room room = roomService.getRoom(roomId);
        List<Player> players = room.getPlayers();

        if (players.size() != 2) return;

        // 무작위로 흑백 정하기
        boolean firstIsBlack = Math.random() > 0.5;
        String blackPlayer = (firstIsBlack ? players.get(0).getId() : players.get(1).getId());
        //게임 시작 시 보드와 턴 초기화
        roomService.getRoom(roomId).initGame(blackPlayer);
        RoomResponseMessage message = RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.GAME_START)
                .blackPlayer(blackPlayer)
                .message("게임이 시작되었습니다")
                .build();
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                message
        );
    }

}

