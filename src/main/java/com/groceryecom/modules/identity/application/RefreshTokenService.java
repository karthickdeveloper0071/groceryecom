package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Exchanges a refresh token for a new token pair.
 *
 * <p>Business rules:
 * <ul>
 *   <li>Only a token of type refresh is accepted; an access token is rejected.</li>
 *   <li>The account is re-read from the database, so a deactivated user stops being
 *       able to refresh even while the old refresh token is still unexpired.</li>
 *   <li>An invalid token and a deleted user give the same message.</li>
 * </ul>
 *
 * <p>Not built yet: refresh tokens are not stored, so they cannot be revoked
 * individually before they expire. See the security standard doc.
 */
@Service
public class RefreshTokenService {

    private static final String INVALID_TOKEN = "Invalid or expired refresh token";

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    private final AuthTokenIssuer tokenIssuer;
    private final AuditLog auditLog;

    RefreshTokenService(UserRepository userRepository, JwtTokenProvider tokenProvider, AuthTokenIssuer tokenIssuer,
                        AuditLog auditLog) {
        this.userRepository = userRepository;
        this.tokenProvider = tokenProvider;
        this.tokenIssuer = tokenIssuer;
        this.auditLog = auditLog;
    }

    @Transactional(readOnly = true)
    public AuthTokenResponse execute(String refreshToken) {
        UUID userId = tokenProvider.parseRefreshToken(refreshToken)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN));

        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new UnauthorizedException(INVALID_TOKEN));
        AccountState.requireUsable(user);

        auditLog.tokenRefreshed(user.getPublicId());
        return tokenIssuer.issueFor(user);
    }
}
