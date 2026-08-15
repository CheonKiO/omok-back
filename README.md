# Omok Backend

실시간 온라인 오목 게임의 백엔드입니다. Spring Boot 3.4 + WebSocket(STOMP/SockJS) 기반이며, Oracle Cloud에 배포되어 운영 중입니다. 회원·인증·기보는 MySQL(HeatWave)에 영속되고, 게임 진행 상태(방·착수)는 인메모리로 관리합니다.

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
| Language | Java 17 |
| Framework | Spring Boot 3.4 (jakarta.*) |
| Realtime | Spring WebSocket · STOMP · SockJS |
| Auth | Spring Security · JWT(JJWT, HS256) · BCrypt |
| Persistence | Spring Data JPA · Hibernate(`ddl-auto=validate`) · Flyway |
| Database | MySQL (OCI HeatWave, Always Free) |
| Build | Gradle → `ROOT.jar` (`bootJar`) |
| Server | Ubuntu · Apache 2.4 · systemd(`omok.service`) |
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
   Apache 2.4              ← SSL 종료, 리버스 프록시, WebSocket(WSS) 터널링
      │
   Spring Boot JAR         ← ROOT.jar, 포트 8080 (systemd omok.service)
      │
      ▼
   MySQL HeatWave          ← 같은 VCN 프라이빗 (10.0.0.191:3306/omok)
