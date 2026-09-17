package com.GroceryEcom.GorceryEcom.modules.user.service;

import com.GroceryEcom.GorceryEcom.common.exception.ValidationException;
import com.GroceryEcom.GorceryEcom.common.exception.UnauthorizedException;
import com.GroceryEcom.GorceryEcom.infrastructure.security.JwtTokenProvider;
import com.GroceryEcom.GorceryEcom.modules.user.dto.*;
import com.GroceryEcom.GorceryEcom.modules.user.model.User;
import com.GroceryEcom.GorceryEcom.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Optional;

/**
 * Authentication Service Implementation
 * Handles user registration, login, token refresh, and password management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Register a new user
     */
    @Override
    @Transactional
    public AuthTokenDTO register(UserRegistrationDTO registrationDTO) {
        log.info("Registering new user: {}", registrationDTO.getUsername());

        // Validate input
        if (userRepository.existsByUsername(registrationDTO.getUsername())) {
            throw new ValidationException("Username already exists", "USERNAME_EXISTS");
        }

        if (userRepository.existsByEmail(registrationDTO.getEmail())) {
            throw new ValidationException("Email already exists", "EMAIL_EXISTS");
        }

        // Create new user
        User user = new User();
        user.setUsername(registrationDTO.getUsername());
        user.setEmail(registrationDTO.getEmail());
        user.setPasswordHash(passwordEncoder.encode(registrationDTO.getPassword()));
        user.setFirstName(registrationDTO.getFirstName());
        user.setLastName(registrationDTO.getLastName());
        user.setPhoneNumber(registrationDTO.getPhoneNumber());

        // Set role (default: CUSTOMER)
        String role = registrationDTO.getRole() != null ? registrationDTO.getRole() : "CUSTOMER";
        try {
            user.setRole(User.UserRole.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException e) {
            user.setRole(User.UserRole.CUSTOMER);
        }

        user.setIsActive(true);
        user.setEmailVerified(false);

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        // Generate tokens
        return generateAuthToken(savedUser);
    }

    /**
     * Login user with username/email and password
     */
    @Override
    @Transactional(readOnly = true)
    public AuthTokenDTO login(UserLoginDTO loginDTO) {
        log.info("User login attempt: {}", loginDTO.getUsername());

        // Find user by username or email
        User user = userRepository.findByUsernameOrEmail(loginDTO.getUsername(), loginDTO.getUsername())
                .orElseThrow(() -> {
                    log.warn("Login failed - User not found: {}", loginDTO.getUsername());
                    return new UnauthorizedException("Invalid username or password");
                });

        // Check if user is active
        if (!user.getIsActive()) {
            log.warn("Login failed - User is inactive: {}", user.getId());
            throw new UnauthorizedException("User account is inactive");
        }

        // Validate password
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed - Invalid password for user: {}", user.getId());
            throw new UnauthorizedException("Invalid username or password");
        }

        log.info("User logged in successfully: {}", user.getId());

        // Generate and return tokens
        return generateAuthToken(user);
    }

    /**
     * Refresh access token using refresh token
     */
    @Override
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

        // Extract user info from refresh token
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // Get user from database
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("User account is inactive");
        }

        // Generate new tokens
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Arrays.asList(user.getRole().name())
        );

        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return AuthTokenDTO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(900L) // 15 minutes
                .build();
    }

    /**
     * Logout user (token invalidation would be done via Redis blacklist in production)
     */
    @Override
    public void logout(Long userId) {
        log.info("User logged out: {}", userId);
        // In production, add token to Redis blacklist
    }

    /**
     * Change password
     */
    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        log.info("User changing password: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        // Validate old password
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("Old password is incorrect");
        }

        // Validate new password
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ValidationException("New password must be different from old password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password changed successfully for user: {}", userId);
    }

    /**
     * Request password reset
     */
    @Override
    public void requestPasswordReset(String email) {
        log.info("Password reset requested for email: {}", email);

        // Respond the same way whether or not the email exists, so callers cannot probe for accounts
        userRepository.findByEmail(email).ifPresentOrElse(
                user -> {
                    // In production, send reset email with token
                    // For now, just log
                    log.info("Password reset email would be sent to user: {}", user.getId());
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

    /**
     * Generate authentication token response
     */
    private AuthTokenDTO generateAuthToken(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Arrays.asList(user.getRole().name())
        );

        String refreshToken = jwtTokenProvider.generateRefreshToken(
                user.getId(),
                user.getUsername()
        );

        UserResponseDTO userDTO = UserResponseDTO.builder()
                .id(user.getId())
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
                .expiresIn(900L) // 15 minutes in seconds
                .user(userDTO)
                .build();
    }
}

