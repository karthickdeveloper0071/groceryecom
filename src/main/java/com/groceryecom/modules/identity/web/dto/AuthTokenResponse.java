package com.groceryecom.modules.identity.web.dto;

/**
 * @param expiresIn access token lifetime in seconds
 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {

    public static AuthTokenResponse bearer(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
