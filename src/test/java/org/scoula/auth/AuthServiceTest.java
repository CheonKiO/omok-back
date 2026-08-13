package org.scoula.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scoula.user.Role;
import org.scoula.user.User;
import org.scoula.user.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final AuthService authService = new AuthService(userRepository, passwordEncoder);

    @Test
    void signupHashesPasswordAndSavesUserWithUserRole() {
        when(userRepository.existsByUsername("kio")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.signup("kio", "raw-password", "키오");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertEquals("kio", saved.getUsername());
        assertEquals("키오", saved.getNickname());
        assertEquals(Role.USER, saved.getRole());
        assertNotEquals("raw-password", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("raw-password", saved.getPasswordHash()),
                "저장된 해시는 원문 비밀번호와 매칭돼야 한다");
    }

    @Test
    void signupRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("kio")).thenReturn(true);

        assertThrows(DuplicateUsernameException.class,
                () -> authService.signup("kio", "pw", "닉"));
        verify(userRepository, never()).save(any());
    }
}
