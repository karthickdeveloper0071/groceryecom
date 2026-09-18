package com.groceryecom.modules.identity.api.dto;

import com.groceryecom.modules.identity.domain.UserSession;

import java.time.Instant;
import java.util.UUID;

/**
 * One device in the user's session list.
 *
 * @param id        pass this to DELETE /v1/auth/sessions/{id} to sign that device out
 * @param current   true for the device making this request
 * @param ipAddress the address the session was last used from
 * @param userAgent what the client called itself; shown so a user can recognise a device
 */
public record SessionResponse(
        UUID id,
        boolean current,
        String ipAddress,
        String userAgent,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt) {

    public static SessionResponse from(UserSession session, boolean current) {
        return new SessionResponse(
                session.getPublicId(),
                current,
                session.getIpAddress(),
                session.getUserAgent(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt());
    }
}
