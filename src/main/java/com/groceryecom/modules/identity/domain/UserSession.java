package com.groceryecom.modules.identity.domain;

import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One login on one device.
 *
 * <p>A session owns the refresh token that keeps it alive. Rotating that token keeps the
 * same session, so "my phone" stays one row in the user's device list however many times
 * the client refreshes.
 */
@Entity
@Table(name = "user_sessions")
@Getter
@Setter
@NoArgsConstructor
public class UserSession extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** Exposed to clients, so a user can revoke one device without seeing database ids. */
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    /** jti of the refresh token currently valid for this session. */
    @Column(name = "refresh_token_id", nullable = false, unique = true, length = 64)
    private String refreshTokenId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public static UserSession start(User user, String refreshTokenId, Instant expiresAt, ClientInfo client) {
        UserSession session = new UserSession();
        session.publicId = UUID.randomUUID();
        session.user = user;
        session.refreshTokenId = refreshTokenId;
        session.expiresAt = expiresAt;
        session.lastUsedAt = Instant.now();
        session.ipAddress = client.ipAddress();
        session.userAgent = client.userAgent();
        return session;
    }

    /** Keeps the session alive under a new refresh token. */
    public void rotateTo(String newRefreshTokenId, Instant newExpiresAt, ClientInfo client) {
        this.refreshTokenId = newRefreshTokenId;
        this.expiresAt = newExpiresAt;
        this.lastUsedAt = Instant.now();
        // A moved client is normal (new IP on mobile data); the latest is the useful one
        this.ipAddress = client.ipAddress();
        this.userAgent = client.userAgent();
    }

    /** Revoking is final: a revoked session is never reopened, a new login creates a new one. */
    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
        setIsActive(false);
    }

    public boolean isLive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
