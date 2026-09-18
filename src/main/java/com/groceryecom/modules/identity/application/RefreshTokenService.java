package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.modules.identity.domain.UserSession;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.platform.security.JwtTokenProvider.RefreshTokenDetails;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchanges a refresh token for a new token pair, keeping the same session alive.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Only a token of type refresh is accepted; an access token is rejected.</li>
 *   <li>A refresh token works <b>once</b>: using it moves the session onto a new token, so
 *       a copy taken from a log or a proxy stops working as soon as the real client
 *       refreshes.</li>
 *   <li>Presenting a token that belongs to no live session means it was already rotated or
 *       its session was revoked. Two parties may hold it, so every session of that user is
 *       ended. See {@link SessionService#rotate}.</li>
 *   <li>The account is re-read, so a deactivated user cannot refresh even while an
 *       unexpired refresh token exists.</li>
 *   <li>Every failure returns the same message.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final String INVALID_TOKEN = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthTokenIssuer tokenIssuer;
    private final SessionService sessionService;
    private final AuditLog auditLog;

    RefreshTokenService(UserRepository userRepository, JwtTokenProvider tokenProvider,
                        AuthTokenIssuer tokenIssuer, SessionService sessionService, AuditLog auditLog) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.tokenIssuer = tokenIssuer;
        this.sessionService = sessionService;
        this.auditLog = auditLog;
    }

    @Transactional
    public AuthTokenResponse execute(String refreshToken, ClientInfo client) {
        RefreshTokenDetails details = tokenProvider.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN));

        User user = userRepository.findByPublicId(details.userId())
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN));
        AccountState.requireUsable(user);

        UserSession session = sessionService.findLiveSessionForRefresh(details.userId(), details.tokenId())
                .orElse(null);
        if (session == null) {
            // Already rotated, revoked, or not this user's: assume the token leaked
            sessionService.handleReplay(details.userId());
            throw new UnauthorizedException(INVALID_TOKEN);
        }

        AuthTokenResponse response = tokenIssuer.continueSession(user, session, client);
        auditLog.tokenRefreshed(user.getPublicId());
        return response;
    }
}
