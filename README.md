# Omok Backend

실시간 온라인 오목 게임의 백엔드입니다. Spring MVC 6 + WebSocket(STOMP/SockJS) 기반이며, Oracle Cloud에 배포되어 운영 중입니다.

**▶ [Live Demo](https://cheonkio.github.io/)** · [Frontend Repository](https://github.com/CheonKiO/omok-front)

<br>

## 목차
- [기술 스택](#기술-스택)
- [시스템 구성](#시스템-구성)
- [설계 판단 기록](#설계-판단-기록)
- [프로젝트 구조](#프로젝트-구조)
- [API](#api)
- [트러블슈팅](#트러블슈팅)
- [개발 이력](#개발-이력)

<br>

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring MVC 6 (jakarta.*) |
| Realtime | Spring WebSocket · STOMP · SockJS |
| Build | Gradle → `ROOT.war` |
| Server | Ubuntu · Apache 2.4 · Tomcat 10 (systemd) |
| Infra | Oracle Cloud VM · Let's Encrypt |
| CI/CD | GitHub Actions |

<br>

## 시스템 구성

```
브라우저 (GitHub Pages)
      │  HTTPS / WSS
      ▼
Oracle Cloud VM (Ubuntu)
      │
   Apache 2.4        ← SSL 종료, 리버스 프록시, WebSocket 터널링
      │
   Tomcat 10         ← ROOT.war (systemd 서비스)
```

<br>

## 설계 판단 기록

### 1. 게임 규칙을 프론트·백엔드 양쪽에 구현한 이유

렌주룰 금수(3-3, 4-4, 장목) 판정 로직은 프론트엔드와 백엔드에 **각각** 존재합니다. 중복처럼 보이지만 역할이 다릅니다.

| | 프론트엔드 | 백엔드 |
|---|---|---|
| 목적 | 클릭 즉시 피드백 | 신뢰 경계 (Trust Boundary) |
| 신뢰 여부 | 신뢰하지 않음 | 최종 판정 |

클라이언트는 조작될 수 있으므로, 서버는 **턴 순서 · 착수 위치 중복 · 금수 여부**를 전부 재검증한 뒤에만 게임 상태를 반영하고 브로드캐스트합니다. 프론트 판정을 통과했다는 사실은 서버 판단에 아무 영향을 주지 않습니다.

> 판정 로직: [`GameService.java`](src/main/java/org/scoula/room/service/GameService.java)

### 2. 렌주룰의 비대칭성 처리

렌주룰은 흑과 백의 규칙이 다릅니다. 승리 판정을 하나의 함수로 처리할 수 없었습니다.

| | 흑 | 백 |
|---|---|---|
| 승리 조건 | **정확히 5목** | 5목 **이상** |
| 6목(장목) | 금수 | 승리 |
| 3-3 / 4-4 | 금수 | 허용 |

또 하나 까다로웠던 건 **금수보다 승리가 우선**한다는 점입니다. 3-3을 만드는 수라도 그 수로 동시에 5목이 완성되면 승리로 처리해야 합니다.

```java
return (hasOverline || openThrees >= 2 || fours >= 2) && !hasFive;
```

판정은 보드에 돌을 임시로 놓고 계산한 뒤 `finally`에서 원상 복구하는 방식이라, 판정 자체가 게임 상태에 부작용을 남기지 않습니다.

보드 좌표·패턴·라인 분석 결과는 각각 `Position` · `Pattern` · `LineAnalysis` 값 객체로 분리해, 패턴 정의와 판정 로직이 섞이지 않도록 했습니다.

### 3. 연결이 끊긴 플레이어를 즉시 패배 처리하지 않은 이유

모바일 환경에서는 터널·엘리베이터 등으로 소켓이 순간적으로 끊깁니다. 즉시 퇴장 처리하면 정상 플레이어가 억울하고, 무한정 기다리면 상대가 묶입니다.

그래서 **"연결 종료"를 의도에 따라 다르게 처리**했습니다.

| 상황 | 처리 |
|---|---|
| 게임 중 소켓 끊김 | 30초 유예 → 재접속 시 게임 속행 / 초과 시 자동 퇴장 |
| 게임 중이 아닐 때 끊김 | 즉시 퇴장 |
| 나가기 버튼 클릭 | 명시적 의사표현이므로 유예 없이 즉시 처리 |

`ScheduledExecutorService`로 유예 타이머를 걸고, 재접속(JOIN) 시 해당 타이머를 취소하는 구조입니다.

> 구현: [`WebSocketEventListener.java#L68-L93`](src/main/java/org/scoula/room/service/WebSocketEventListener.java#L68-L93)

### 4. 컨트롤러를 얇게 유지한 이유

초기에는 `RoomSocketController`가 메시지 수신과 게임 로직을 모두 처리했습니다. 메시지 타입이 늘어날수록 컨트롤러가 비대해져, 게임 로직을 전부 `RoomSocketService`로 이동시키고 컨트롤러는 **라우팅만** 담당하도록 분리했습니다.

<br>

## 프로젝트 구조

```
org/scoula/room/
├── controller/
│   ├── RoomController.java        # REST API (방 생성/조회/입장/퇴장)
│   └── RoomSocketController.java  # WebSocket 라우팅 전담
├── service/
│   ├── RoomService(Impl).java     # 방 상태 관리 (ConcurrentHashMap)
│   ├── RoomSocketService.java     # 게임 진행 + 브로드캐스트
│   ├── GameService.java           # 규칙 판정 (금수·승리·착수)
│   └── WebSocketEventListener.java# 연결 끊김 감지 + 유예 처리
└── dto/
    ├── Room · Player · RoomResponseDto
    ├── RoomRequestMessage · RoomResponseMessage
    └── MessageType.java
```

**MessageType**
`JOIN` `LEAVE` `ACTION` `GAME_START` `GAME_END` `READY` `CANCEL` `SURRENDER` `TIMEOUT` `ERROR` `DISCONNECTED` `RECONNECT`

<br>

## API

### REST

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 조회 |
| GET | `/api/rooms/{roomId}` | 방 상세 조회 |
| POST | `/api/rooms/create?title=` | 방 생성 |
| POST | `/api/rooms/join/{roomId}` | 방 입장 |
| POST | `/api/rooms/leave/{roomId}?playerId=` | 방 퇴장 (즉시 처리) |

### WebSocket

| | |
|---|---|
| Endpoint | `/game` (STOMP over SockJS) |
| 구독 | `/topic/room/{roomId}` |

<br>

## 트러블슈팅

### CORS 에러로 보였지만 CORS 문제가 아니었던 건

배포 후 브라우저 콘솔에 CORS 에러가 떴습니다. 하지만 원인은 다른 곳에 있었습니다.

```
Tomcat 다운 → Apache가 503 반환 → 503 응답에 CORS 헤더 없음 → 브라우저가 CORS 에러로 표시
```

CORS 설정을 아무리 고쳐도 해결되지 않았고, `catalina.out`을 확인하고 나서야 Tomcat이 죽어 있다는 걸 알았습니다. **에러 메시지가 가리키는 지점과 실제 원인이 다를 수 있다**는 걸 배운 사례입니다.

### WebSocket이 XHR 폴링으로 폴백되던 문제

게임은 동작했지만 응답이 느렸습니다. SockJS가 WebSocket 연결에 실패해 XHR 폴링으로 폴백하고 있었기 때문입니다.

원인은 Apache `ProxyPass` **순서**였습니다. 포괄 경로(`/`)가 먼저 매칭되어 `/game/`이 HTTP로 처리되고 있었습니다.

```apache
# 구체적인 경로를 먼저 선언해야 함
RewriteEngine On
RewriteCond %{HTTP:Upgrade} websocket [NC]
RewriteRule ^/game/(.*) ws://localhost:8080/game/$1 [P,L]

ProxyPass        /game/ http://localhost:8080/game/
ProxyPassReverse /game/ http://localhost:8080/game/
ProxyPass        /      http://localhost:8080/
ProxyPassReverse /      http://localhost:8080/
```

### 배포 시 Tomcat 권한 문제

`startup.sh`를 직접 실행하면 프로세스 소유권이 꼬여 이후 배포에서 권한 에러가 발생했습니다. Tomcat을 **systemd 서비스로 등록**하고 sudoers에 재시작 권한만 제한적으로 부여해 해결했습니다.

```bash
# /etc/sudoers.d/tomcat
ubuntu ALL=(ALL) NOPASSWD: /bin/systemctl restart tomcat
```

<br>

## 개발 이력

이 프로젝트는 **두 번에 걸쳐** 개발되었습니다.

| 시기 | 방식 | 목표 |
|---|---|---|
| 2025.07 ~ 08 | 직접 구현 (대화형 AI는 보조) | 부트캠프 학습 내용 복습 |
| 2026.05 ~ 06 | Claude Code 기반 재개발 | 구조 개선 · 기능 고도화 |

2차 개발 중 1차 때 직접 작성한 금수 판정 로직에서 **버그 4개**를 발견해 수정했습니다.

- 보드 바깥 좌표를 빈 칸(EMPTY)으로 처리해 열린 3/4를 오판정
- 대칭 열린 3 패턴(`○●C●○`)이 이중 카운트되어 3-3 오판정 유발
- 열린 3 패턴 배열의 길이 불일치
- 4 패턴 `C●●_●` / `●_●●C` 누락

직접 구현해본 경험이 있었기에 AI가 생성한 코드에서 어느 부분을 의심해야 하는지 판단할 수 있었습니다.

<br>

## 향후 계획

- [ ] 인메모리 저장소 → DB 연동 (현재 서버 재시작 시 게임 상태 소멸)
- [ ] 대전 기록 · 기보 저장 및 복기 기능
- [ ] ELO 레이팅 기반 랭크 시스템
- [ ] Minimax + 알파베타 가지치기 AI 대전
- [ ] 타이머 관리 주체를 백엔드로 이관 (재연결 시 동기화 정확도 개선)
