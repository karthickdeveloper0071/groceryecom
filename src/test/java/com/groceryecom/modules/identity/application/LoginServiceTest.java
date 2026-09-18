package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.LoginRequest;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginServiceTest {

    private static final String INVALID = "Invalid username or password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuditLog auditLog;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        when(tokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        loginService = new LoginService(userRepository, passwordEncoder, new AuthTokenIssuer(tokenProvider), auditLog);
    }

    @Test
    void anIdentifierWithAtSignIsTreatedAsAnEmail() {
        User user = activeUser();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);

        assertThat(loginService.execute(new LoginRequest("  Alice@Example.COM ", "password123")).user().username())
                .isEqualTo("alice");
    }

    @Test
    void unknownAccountStillSpendsAPasswordComparison() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("ghost", "password123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);

        verify(passwordEncoder).matches("password123", "{bcrypt}dummy");
    }

    @Test
    void wrongPasswordGivesTheSameMessageAsAnUnknownAccount() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "wrong")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);
    }

    @Test
    void disabledAccountIsOnlyRevealedAfterTheCorrectPassword() {
        User user = activeUser();
        user.setIsActive(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "{bcrypt}stored")).thenReturn(false);
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "wrong")))
                .hasMessage(INVALID);
        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "password123")))
                .hasMessage("User account is disabled");
    }

    @Test
    void softDeletedAccountCannotLogIn() {
        User user = activeUser();
        user.setIsDeleted(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "password123")))
                .hasMessage("User account is disabled");
    }

    private static User activeUser() {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("{bcrypt}stored");
        user.setRole(Role.CUSTOMER);
        user.setIsActive(true);
        user.setIsDeleted(false);
        return user;
    }
}
