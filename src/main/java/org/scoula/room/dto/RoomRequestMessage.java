package org.scoula.room.dto;

public record RoomRequestMessage(Player sender, String roomId, MessageType type, Integer index) {}
