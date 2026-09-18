package com.groceryecom.modules.identity.internal;

import com.groceryecom.modules.identity.api.Role;
import com.groceryecom.modules.identity.api.UserRegisteredEvent;
import com.groceryecom.modules.identity.web.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.web.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.web.dto.LoginRequest;
import com.groceryecom.modules.identity.web.dto.RegisterRequest;
import com.groceryecom.modules.identity.web.dto.UserResponse;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.ConflictException;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.exception.UnauthorizedException;
import com.groceryecom.shared.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration, login, token refresh and password changes.
 */
@Slf4j
@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final ApplicationEventPublisher events;

    /**
     * Compared against when the user does not exist, so a failed login takes the same
     * time whether or not the account exists (no account probing by timing).
     */
    private final String dummyPasswordHash;

    AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                JwtTokenProvider tokenProvider, ApplicationEventPublisher events) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.events = events;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Creates a CUSTOMER account. Vendor and admin accounts are created through
     * vendor onboarding and the admin console.
     */
    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        String username = normalize(request.username());
        String email = normalize(request.email());

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

        User saved = userRepository.save(user);
        log.info("Registered user {}", saved.getPublicId());

        events.publishEvent(new UserRegisteredEvent(saved.getPublicId(), saved.getEmail(), saved.getRole()));
        return issueTokens(saved);
    }

    /**
     * @param request username or email, in any letter case, plus password
     */
    @Transactional(readOnly = true)
    public AuthTokenResponse login(LoginRequest request) {
        String identifier = normalize(request.username());
        Optional<User> found = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        User user = found.get();
        // Check the password before account state, so the response never reveals
        // that an account exists to someone who doesn't know its password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Failed login for user {}", user.getPublicId());
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        requireUsable(user);

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse refresh(String refreshToken) {
        UUID userId = tokenProvider.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token"));
        requireUsable(user);

        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        return userRepository.findByPublicId(userId)
                .map(AuthService::toResponse)
                .orElseThrow(() -> new NotFoundException("User", userId));
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ValidationException("Old password is incorrect", "INCORRECT_PASSWORD");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new ValidationException("New password must be different from old password", "PASSWORD_UNCHANGED");
        }

        // Saved at commit by dirty checking
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        log.info("Changed password for user {}", userId);
    }

    private static void requireUsable(User user) {
        if (!user.getIsActive() || user.getIsDeleted()) {
            throw new UnauthorizedException("User account is disabled");
        }
    }

    private AuthTokenResponse issueTokens(User user) {
        String accessToken = tokenProvider.createAccessToken(
                user.getPublicId(), user.getUsername(), List.of(user.getRole().name()));
        String refreshToken = tokenProvider.createRefreshToken(user.getPublicId());
        return AuthTokenResponse.bearer(accessToken, refreshToken,
                tokenProvider.accessTokenTtl().toSeconds(), toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getCreatedAt());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