```

- 애플리케이션은 임베디드 서버를 품은 실행형 JAR(`ROOT.jar`)이며 `systemd` 서비스(`omok.service`)로 8080 포트에서 구동됩니다.
- DB 접속 정보·JWT 시크릿은 소스에 넣지 않고 systemd `EnvironmentFile=/etc/omok.env`(DB_URL/USERNAME/PASSWORD, JWT_SECRET)로 주입합니다.
- **게임 진행 상태(방·착수)는 인메모리**라 서버 중단 시 소멸하지만, **회원·인증·기보는 DB에 영속**됩니다.

<br>

## 설계 판단 기록

### 1. 게임 규칙을 프론트·백엔드 양쪽에 구현한 이유

렌주룰 금수(3-3, 4-4, 장목) 판정 로직은 프론트엔드와 백엔드에 **각각** 존재합니다. 중복처럼 보이지만 역할이 다릅니다.

| | 프론트엔드 | 백엔드 |
|---|---|---|
| 목적 | 클릭 즉시 피드백 | 신뢰 경계 (Trust Boundary) |
| 신뢰 여부 | 신뢰하지 않음 | 최종 판정 |

클라이언트는 조작될 수 있으므로, 서버는 **턴 순서 · 착수 위치 중복 · 금수 여부**를 전부 재검증한 뒤에만 게임 상태를 반영하고 브로드캐스트합니다. 프론트 판정을 통과했다는 사실은 서버 판단에 아무 영향을 주지 않습니다.

> 판정 로직: [`RenjuRuleEngine.java`](src/main/java/org/scoula/room/service/RenjuRuleEngine.java)

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

판정은 보드에 돌을 임시로 놓고 계산한 뒤 원상 복구하는 방식이라, 판정 자체가 게임 상태에 부작용을 남기지 않습니다. 렌주 판정은 방·브로드캐스트 로직에서 떼어내 **순수 board 로직 `RenjuRuleEngine`으로 분리**했고, 보드 좌표·돌 색·라인 분석 결과는 각각 `Position` · `StoneColor` · `LineAnalysis` 값 객체로 나눠 패턴 정의와 판정 로직이 섞이지 않도록 했습니다.

### 3. 신원은 payload가 아니라 인증 principal을 앵커로 삼는다 (보안)

WebSocket 메시지의 `sender` 같은 **payload 필드는 클라이언트가 마음대로 채울 수 있어** 인가 신원으로 쓰면 자리 탈취·상대 대신 착수 같은 조작이 가능합니다. 그래서 신원의 **신뢰 앵커를 JWT subject로 고정**했습니다.

- STOMP `CONNECT` 시 `StompAuthChannelInterceptor`가 JWT를 검증해 **principal**을 세션에 바인딩합니다.
- 착수·기권·준비·타임아웃 등 게임 액션은 payload의 `sender`가 아니라 **principal + 방 멤버십/턴 소유**로 인가합니다. 미인증 CONNECT면 principal이 없어 서비스가 인가를 거부합니다.
- REST 방 생성/입장/퇴장도 마찬가지로 인증 principal만 신원으로 사용하고, body의 `player.id`나 과거 프론트가 보내던 `?playerId=` 쿼리는 표시용으로만 취급하거나 무시합니다.
- principal은 **회원이면 숫자 `userId`, 게스트면 `guest-<uuid>`** 형태라, 회원/게스트를 문자열로 구분할 수 있습니다.

### 4. 연결이 끊긴 플레이어를 즉시 패배 처리하지 않은 이유

모바일 환경에서는 터널·엘리베이터 등으로 소켓이 순간적으로 끊깁니다. 즉시 퇴장 처리하면 정상 플레이어가 억울하고, 무한정 기다리면 상대가 묶입니다.

그래서 **"연결 종료"를 의도에 따라 다르게 처리**했습니다.

| 상황 | 처리 |
|---|---|
| 게임 중 소켓 끊김 | 30초 유예 → 재접속 시 게임 속행 / 초과 시 자동 퇴장(몰수) |
| 게임 중이 아닐 때 끊김 | 즉시 퇴장 |
| 나가기 버튼 클릭 | 명시적 의사표현이므로 유예 없이 즉시 처리 |

`ScheduledExecutorService`로 유예 타이머를 걸고, 재접속(JOIN) 시 해당 타이머를 (위조 불가한 principal 기준으로) 취소하는 구조입니다.

> 구현: [`WebSocketEventListener.java`](src/main/java/org/scoula/room/service/WebSocketEventListener.java)

### 5. 기보 저장·복기 (kifu)

종료된 대국은 `game` 테이블에 **게임 종료 순간 한 번에** 저장합니다. 승리·기권·시간초과·끊김몰수 **4개 종료 경로가 모두 `GameArchiveService.archive()`** 라는 단일 훅을 통과하도록 만들어, 저장 로직이 흩어지지 않게 했습니다.

- **참가자 중 1명 이상이 회원일 때만 저장**합니다(게스트끼리 둔 판은 미저장). 서버 중단으로 훅에 도달하지 못한 중단 게임은 인메모리라 자연히 소멸합니다.
- `end_reason`은 enum(`WIN_5` · `SURRENDER` · `TIMEOUT` · `DISCONNECT`), `moves`는 착수 index를 CSV로 직렬화합니다.
- 조회는 `GET /api/games`(내 기보 목록, 회원만) · `GET /api/games/{id}`(참가자 본인만, 복기용 moves 포함)이며, 남의 기보나 존재 여부는 노출하지 않습니다(비참가자는 404).
- 저장 실패가 게임 종료 브로드캐스트를 깨지 않도록 예외를 삼키는 **best-effort**로 처리합니다.

### 6. 컨트롤러를 얇게 유지한 이유

초기에는 `RoomSocketController`가 메시지 수신과 게임 로직을 모두 처리했습니다. 메시지 타입이 늘어날수록 컨트롤러가 비대해져, 게임 로직을 전부 `RoomSocketService`로 이동시키고 컨트롤러는 **라우팅만** 담당하도록 분리했습니다. 착수 처리(`processMove` 등)는 방 단위 `synchronized(room)` 락과 move index(0~224) 검증으로 동시성·범위를 방어하고, 브로드캐스트는 `RoomBroadcaster`로 단일화했습니다.

<br>

## 프로젝트 구조

```
org/scoula/
├── OmokApplication.java            # Spring Boot 진입점
├── config/
│   ├── SecurityConfig.java         # Spring Security(STATELESS) · CORS · JWT 필터 체인
│   ├── WebSocketConfig.java        # STOMP 엔드포인트 + inbound 채널 인증 인터셉터
│   └── SchedulingConfig.java
├── auth/                           # 인증 (JWT · 회원가입/로그인/게스트/refresh/logout)
│   ├── AuthController.java · AuthService.java
│   ├── JwtProvider.java · JwtAuthenticationFilter.java
│   ├── StompAuthChannelInterceptor.java   # STOMP CONNECT 시 principal 바인딩
│   ├── RefreshToken.java · RefreshTokenRepository.java    # refresh 토큰 DB 영속
│   └── dto/ · 예외들
├── user/                           # 회원 (User 엔티티 · Role · /api/users/me)
│   ├── UserController.java · User.java · UserRepository.java · Role.java
├── game/                           # 기보 저장/조회 (복기)
│   ├── GameController.java · GameQueryService.java · GameArchiveService.java
│   ├── Game.java · GameRepository.java · EndReason.java · WinnerColor.java
│   └── dto/
└── room/                           # 방 · 실시간 게임 (인메모리)
    ├── controller/
    │   ├── RoomController.java             # REST (방 생성/조회/입장/퇴장)
    │   └── RoomSocketController.java       # WebSocket 라우팅 전담
    ├── service/
    │   ├── RoomService(Impl).java          # 방 상태 관리 (ConcurrentHashMap)
    │   ├── RoomSocketService.java          # 게임 진행 (synchronized(room))
    │   ├── RenjuRuleEngine.java            # 순수 board 규칙 판정 (금수·승리)
    │   ├── RoomBroadcaster.java            # 브로드캐스트 단일화
    │   ├── WebSocketEventListener.java     # 연결 끊김 감지 + 유예 처리
    │   ├── EmptyRoomCleaner.java · RoomCreationRateLimiter.java
    │   └── GameService.java
    ├── domain/  (Room · Player)
    └── dto/     (RoomRequest/ResponseMessage · RoomResponseDto · MessageType)

