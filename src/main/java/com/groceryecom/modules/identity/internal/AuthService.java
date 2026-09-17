package com.groceryecom.modules.identity.internal;

import com.groceryecom.modules.identity.web.dto.AuthTokenDTO;
import com.groceryecom.modules.identity.web.dto.UserLoginDTO;
import com.groceryecom.modules.identity.web.dto.UserRegistrationDTO;

import java.util.UUID;

/**
 * Authentication Service Interface
 * Defines authentication and authorization operations
 */
public interface AuthService {

    /**
     * Register a new customer account
     * @param registrationDTO User registration data
     * @return Authentication token response
     */
    AuthTokenDTO register(UserRegistrationDTO registrationDTO);

    /**
     * Login user with credentials
     * @param loginDTO User login credentials
     * @return Authentication token response
     */
    AuthTokenDTO login(UserLoginDTO loginDTO);

    /**
     * Refresh access token using refresh token
     * @param refreshToken Refresh token
     * @return New authentication token response
     */
    AuthTokenDTO refreshToken(String refreshToken);

    /**
     * Logout user (invalidate token)
     * @param userId User public ID
     */
    void logout(UUID userId);

    /**
     * Change user password
     * @param userId User public ID
     * @param oldPassword Current password
     * @param newPassword New password
     */
    void changePassword(UUID userId, String oldPassword, String newPassword);

    /**
     * Request password reset
     * @param email User email address
     */
    void requestPasswordReset(String email);

    /**
     * Reset password with token
     * @param resetToken Password reset token
     * @param newPassword New password
     */
    void resetPassword(String resetToken, String newPassword);

    /**
     * Verify email address
     * @param verificationToken Email verification token
     */
    void verifyEmail(String verificationToken);
}
