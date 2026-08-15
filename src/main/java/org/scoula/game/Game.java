package org.scoula.game;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 종료된 대국 1건의 기보. 게임 종료 순간 원샷으로 저장된다(중단된 대국은 저장되지 않음).
 * 회원 자리는 user_id(비회원=게스트면 null), 이름은 표시용으로 저장한다.
 * moves는 착수 index를 놓인 순서대로 CSV로 직렬화(예 "112,113,96").
 */
@Entity
@Table(name = "game")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "black_user_id")
    private Long blackUserId; // 게스트면 null

    @Column(name = "white_user_id")
    private Long whiteUserId; // 게스트면 null

    @Column(nullable = false, length = 50)
    private String blackName;

    @Column(nullable = false, length = 50)
    private String whiteName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private WinnerColor winner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EndReason endReason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String moves; // index CSV

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
