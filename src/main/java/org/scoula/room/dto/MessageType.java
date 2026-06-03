package org.scoula.room.dto;

public enum MessageType {
    JOIN,
    LEAVE,
    ACTION,
    GAME_START,
    TIMEOUT,
    GAME_END,
    ERROR,
    READY,
    CANCEL,
    SURRENDER,
    DISCONNECTED,
    RECONNECT
}