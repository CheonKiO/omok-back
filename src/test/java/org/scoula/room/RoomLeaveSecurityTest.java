package org.scoula.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP leave가 ?playerId= 쿼리가 아니라 인증 principal 본인 자리만 제거하는지 검증한다 (Task 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomLeaveSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String guestToken(String nickname) throws Exception {
        String body = mvc.perform(post("/api/auth/guest").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String createRoom(String token, String title) throws Exception {
        return mvc.perform(post("/api/rooms/create")
                        .header("Authorization", "Bearer " + token)
                        .param("title", title))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void join(String token, String roomId, String playerId, String playerName) throws Exception {
        mvc.perform(post("/api/rooms/join/" + roomId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"" + playerId + "\",\"name\":\"" + playerName + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void leaveRemovesOnlyTheAuthenticatedPrincipalsOwnSeat() throws Exception {
        String tokenA = guestToken("A1");
        String tokenB = guestToken("B1");
        String roomId = createRoom(tokenA, "room-leave-1");
        join(tokenA, roomId, "pA", "플레이어A");
        join(tokenB, roomId, "pB", "플레이어B");

        // A는 본인 자리만 떠난다. playerId 쿼리 없이 인증만으로 인가.
        mvc.perform(post("/api/rooms/leave/" + roomId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        mvc.perform(get("/api/rooms/" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(1))
                .andExpect(jsonPath("$.players[0].id").value("pB"));
    }

    @Test
    void nonMemberCannotEvictAnotherPlayersSeatViaPlayerIdQuery() throws Exception {
        String tokenA = guestToken("A2");
        String tokenB = guestToken("B2");
        String tokenAttacker = guestToken("Attacker");
        String roomId = createRoom(tokenA, "room-leave-2");
        join(tokenA, roomId, "pA2", "플레이어A2");
        join(tokenB, roomId, "pB2", "플레이어B2");

        // 공격자는 이 방의 멤버가 아니다. 피해자의 표시용 playerId를 쿼리에 실어도
        // 인가는 principal 기준이므로 아무 자리도 제거되지 않는다.
        mvc.perform(post("/api/rooms/leave/" + roomId)
                        .header("Authorization", "Bearer " + tokenAttacker)
                        .param("playerId", "pB2"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/rooms/" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players.length()").value(2));
    }

    @Test
    void leaveRequiresAuthentication() throws Exception {
        String tokenA = guestToken("A3");
        String roomId = createRoom(tokenA, "room-leave-3");
        join(tokenA, roomId, "pA3", "플레이어A3");

        mvc.perform(post("/api/rooms/leave/" + roomId))
                .andExpect(status().isUnauthorized());
    }
}
