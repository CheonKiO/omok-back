package org.scoula.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void savesAndFindsByToken() {
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(1L).token("refresh-abc").expiresAt(LocalDateTime.now().plusDays(14)).build());

        assertTrue(refreshTokenRepository.findByToken("refresh-abc").isPresent());
        assertFalse(refreshTokenRepository.findByToken("nope").isPresent());
    }

    @Test
    void deleteByTokenRemovesRow() {
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(1L).token("refresh-xyz").expiresAt(LocalDateTime.now().plusDays(14)).build());

        refreshTokenRepository.deleteByToken("refresh-xyz");

        assertFalse(refreshTokenRepository.findByToken("refresh-xyz").isPresent());
    }
}