resources/
├── application.yml
└── db/migration/
    ├── V1__baseline_schema.sql     # users · refresh_tokens
    └── V2__add_game_table.sql      # game (기보)
```

**MessageType**
`JOIN` `LEAVE` `ACTION` `GAME_START` `GAME_END` `READY` `CANCEL` `SURRENDER` `TIMEOUT` `ERROR` `DISCONNECTED` `RECONNECT`

<br>

## API

### 인증 (REST)

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/auth/signup` | 회원가입 |
| POST | `/api/auth/login` | 로그인 (access + refresh 토큰 발급, refresh는 DB 저장) |
| POST | `/api/auth/guest` | 게스트 토큰 발급 (role=GUEST, DB 미저장) |
| POST | `/api/auth/refresh` | refresh 토큰으로 재발급 (DB 검증) |
| POST | `/api/auth/logout` | refresh 토큰 폐기 (DB 삭제) |
| GET | `/api/users/me` | 현재 로그인 사용자 정보 (인증 필요) |

### 기보 (REST · 인증 필요)

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/games` | 내 기보 목록 (회원만, 게스트는 빈 목록) |
| GET | `/api/games/{id}` | 기보 상세·복기 (참가자 본인만, 아니면 404) |

### 방 (REST)

| Method | Endpoint | 설명 |
|---|---|---|
| GET | `/api/rooms` | 방 목록 조회 (공개) |
| GET | `/api/rooms/{roomId}` | 방 상세 조회 (공개) |
| POST | `/api/rooms/create?title=&password=` | 방 생성 (인증 필요, principal당 rate-limit) |
| POST | `/api/rooms/join/{roomId}?password=` | 방 입장 (인증 필요) |
| POST | `/api/rooms/leave/{roomId}` | 방 퇴장 (인증 필요, 신원은 principal) |

### WebSocket

| | |
|---|---|
| Endpoint | `/game` (STOMP over SockJS) |
| 구독 | `/topic/room/{roomId}` · 개인 에러 `/user/queue/errors` |
| 발행 | `/app/room/{roomId}/join` · `/app/ready` · `/app/cancel` · `/app/surrender` · `/app/timeout` · `/app/move` |
| 인증 | CONNECT 시 JWT → principal 바인딩, 이후 액션은 principal + 방 멤버십/턴 소유로 인가 |

<br>

## 트러블슈팅

### CORS 에러로 보였지만 CORS 문제가 아니었던 건

배포 후 브라우저 콘솔에 CORS 에러가 떴습니다. 하지만 원인은 다른 곳에 있었습니다.

```
서버 다운 → Apache가 503 반환 → 503 응답에 CORS 헤더 없음 → 브라우저가 CORS 에러로 표시
```

CORS 설정을 아무리 고쳐도 해결되지 않았고, 로그를 확인하고 나서야 백엔드가 죽어 있다는 걸 알았습니다. **에러 메시지가 가리키는 지점과 실제 원인이 다를 수 있다**는 걸 배운 사례입니다. (이 경험 이후, 배포 파이프라인에 부팅 헬스체크(`curl /api/rooms`)를 넣어 "CI는 green인데 prod는 죽은" 상황을 배포 실패로 잡도록 했습니다.)

관련해서, 인증 실패(401)를 컨트롤러 도달 전 JWT 필터가 반환할 때 CORS 헤더가 빠지면 프론트 인터셉터가 401을 인지하지 못해 토큰 refresh가 트리거되지 않았습니다. 그래서 CORS를 MVC 레벨뿐 아니라 **Security 필터 체인에도 등록**해 401 응답에도 CORS 헤더가 실리도록 했습니다.

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

### 배포 시 프로세스 권한 문제

애플리케이션을 셸 스크립트로 직접 실행하면 프로세스 소유권이 꼬여 이후 배포에서 권한 에러가 발생했습니다. 앱을 **systemd 서비스(`omok.service`)로 등록**하고 sudoers에 재시작 권한만 제한적으로 부여해 해결했습니다. (초기 1차 구조에서는 Tomcat WAR를 systemd로 등록했고, Spring Boot JAR로 이관한 뒤에는 실행형 JAR을 `omok.service`가 직접 구동합니다.)

```bash
# /etc/sudoers.d/omok
ubuntu ALL=(ALL) NOPASSWD: /bin/systemctl restart omok
```

<br>

## 개발 이력

이 프로젝트는 여러 단계에 걸쳐 개발되었습니다.

| 시기 | 방식 | 목표 |
|---|---|---|
| 2025.07 ~ 08 | 직접 구현 (대화형 AI는 보조) | 부트캠프 학습 내용 복습 |
| 2026.05 ~ 06 | Claude Code 기반 재개발 | 구조 개선 · 기능 고도화 |
| 2026 (백엔드 대개편) | Claude Code | Spring Boot 이관 · DB·인증 · 보안 · 기보 |

**금수 판정 버그 수정 (2차 개발 중)** — 1차 때 직접 작성한 금수 판정 로직에서 **버그 4개**를 발견해 수정했습니다.

- 보드 바깥 좌표를 빈 칸(EMPTY)으로 처리해 열린 3/4를 오판정
- 대칭 열린 3 패턴(`○●C●○`)이 이중 카운트되어 3-3 오판정 유발
- 열린 3 패턴 배열의 길이 불일치
- 4 패턴 `C●●_●` / `●_●●C` 누락

직접 구현해본 경험이 있었기에 AI가 생성한 코드에서 어느 부분을 의심해야 하는지 판단할 수 있었습니다.

**2026년 백엔드 대개편** — 초기 구조(Spring MVC 6 WAR + Tomcat 10 + Java 21, 인메모리 전용)에서 다음을 순차적으로 이관·추가했습니다. 모두 prod에 배포되어 운영 중입니다.

- **Spring Boot 3.4 이관**: WAR/Tomcat → 임베디드 실행형 JAR(Java 17, `bootJar`), systemd `omok.service` 구동.
- **DB·인증 도입**: MySQL(HeatWave) 연동, JPA `ddl-auto=validate` + Flyway 마이그레이션, JWT(access/refresh + 게스트) 기반 Spring Security STATELESS 인증, refresh 토큰 DB 영속.
- **보안 신원 바인딩**: STOMP principal을 신뢰 앵커로 삼아 payload 기반 자리 탈취 벡터 차단.
- **기보 저장·복기**: 종료 대국을 `game` 테이블에 원샷 저장(4개 종료 경로 단일 훅), 목록/상세 조회 API.
- **리팩토링**: 렌주 판정을 순수 `RenjuRuleEngine`으로 분리, `synchronized(room)` 락 + move index 검증, 브로드캐스트 단일화, 컨트롤러 라우팅 전담화.

<br>

## 향후 계획

- [ ] ELO 레이팅 기반 랭크 시스템 (종료 훅 `GameArchiveService`에 얹을 예정)
- [ ] Minimax + 알파베타 가지치기 AI 대전
- [ ] 타이머 관리 주체를 백엔드로 이관 (재연결 시 동기화 정확도 개선)
