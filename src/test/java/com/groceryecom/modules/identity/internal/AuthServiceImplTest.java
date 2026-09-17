package com.groceryecom.modules.identity.internal;

import com.groceryecom.modules.identity.api.Role;
import com.groceryecom.modules.identity.api.UserRegisteredEvent;
import com.groceryecom.modules.identity.web.dto.UserRegistrationDTO;
import com.groceryecom.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void passwordResetForUnknownEmailDoesNotRevealThatEmailIsMissing() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatCode(() -> authService.requestPasswordReset("Nobody@Example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void registrationCreatesLowerCaseCustomerAndPublishesEvent() {
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        UserRegistrationDTO dto = new UserRegistrationDTO("  Alice ", "Alice@Example.COM", "password123", "Alice", null, null);
        authService.register(dto);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("alice");
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);

        verify(events).publishEvent(new UserRegisteredEvent(saved.getValue().getPublicId(), "alice@example.com", Role.CUSTOMER));
    }
}
