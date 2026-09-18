package com.groceryecom.modules.identity.internal;

import com.groceryecom.modules.identity.api.Role;
import com.groceryecom.modules.identity.api.UserRegisteredEvent;
import com.groceryecom.modules.identity.web.dto.LoginRequest;
import com.groceryecom.modules.identity.web.dto.RegisterRequest;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.ConflictException;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private ApplicationEventPublisher events;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        lenient().when(tokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider, events);
    }

    @Test
    void registrationCreatesLowerCaseCustomerAndPublishesEvent() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        authService.register(new RegisterRequest("  Alice ", "Alice@Example.COM", "password123", "Alice", null, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);

        verify(events).publishEvent(new UserRegisteredEvent(saved.getValue().getPublicId(), "alice@example.com", Role.CUSTOMER));
    }

    @Test
    void duplicateUsernameIsAConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("ALICE", "a@example.com", "password123", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "USERNAME_EXISTS");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unknownUserStillSpendsAPasswordCheck() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid username or password");
        verify(passwordEncoder).matches(eq("password123"), eq("hash"));
    }

    @Test
    void disabledAccountIsOnlyRevealedAfterCorrectPassword() {
        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setPasswordHash("stored");
        user.setIsActive(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored")).thenReturn(false);
        when(passwordEncoder.matches("right", "stored")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong")))
                .hasMessage("Invalid username or password");
        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "right")))
                .hasMessage("User account is disabled");
    }
}
