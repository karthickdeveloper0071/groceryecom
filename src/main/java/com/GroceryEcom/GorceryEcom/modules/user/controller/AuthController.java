package com.GroceryEcom.GorceryEcom.modules.user.controller;

import com.GroceryEcom.GorceryEcom.common.util.ApiResponse;
import com.GroceryEcom.GorceryEcom.modules.user.dto.*;
import com.GroceryEcom.GorceryEcom.modules.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 * Handles user registration, login, token refresh, and password management
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and Authorization endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * User Registration Endpoint
     * Public endpoint - no authentication required
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Register a new user account")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> register(
            @Valid @RequestBody UserRegistrationDTO registrationDTO) {
        
        log.info("User registration request for: {}", registrationDTO.getUsername());
        AuthTokenDTO authToken = authService.register(registrationDTO);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authToken, "User registered successfully"));
    }

    /**
     * User Login Endpoint
     * Public endpoint - no authentication required
     */
    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Authenticate user with username/email and password")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> login(
            @Valid @RequestBody UserLoginDTO loginDTO) {
        
        log.info("User login request for: {}", loginDTO.getUsername());
        AuthTokenDTO authToken = authService.login(loginDTO);
        
        return ResponseEntity.ok(ApiResponse.success(authToken, "Login successful"));
    }

    /**
     * Refresh Access Token Endpoint
     * Public endpoint - no authentication required (uses refresh token instead)
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    public ResponseEntity<ApiResponse<AuthTokenDTO>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        
        log.info("Token refresh request");
        
        // Extract token from Bearer prefix
        String token = authHeader;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        AuthTokenDTO authToken = authService.refreshToken(token);
        
        return ResponseEntity.ok(ApiResponse.success(authToken, "Token refreshed successfully"));
    }

    /**
     * Get Current User Profile
     * Requires authentication
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get current user profile", description = "Get authenticated user's profile information")
    public ResponseEntity<ApiResponse<String>> getCurrentUser() {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        log.info("Current user request for: {}", username);
        
        return ResponseEntity.ok(
                ApiResponse.success(username, "Current user retrieved successfully")
        );
    }

    /**
     * Logout User
     * Requires authentication
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout user", description = "Logout authenticated user and invalidate token")
    public ResponseEntity<ApiResponse<Void>> logout() {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        log.info("User logout request for: {}", username);
        
        // In production, add token to blacklist
        SecurityContextHolder.clearContext();
        
        return ResponseEntity.ok(ApiResponse.success(null, "Logout successful"));
    }

    /**
     * Change Password
     * Requires authentication
     */
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or #userId.toString() == principal.name)")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Change user password", description = "Change password for authenticated user")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        
        log.info("Password change request for user: {}", userId);
        authService.changePassword(userId, changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Password changed successfully"));
    }

    /**
     * Request Password Reset
     * Public endpoint - no authentication required
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Send password reset email")
    public ResponseEntity<ApiResponse<Void>> requestPasswordReset(
            @Valid @RequestBody ForgotPasswordDTO forgotPasswordDTO) {
        
        log.info("Password reset request for email: {}", forgotPasswordDTO.getEmail());
        authService.requestPasswordReset(forgotPasswordDTO.getEmail());
        
        return ResponseEntity.ok(ApiResponse.success(null, 
                "If the email exists, you will receive a password reset link"));
    }

    /**
     * Reset Password with Token
     * Public endpoint - no authentication required
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using reset token")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO) {
        
        log.info("Password reset with token");
        authService.resetPassword(resetPasswordDTO.getResetToken(), resetPasswordDTO.getNewPassword());
        
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset successfully"));
    }

    /**
     * Verify Email Address
     * Public endpoint - no authentication required
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verify email", description = "Verify email address with verification token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(
            @RequestParam String token) {
        
        log.info("Email verification request");
        authService.verifyEmail(token);
        
        return ResponseEntity.ok(ApiResponse.success(null, "Email verified successfully"));
    }
}

