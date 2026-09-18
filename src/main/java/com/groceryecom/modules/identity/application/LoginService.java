package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.LoginRequest;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.shared.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Exchanges a username or email plus password for tokens.
 *
 * <p>Business rules:
 * <ul>
 *   <li>The identifier may be the username or the email, in any letter case.</li>
 *   <li>Wrong credentials and unknown accounts give the same answer, and an unknown
 *       account still costs one password comparison, so neither the message nor the
 *       response time tells an attacker which usernames exist.</li>
 *   <li>The password is checked before account state, so "account disabled" is only
 *       revealed to someone who already knows the password.</li>
 * </ul>
 */
@Slf4j
@Service
public class LoginService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;
    private final AuditLog auditLog;

    /** Compared against when no account matches, to keep the timing indistinguishable. */
    private final String dummyPasswordHash;

    LoginService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthTokenIssuer tokenIssuer,
                 AuditLog auditLog) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.auditLog = auditLog;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse execute(LoginRequest request) {
        String identifier = UserNormalizer.normalize(request.username());

        Optional<User> found = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditLog.loginFailed(null, "no such account");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLog.loginFailed(user.getPublicId(), "wrong password");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        try {
            AccountState.requireUsable(user);
        } catch (UnauthorizedException e) {
            auditLog.loginFailed(user.getPublicId(), "account disabled");
            throw e;
        }

        auditLog.loginSucceeded(user.getPublicId());
        return tokenIssuer.issueFor(user);
    }
}
