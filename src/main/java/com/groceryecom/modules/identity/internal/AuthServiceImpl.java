package com.groceryecom.modules.identity.internal;

import com.groceryecom.modules.identity.api.Role;
import com.groceryecom.modules.identity.api.UserRegisteredEvent;
import com.groceryecom.modules.identity.web.dto.AuthTokenDTO;
import com.groceryecom.modules.identity.web.dto.UserLoginDTO;
import com.groceryecom.modules.identity.web.dto.UserRegistrationDTO;
import com.groceryecom.modules.identity.web.dto.UserResponseDTO;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.UnauthorizedException;
import com.groceryecom.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
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
 * Authentication Service Implementation
 * Handles user registration, login, token refresh, and password management
 */
@Service
@RequiredArgsConstructor
@Slf4j
class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ApplicationEventPublisher events;

    /**
     * Register a new customer account.
     * Self-registration always creates a CUSTOMER; vendor and admin accounts are
     * created through vendor onboarding and the admin console.
     */
    @Override
    @Transactional
    public AuthTokenDTO register(UserRegistrationDTO registrationDTO) {
        String username = normalize(registrationDTO.getUsername());
        String email = normalize(registrationDTO.getEmail());
        log.info("Registering new user: {}", username);

        // Validate input
        if (userRepository.existsByUsername(username)) {
            throw new ValidationException("Username already exists", "USERNAME_EXISTS");
        }

        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Email already exists", "EMAIL_EXISTS");
        }

        // Create new user
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(registrationDTO.getPassword()));
        user.setFirstName(registrationDTO.getFirstName());
        user.setLastName(registrationDTO.getLastName());
        user.setPhoneNumber(registrationDTO.getPhoneNumber());
        user.setRole(Role.CUSTOMER);
        user.setIsActive(true);
        user.setEmailVerified(false);

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getPublicId());

        events.publishEvent(new UserRegisteredEvent(savedUser.getPublicId(), savedUser.getEmail(), savedUser.getRole()));

        // Generate tokens
        return generateAuthToken(savedUser);
    }

    /**
     * Login user with username/email and password
     */
    @Override
    @Transactional(readOnly = true)
    public AuthTokenDTO login(UserLoginDTO loginDTO) {
        String identifier = normalize(loginDTO.getUsername());
        log.info("User login attempt: {}", identifier);

        // Find user by email or username
        Optional<User> found = identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(identifier);
        User user = found.orElseThrow(() -> {
            log.warn("Login failed - User not found: {}", identifier);
            return new UnauthorizedException("Invalid username or password");
        });

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("Login failed - User is inactive: {}", user.getPublicId());
            throw new UnauthorizedException("User account is inactive");
        }

        // Validate password
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed - Invalid password for user: {}", user.getPublicId());
            throw new UnauthorizedException("Invalid username or password");
        }

        log.info("User logged in successfully: {}", user.getPublicId());

        // Generate and return tokens
        return generateAuthToken(user);
    }

    /**
     * Refresh access token using refresh token
     */
    @Override
    @Transactional(readOnly = true)
    public AuthTokenDTO refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Check if it's actually a refresh token
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Token is not a refresh token");
        }

        // Get user from database
        UUID userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is inactive");
        }

        return generateAuthToken(user);
    }

    /**
     * Logout user (token invalidation would be done via Redis blacklist in production)
     */
    @Override
    public void logout(UUID userId) {
        log.info("User logged out: {}", userId);
        // In production, add token to Redis blacklist
    }

    /**
     * Change password
     */
    @Override
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword) {
        log.info("User changing password: {}", userId);

        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Validate old password
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("Old password is incorrect");
        }

        // Validate new password
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ValidationException("New password must be different from old password");
        }

        // Update password (dirty checking saves it at commit)
        user.setPasswordHash(passwordEncoder.encode(newPassword));

        log.info("Password changed successfully for user: {}", userId);
    }

    /**
     * Request password reset
     */
    @Override
    @Transactional(readOnly = true)
    public void requestPasswordReset(String email) {
        log.info("Password reset requested");

        // Respond the same way whether or not the email exists, so callers cannot probe for accounts
        userRepository.findByEmail(normalize(email)).ifPresentOrElse(
                user -> {
                    // In production, send reset email with token
                    // For now, just log
                    log.info("Password reset email would be sent to user: {}", user.getPublicId());
                },
                () -> log.info("Password reset requested for unknown email")
        );
    }

    /**
     * Reset password with token
     */
    @Override
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        log.info("Resetting password with token");

        // In production, validate reset token from database/Redis
        // For now, just throw error
        throw new ValidationException("Password reset functionality not yet implemented");
    }

    /**
     * Verify email address
     */
    @Override
    @Transactional
    public void verifyEmail(String verificationToken) {
        log.info("Verifying email with token");

        // In production, validate verification token and mark email as verified
        throw new ValidationException("Email verification functionality not yet implemented");
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Generate authentication token response
     */
    private AuthTokenDTO generateAuthToken(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                List.of(user.getRole().name())
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getPublicId(),
                user.getUsername()
        );

        UserResponseDTO userDTO = UserResponseDTO.builder()
                .id(user.getPublicId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole().name())
                .emailVerified(user.getEmailVerified())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();

        return AuthTokenDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .user(userDTO)
                .build();
    }
}
