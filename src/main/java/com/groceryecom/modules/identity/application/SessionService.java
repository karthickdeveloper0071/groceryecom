package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.SessionResponse;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserSession;
import com.groceryecom.modules.identity.domain.UserSessionRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.platform.security.token.TokenRegistry;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessions: one per login, per device.
 *
 * <p>The database is the source of truth. A refresh token is only usable while the session
 * it belongs to is live, so revoking a session immediately stops that device from renewing
 * itself; the {@link TokenRegistry} additionally refuses its access tokens for the few
 * minutes they have left.
 */
@Slf4j
@Service
public class SessionService {

    private static final String INVALID_TOKEN = "Invalid or expired refresh token";

    private final UserSessionRepository sessions;
    private final TokenRegistry tokenRegistry;
    private final JwtTokenProvider tokenProvider;
    private final AuditLog auditLog;

    SessionService(UserSessionRepository sessions, TokenRegistry tokenRegistry,
                   JwtTokenProvider tokenProvider, AuditLog auditLog) {
        this.sessions = sessions;
        this.tokenRegistry = tokenRegistry;
        this.tokenProvider = tokenProvider;
        this.auditLog = auditLog;
    }

    @Transactional
    public UserSession startSession(User user, String refreshTokenId, ClientInfo client) {
        UserSession session = UserSession.start(
                user, refreshTokenId, Instant.now().plus(tokenProvider.refreshTokenTtl()), client);
        return sessions.saveAndFlush(session);
    }

    /**
     * The live session a refresh token belongs to.
     *
     * <p>Empty means the token was already rotated, its session was revoked, or it does not
     * belong to the user it claims to: the caller must treat that as replay. This method
     * deliberately does not revoke anything itself, so that the revocation can be committed
     * in its own transaction while the request still fails.
     */
    @Transactional(readOnly = true)
    public Optional<UserSession> findLiveSessionForRefresh(UUID userId, String refreshTokenId) {
        Optional<UserSession> session = sessions.findByRefreshTokenId(refreshTokenId)
                .filter(candidate -> candidate.isLive(Instant.now()));

        if (session.isPresent() && !session.get().getUser().getPublicId().equals(userId)) {
            // The token's subject and the session's owner disagree: never rotate that
            log.warn("Refresh token subject does not match its session owner");
            return Optional.empty();
        }
        return session;
    }

    /** Moves a live session onto a new refresh token, keeping it one device in the list. */
    @Transactional
    public UserSession rotate(UserSession session, String newRefreshTokenId, ClientInfo client) {
        try {
            session.rotateTo(newRefreshTokenId, Instant.now().plus(tokenProvider.refreshTokenTtl()), client);
            return sessions.saveAndFlush(session);
        } catch (DataIntegrityViolationException e) {
            // Two refreshes with the same token arrived together; only one may win
            throw new UnauthorizedException(INVALID_TOKEN);
        }
    }

    /**
     * Records that a refresh token was replayed: every session of that user ends.
     *
     * <p>Runs in its own transaction, because the request that discovered the replay then
     * fails with 401 and would otherwise roll the revocation back, leaving it only in the
     * Redis cache.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReplay(UUID userId) {
        revokeAllSessions(userId, "refresh token replay");
        auditLog.refreshTokenReused(userId);
    }

    /** Signs out the device the caller is using, and refuses the access token it sent. */
    @Transactional
    public void revokeCurrentSession(AuthenticatedUser user) {
        tokenRegistry.revokeAccessToken(user.id(), user.tokenId(), tokenProvider.remainingLifetime(user.issuedAt()));

        if (user.sessionId() != null) {
            sessions.findByPublicIdAndUserPublicId(user.sessionId(), user.id())
                    .ifPresent(this::revoke);
        }
        auditLog.loggedOut(user.id());
    }

    /** Signs out one device chosen from the list, which may or may not be the current one. */
    @Transactional
    public void revokeSession(AuthenticatedUser user, UUID sessionPublicId) {
        UserSession session = sessions.findByPublicIdAndUserPublicId(sessionPublicId, user.id())
                .orElseThrow(() -> new NotFoundException("Session", sessionPublicId));

        revoke(session);
        if (sessionPublicId.equals(user.sessionId())) {
            tokenRegistry.revokeAccessToken(
                    user.id(), user.tokenId(), tokenProvider.remainingLifetime(user.issuedAt()));
        }
        auditLog.sessionRevoked(user.id(), sessionPublicId);
    }

    /**
     * Ends every session of a user: "log out everywhere", a password change, an admin
     * lock-out.
     *
     * <p>Its own transaction on purpose: revoking must survive even when the caller's
     * transaction fails afterwards. Erring towards "sessions ended" is the safe direction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(UUID userPublicId, String reason) {
        int ended = sessions.revokeAllForUser(userPublicId, Instant.now());
        // One cut-off refuses the access tokens of all those sessions at once
        tokenRegistry.revokeAllTokens(userPublicId, Instant.now(), tokenProvider.refreshTokenTtl());
        auditLog.allSessionsRevoked(userPublicId);
        log.info("Ended {} session(s) for user {}: {}", ended, userPublicId, reason);
    }

    /** The caller's devices, so they can spot one they do not recognise. */
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(AuthenticatedUser user) {
        return sessions.findByUserPublicIdAndRevokedAtIsNullOrderByCreatedAtDesc(user.id()).stream()
                .map(session -> SessionResponse.from(session, session.getPublicId().equals(user.sessionId())))
                .toList();
    }

    private void revoke(UserSession session) {
        session.revoke();
        sessions.saveAndFlush(session);
        // Access tokens of that session stop working now, not in fifteen minutes
        tokenRegistry.revokeSession(session.getPublicId(), tokenProvider.accessTokenTtl());
    }
}
