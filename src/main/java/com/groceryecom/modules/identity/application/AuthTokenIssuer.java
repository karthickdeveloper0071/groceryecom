package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.mapper.UserMapper;
import com.groceryecom.platform.security.JwtTokenProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the token response returned by registration, login and refresh, so the
 * three use cases cannot drift apart in what they hand back to clients.
 */
@Component
class AuthTokenIssuer {

    private final JwtTokenProvider tokenProvider;

    AuthTokenIssuer(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    AuthTokenResponse issueFor(User user) {
        String accessToken = tokenProvider.createAccessToken(
                user.getPublicId(), user.getUsername(), List.of(user.getRole().name()));
        String refreshToken = tokenProvider.createRefreshToken(user.getPublicId());

        return AuthTokenResponse.bearer(accessToken, refreshToken,
                tokenProvider.accessTokenTtl().toSeconds(), UserMapper.toResponse(user));
    }
}
