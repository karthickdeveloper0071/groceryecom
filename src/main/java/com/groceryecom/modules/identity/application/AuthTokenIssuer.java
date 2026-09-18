package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserSession;
import com.groceryecom.modules.identity.mapper.UserMapper;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.platform.security.JwtTokenProvider.IssuedToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the token response returned by registration, login and refresh, so the three use
 * cases cannot drift apart in what they hand back to clients.
 *
 * <p>The refresh token is created first, because the session is identified by it; the
 * access token then carries that session's id, which is what lets one device be signed out.
 */
@Component
class AuthTokenIssuer {

    private final JwtTokenProvider tokenProvider;
    private final SessionService sessionService;

    AuthTokenIssuer(JwtTokenProvider tokenProvider, SessionService sessionService) {
        this.tokenProvider = tokenProvider;
        this.sessionService = sessionService;
    }

    /** A fresh login: starts a new session, which appears in the user's device list. */
    AuthTokenResponse startSession(User user, ClientInfo client) {
        IssuedToken refreshToken = tokenProvider.createRefreshToken(user.getPublicId());
        UserSession session = sessionService.startSession(user, refreshToken.tokenId(), client);
        return respond(user, session, refreshToken);
    }

    /** A refresh: the same session continues under a new refresh token. */
    AuthTokenResponse continueSession(User user, UserSession session, ClientInfo client) {
        IssuedToken refreshToken = tokenProvider.createRefreshToken(user.getPublicId());
        UserSession rotated = sessionService.rotate(session, refreshToken.tokenId(), client);
        return respond(user, rotated, refreshToken);
    }

    private AuthTokenResponse respond(User user, UserSession session, IssuedToken refreshToken) {
        IssuedToken accessToken = tokenProvider.createAccessToken(
                user.getPublicId(), session.getPublicId(), user.getUsername(), List.of(user.getRole().name()));

        return AuthTokenResponse.bearer(accessToken.token(), refreshToken.token(),
                tokenProvider.accessTokenTtl().toSeconds(), UserMapper.toResponse(user));
    }
}
