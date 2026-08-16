package org.scoula.room.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scoula.room.dto.MessageType;
import org.scoula.room.domain.Player;
import org.scoula.room.domain.Room;
import org.scoula.room.dto.RoomResponseMessage;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomSocketService {

    private final RoomBroadcaster roomBroadcaster;
    private final RoomService roomService;
    private final GameService gameService;
    private final org.scoula.game.GameArchiveService gameArchiveService;
    // 게임 시작 지연을 게임마다 새 non-daemon Timer 스레드 대신 공용 스케줄러로 처리(스레드 누수 제거).
    private final TaskScheduler taskScheduler;

    private void broadcast(String roomId, RoomResponseMessage message) {
        roomBroadcaster.broadcast(roomId, message);
    }

    /** 표시용: playerId에 대응하는 이름을 players에서 찾는다(로그용). 없으면 id 반환. */
    private String nameOf(List<Player> players, String playerId) {
        for (Player p : players) {
            if (p.id().equals(playerId)) return p.name();
        }
        return playerId;
    }

    /** 표시용: principal의 자리 라벨(이름)을 room에서 유도한다. 미바인딩이면 principal 반환. */
    private String seatName(Room room, String principal) {
        String id = room.playerIdOf(principal);
        List<Player> players = room.getPlayers();
        if (id != null && players != null) return nameOf(players, id);
        return id != null ? id : principal;
    }

    public void notifyGameStart(String roomId) {
        Room room = roomService.getRoom(roomId);
        synchronized (room) {
            List<Player> players = room.getPlayers();
            if (players.size() != 2) return;
            // 자리 = 멤버 principal. 흑/백은 서로 다른 두 멤버 principal에서 직접 배정한다
            // (player.id 역조회를 쓰지 않아 자리 오염/모호성 없음, 흑≠백 보장).
            List<String> memberPrincipals = room.memberPrincipals();
            if (memberPrincipals.size() != 2) return;

            boolean firstIsBlack = Math.random() > 0.5;
            String blackPrincipal = firstIsBlack ? memberPrincipals.get(0) : memberPrincipals.get(1);
            String whitePrincipal = firstIsBlack ? memberPrincipals.get(1) : memberPrincipals.get(0);
            // 표시용 blackPlayer(player.id)는 선택된 흑 principal의 라벨로 세팅(프론트 호환).
            String blackId = room.playerIdOf(blackPrincipal);
            String blackName = nameOf(players, blackId);
            String whiteName = nameOf(players, room.playerIdOf(whitePrincipal));
            room.initGame(blackId);
            room.setBlackPrincipal(blackPrincipal);
            room.setWhitePrincipal(whitePrincipal);

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

    public void processReady(String roomId, String principal) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            if (!room.isMember(principal)) return;
            room.setReady(room.getReady() + 1);

            broadcast(roomId, RoomResponseMessage.builder()
                    .roomId(roomId)
                    .sender(room.playerIdOf(principal))
                    .type(MessageType.READY)
                    .message(seatName(room, principal))
                    .build());

            if (room.getReady() == 2) {
                taskScheduler.schedule(() -> notifyGameStart(roomId), Instant.now().plusMillis(500));
            }
        }
    }

    public void processCancel(String roomId, String principal) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        synchronized (room) {
            if (!room.isMember(principal)) return;
            room.setReady(room.getReady() - 1);

            broadcast(roomId, RoomResponseMessage.builder()
                    .roomId(roomId)
                    .sender(room.playerIdOf(principal))
                    .type(MessageType.CANCEL)
                    .message(seatName(room, principal))
                    .build());
        }
    }

    public void processSurrender(String roomId, String principal) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        String name;
        org.scoula.game.WinnerColor winner;
        synchronized (room) {
            if (!room.isMember(principal)) return;
            if (!room.isPlaying()) return; // 이미 종료된 게임 → 중복 처리/저장 방지
            name = seatName(room, principal);
            room.setPlaying(false);
            room.setReady(0);
            // 기권자(principal)가 패 → 상대 승. 기보 저장.
            winner = principal.equals(room.blackPrincipal())
                    ? org.scoula.game.WinnerColor.WHITE : org.scoula.game.WinnerColor.BLACK;
            gameArchiveService.archive(room, winner, org.scoula.game.EndReason.SURRENDER);
        }

        log.info("[SURRENDER] roomId={} player=\"{}\"", roomId, name);
        broadcast(roomId, RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.GAME_END)
                .message(name + "님이 기권하셨습니다.")
                .winner(winner.name())
                .build());
    }

    public void processTimeout(String roomId, String principal) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;
        String name;
        org.scoula.game.WinnerColor winner;
        synchronized (room) {
            if (!room.isMember(principal)) return;
            if (!room.isPlaying()) return; // 이미 종료된 게임 → 중복 처리/저장 방지
            name = seatName(room, principal);
            room.setPlaying(false);
            // 시간초과자(principal)가 패 → 상대 승. 기보 저장.
            winner = principal.equals(room.blackPrincipal())
                    ? org.scoula.game.WinnerColor.WHITE : org.scoula.game.WinnerColor.BLACK;
            gameArchiveService.archive(room, winner, org.scoula.game.EndReason.TIMEOUT);
        }

        log.info("[TIMEOUT] roomId={} player=\"{}\"", roomId, name);
        broadcast(roomId, RoomResponseMessage.builder()
                .roomId(roomId)
                .type(MessageType.GAME_END)
                .message(name + "님이 시간을 초과하여 게임이 종료되었습니다.")
                .winner(winner.name())
                .build());
    }

    public void processMove(String roomId, String principal, int index) {
        Room room = roomService.getRoom(roomId);
        if (room == null) {
            broadcastError(roomId, "방이 존재하지 않습니다.");
            return;
        }

        synchronized (room) {
            String err = validateMove(room, principal, index);
            if (err != null) {
                broadcastError(roomId, err);
                return;
            }

            gameService.applyMove(room, index);
            int turn = room.getTurn();
            String name = seatName(room, principal);

            if (gameService.checkGameEnd(room, index)) {
                room.setPlaying(false);
                room.setReady(0);
                // 착수자(principal)가 승. 기보 저장.
                org.scoula.game.WinnerColor winner = principal.equals(room.blackPrincipal())
                        ? org.scoula.game.WinnerColor.BLACK : org.scoula.game.WinnerColor.WHITE;
                gameArchiveService.archive(room, winner, org.scoula.game.EndReason.WIN_5);
                log.info("[GAME_WIN] roomId={} winner=\"{}\" turn={}", roomId, name, turn);
                broadcast(roomId, RoomResponseMessage.builder()
                        .roomId(roomId)
                        .type(MessageType.GAME_END)
                        .message(name + "님이 승리하셨습니다")
                        .index(index)
                        .turn(turn)
                        .winner(winner.name())
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

    /**
     * 착수 가드체인. 유효하면 null, 위반 시 에러 메시지를 반환.
     * 신원(A''): 착수 권한은 payload sender가 아니라 세션 principal의 멤버십·자리(턴 소유)로만 판정한다.
     */
    private String validateMove(Room room, String principal, int index) {
        if (index < 0 || index >= 225) return "잘못된 착수 위치입니다.";
        if (!room.isPlaying()) return "게임이 진행중이지 않습니다.";
        if (!room.isMember(principal)) return "권한이 없습니다.";
        if (!room.isTurnOwner(principal, room.getTurn())) return "현재 당신의 차례가 아닙니다.";
        boolean isBlackTurn = room.getTurn() % 2 == 1;
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
