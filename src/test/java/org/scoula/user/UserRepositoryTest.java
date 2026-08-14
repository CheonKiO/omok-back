package org.scoula.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByUsername() {
        User saved = userRepository.save(User.builder()
                .username("kio")
                .passwordHash("hashed")
                .nickname("키오")
                .role(Role.USER)
                .build());

        assertNotNull(saved.getId());

        Optional<User> found = userRepository.findByUsername("kio");
        assertTrue(found.isPresent());
        assertEquals("키오", found.get().getNickname());
    }

    @Test
    void existsByUsernameReflectsPresence() {
        userRepository.save(User.builder()
                .username("kio").passwordHash("h").nickname("n").role(Role.USER).build());

        assertTrue(userRepository.existsByUsername("kio"));
        assertFalse(userRepository.existsByUsername("nobody"));
    }
}
