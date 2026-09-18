package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Changes the password of the logged-in user.
 *
 * <p>Business rules:
 * <ul>
 *   <li>The current password must be given and must match, so a stolen access token
 *       alone cannot lock the owner out.</li>
 *   <li>The new password must differ from the current one.</li>
 *   <li>Acts only on the caller's own account; the user id comes from the token, never
 *       from the request.</li>
 * </ul>
 *
 * <p>All sessions end afterwards, so a leaked token stops working immediately.
 */
@Slf4j
@Service
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditLog auditLog;

    ChangePasswordService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          SessionService sessionService, AuditLog auditLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditLog = auditLog;
    }

    @Transactional
    public void execute(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ValidationException("Old password is incorrect", "INCORRECT_PASSWORD");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new ValidationException("New password must be different from old password", "PASSWORD_UNCHANGED");
        }

        // Written at commit by dirty checking; no explicit save needed
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        auditLog.passwordChanged(userId);

        // Someone changing a password often does it because they fear the old one leaked,
        // so every existing session ends, including this one: the client must log in again
        sessionService.revokeAllSessions(userId, "password changed");
    }
}
