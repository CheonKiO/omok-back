package org.scoula.game;

/** 대국 종료 사유. */
public enum EndReason {
    WIN_5,      // 5목 완성
    SURRENDER,  // 기권
    TIMEOUT,    // 시간 초과
    DISCONNECT  // 연결 끊김 후 30초 유예 초과(몰수)
}
