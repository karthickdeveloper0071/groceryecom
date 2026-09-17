package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.infrastructure.security.JwtTokenProvider;
import com.GroceryEcom.GorceryEcom.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void passwordResetForUnknownEmailDoesNotRevealThatEmailIsMissing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.requestPasswordReset("nobody@example.com"))
                .doesNotThrowAnyException();
    }
}
