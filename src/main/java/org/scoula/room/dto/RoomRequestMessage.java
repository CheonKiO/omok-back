package org.scoula.room.dto;

import org.scoula.room.domain.Player;

public record RoomRequestMessage(Player sender, String roomId, MessageType type, Integer index) {}
