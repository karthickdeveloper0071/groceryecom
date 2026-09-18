package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.RegisterRequest;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.contract.UserRegisteredEvent;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegisterCustomerServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private AuditLog auditLog;

    private RegisterCustomerService service() {
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}hash");
        when(tokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        return new RegisterCustomerService(userRepository, passwordEncoder, new AuthTokenIssuer(tokenProvider), events, auditLog);
    }

    @Test
    void storesLowerCaseCustomerAndPublishesEvent() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        service().execute(new RegisterRequest("  Alice ", "Alice@Example.COM", "password123", "Alice", null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");

        verify(events).publishEvent(
                new UserRegisteredEvent(saved.getValue().getPublicId(), "alice@example.com", Role.CUSTOMER));
    }

    @Test
    void returnsTokensAndTheCreatedProfile() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });
        when(tokenProvider.createAccessToken(any(), any(), any())).thenReturn("access");
        when(tokenProvider.createRefreshToken(any())).thenReturn("refresh");

        var response = service().execute(new RegisterRequest("alice", "alice@example.com", "password123", null, null, null));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.user().role()).isEqualTo("CUSTOMER");
    }

    @Test
    void duplicateUsernameIsAConflictAndNothingIsSaved() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> service()
                .execute(new RegisterRequest("ALICE", "a@example.com", "password123", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USERNAME_EXISTS")
                .hasFieldOrPropertyWithValue("statusCode", 409);

        verify(userRepository, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    /** Two simultaneous registrations: both pass the pre-check, the database stops the second. */
    @Test
    void losingAUniqueConstraintRaceIsAConflictNotAServerError() {
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates uk_users_username"));

        assertThatThrownBy(() -> service()
                .execute(new RegisterRequest("alice", "alice@example.com", "password123", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ACCOUNT_EXISTS")
                .hasFieldOrPropertyWithValue("statusCode", 409);

        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void duplicateEmailIsAConflict() {
        when(userRepository.existsByEmail("a@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service()
                .execute(new RegisterRequest("alice", "A@Example.com", "password123", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "EMAIL_EXISTS");
    }
}
