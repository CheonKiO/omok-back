package org.scoula.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getRoomsIsPublic_forHealthcheck() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().isOk()); // 헬스체크 필수
    }

    @Test
    void createRoomRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/rooms/create").param("title", "x"))
                .andExpect(status().isUnauthorized());
    }
}
