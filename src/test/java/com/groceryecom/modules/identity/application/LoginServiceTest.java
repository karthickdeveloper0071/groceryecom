package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.LoginRequest;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.ratelimit.RateLimitProperties;
import com.groceryecom.platform.ratelimit.RateLimitProperties.Policy;
import com.groceryecom.platform.ratelimit.RateLimiter;
import com.groceryecom.shared.exception.TooManyRequestsException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginServiceTest {

    private static final String INVALID = "Invalid username or password";
    private static final RateLimitProperties NO_LIMITS =
            new RateLimitProperties(false, null, null, null, null);
    private static final RateLimiter ALWAYS_ALLOW = (key, policy) -> RateLimiter.Decision.allow();

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenIssuer tokenIssuer;

    @Mock
    private AuditLog auditLog;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}dummy");
        when(tokenIssuer.startSession(any(), any()))
                .thenReturn(AuthTokenResponse.bearer("access", "refresh", 900, null));
        loginService = new LoginService(userRepository, passwordEncoder, tokenIssuer, auditLog,
                ALWAYS_ALLOW, NO_LIMITS);
    }

    @Test
    void anIdentifierWithAtSignIsTreatedAsAnEmail() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);

        assertThat(loginService.execute(new LoginRequest("  Alice@Example.COM ", "password123"), ClientInfo.UNKNOWN)
                .accessToken()).isEqualTo("access");
    }

    @Test
    void unknownAccountStillSpendsAPasswordComparison() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("ghost", "password123"), ClientInfo.UNKNOWN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);

        verify(passwordEncoder).matches("password123", "{bcrypt}dummy");
        verify(tokenIssuer, never()).startSession(any(), any());
    }

    @Test
    void wrongPasswordGivesTheSameMessageAsAnUnknownAccount() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "wrong"), ClientInfo.UNKNOWN))
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

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "wrong"), ClientInfo.UNKNOWN))
                .hasMessage(INVALID);
        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "password123"), ClientInfo.UNKNOWN))
                .hasMessage("User account is disabled");
    }

    @Test
    void softDeletedAccountCannotLogIn() {
        User user = activeUser();
        user.setIsDeleted(true);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "{bcrypt}stored")).thenReturn(true);

        assertThatThrownBy(() -> loginService.execute(new LoginRequest("alice", "password123"), ClientInfo.UNKNOWN))
                .hasMessage("User account is disabled");
    }

    /** The per-account limit stops a distributed attack on one account. */
    @Test
    void tooManyAttemptsOnOneAccountAreThrottledBeforeTheDatabaseIsTouched() {
        RateLimitProperties limits = new RateLimitProperties(true,
                new Policy(20, Duration.ofMinutes(1)),
                new Policy(1, Duration.ofMinutes(5)),
                new Policy(5, Duration.ofMinutes(10)),
                new Policy(60, Duration.ofMinutes(1)));
        RateLimiter alwaysBlock = (key, policy) -> RateLimiter.Decision.block(42);
        LoginService throttled = new LoginService(userRepository, passwordEncoder, tokenIssuer, auditLog,
                alwaysBlock, limits);

        assertThatThrownBy(() -> throttled.execute(new LoginRequest("alice", "password123"), ClientInfo.UNKNOWN))
                .isInstanceOf(TooManyRequestsException.class)
                .hasFieldOrPropertyWithValue("errorCode", "RATE_LIMITED")
                .hasFieldOrPropertyWithValue("retryAfterSeconds", 42L);

        verify(userRepository, never()).findByUsername(any());
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
