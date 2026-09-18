package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangePasswordServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLog auditLog;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private ChangePasswordService changePasswordService;

    @Test
    void endsEverySessionSoALeakedTokenStopsWorking() {
        User user = user();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPassword1", "{bcrypt}old")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("{bcrypt}new");

        changePasswordService.execute(USER_ID, request("oldPassword1", "newPassword1"));

        verify(sessionService).revokeAllSessions(eq(USER_ID), any());
    }

    @Test
    void storesTheNewHash() {
        User user = user();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPassword1", "{bcrypt}old")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1")).thenReturn("{bcrypt}new");

        changePasswordService.execute(USER_ID, request("oldPassword1", "newPassword1"));

        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}new");
    }

    @Test
    void wrongCurrentPasswordIsRejected() {
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("wrong", "{bcrypt}old")).thenReturn(false);

        assertThatThrownBy(() -> changePasswordService.execute(USER_ID, request("wrong", "newPassword1")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "INCORRECT_PASSWORD");
    }

    @Test
    void reusingTheCurrentPasswordIsRejected() {
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("samePassword1", "{bcrypt}old")).thenReturn(true);

        assertThatThrownBy(() -> changePasswordService.execute(USER_ID, request("samePassword1", "samePassword1")))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "PASSWORD_UNCHANGED");
    }

    @Test
    void missingUserIsNotFound() {
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> changePasswordService.execute(USER_ID, request("oldPassword1", "newPassword1")))
                .isInstanceOf(NotFoundException.class);
    }

    private static ChangePasswordRequest request(String oldPassword, String newPassword) {
        return new ChangePasswordRequest(oldPassword, newPassword, newPassword);
    }

    private static User user() {
        User user = new User();
        user.setPublicId(USER_ID);
        user.setPasswordHash("{bcrypt}old");
        return user;
    }
}
