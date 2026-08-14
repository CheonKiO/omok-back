package org.scoula.room.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.dto.Player;
import org.scoula.room.dto.Room;
import org.scoula.room.dto.RoomResponseMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSocketService {

    private final RoomBroadcaster roomBroadcaster;
    private final RoomService roomService;
    private final GameService gameService;

    private void broadcast(String roomId, RoomResponseMessage message) {
        roomBroadcaster.broadcast(roomId, message);
    }

    public void notifyGameStart(String roomId) {
        Room room = roomService.getRoom(roomId);
        synchronized (room) {
            List<Player> players = room.getPlayers();
            if (players.size() != 2) return;

            boolean firstIsBlack = Math.random() > 0.5;
            String blackId = firstIsBlack ? players.get(0).id() : players.get(1).id();
            String blackName = firstIsBlack ? players.get(0).name() : players.get(1).name();
            String whiteName = firstIsBlack ? players.get(1).name() : players.get(0).name();
            room.initGame(blackId);

            log.info("[GAME_START] roomId={} title=\"{}\" black=\"{}\" white=\"{}\"",
                    roomId, room.getTitle(), blackName, whiteName);

            broadcast(roomId, RoomResponseMessage.builder()
                    .roomId(roomId)
                    .type(MessageType.GAME_START)
                    .blackPlayer(blackId)
                    .message("게임이 시작되었습니다")
                    .build());
        }
    }

    public void processReady(String roomId, Player sender) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            room.setReady(room.getReady() + 1);

            broadcast(roomId, RoomResponseMessage.builder()
                    .roomId(roomId)
                    .sender(sender.id())
                    .type(MessageType.READY)
                    .message(sender.name())
                    .build());

            if (room.getReady() == 2) {
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() { notifyGameStart(roomId); }
                }, 500);
            }
        }
    }

    public void processCancel(String roomId, Player sender) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            room.setReady(room.getReady() - 1);

            broadcast(roomId, RoomResponseMessage.builder()
                    .roomId(roomId)
                    .sender(sender.id())
                    .type(MessageType.CANCEL)
                    .message(sender.name())
                    .build());
        }
    }

    public void processSurrender(String roomId, Player sender) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            room.setPlaying(false);
            room.setReady(0);
        }

        log.info("[SURRENDER] roomId={} player=\"{}\"", roomId, sender.name());
        broadcast(roomId, RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.GAME_END)
                .message(sender.name() + "님이 기권하셨습니다.")
                .build());
    }

    public void processTimeout(String roomId, Player sender) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            room.setPlaying(false);
        }

        log.info("[TIMEOUT] roomId={} player=\"{}\"", roomId, sender.name());
        broadcast(roomId, RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.GAME_END)
                .message(sender.name() + "님이 시간을 초과하여 게임이 종료되었습니다.")
                .build());
    }

    public void processMove(String roomId, Player sender, int index) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            broadcastError(roomId, "방이 존재하지 않습니다.");
            return;
        }

        synchronized (room) {
            String err = validateMove(room, sender, index);
            if (err != null) {
                broadcastError(roomId, err);
                return;
            }

            gameService.applyMove(room, index);
            int turn = room.getTurn();

            if (gameService.checkGameEnd(room, index)) {
                room.setPlaying(false);
                room.setReady(0);
                log.info("[GAME_WIN] roomId={} winner=\"{}\" turn={}", roomId, sender.name(), turn);
                broadcast(roomId, RoomResponseMessage.builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(sender.name() + "님이 승리하셨습니다")
                        .index(index)
                        .turn(turn)
                        .build());
            } else {
                broadcast(roomId, RoomResponseMessage.builder()
                        .roomId(roomId)
                        .type(MessageType.ACTION)
                        .index(index)
                        .turn(turn)
                        .build());
            }
        }
    }

    /** 착수 가드체인. 유효하면 null, 위반 시 에러 메시지를 반환. */
    private String validateMove(Room room, Player sender, int index) {
        if (index < 0 || index >= 225) return "잘못된 착수 위치입니다.";
        if (!room.isPlaying()) return "게임이 진행중이지 않습니다.";
        boolean isBlackTurn = room.getTurn() % 2 == 1;
        if (isBlackTurn != room.getBlackPlayer().equals(sender.id())) return "현재 당신의 차례가 아닙니다.";
        if (room.getBoard()[index / 15][index % 15] != 0) return "이미 다른 돌이 존재합니다.";
        if (isBlackTurn && gameService.isForbiddenMove(room, index)) return "금수 위치입니다.";
        return null;
    }

    private void broadcastError(String roomId, String msg) {
        broadcast(roomId, RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.ERROR)
                .message(msg)
                .build());
    }
}
