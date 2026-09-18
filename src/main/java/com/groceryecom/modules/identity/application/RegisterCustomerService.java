package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.RegisterRequest;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.contract.UserRegisteredEvent;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.shared.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a customer account and logs the new user straight in.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Self-registration always creates a {@link Role#CUSTOMER}. Vendor and admin
 *       accounts come from vendor onboarding and the admin console, so a request
 *       cannot ask for a role.</li>
 *   <li>Username and email are unique, compared in lower case.</li>
 *   <li>Other modules learn about the account through {@link UserRegisteredEvent},
 *       written to the outbox in this same transaction.</li>
 * </ul>
 */
@Slf4j
@Service
public class RegisterCustomerService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    RegisterCustomerService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            AuthTokenIssuer tokenIssuer, ApplicationEventPublisher events, AuditLog auditLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.events = events;
        this.auditLog = auditLog;
    }

    @Transactional
    public AuthTokenResponse execute(RegisterRequest request) {
        String username = UserNormalizer.normalize(request.username());
        String email = UserNormalizer.normalize(request.email());

        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists", "USERNAME_EXISTS");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists", "EMAIL_EXISTS");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(Role.CUSTOMER);

        User saved = save(user);
        auditLog.registered(saved.getPublicId(), saved.getRole().name());

        events.publishEvent(new UserRegisteredEvent(saved.getPublicId(), saved.getEmail(), saved.getRole()));
        return tokenIssuer.issueFor(saved);
    }

    /**
     * The checks above cannot prevent two simultaneous registrations of the same
     * username or email: both can pass the check before either inserts. The unique
     * constraints in the database are the real guard, so the loser of that race is
     * translated into the same 409 it would have received a moment earlier, instead
     * of surfacing as a 500.
     */
    private User save(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            log.info("Registration lost a race on a unique constraint");
            throw new ConflictException("Username or email already exists", "ACCOUNT_EXISTS");
        }
    }
}
