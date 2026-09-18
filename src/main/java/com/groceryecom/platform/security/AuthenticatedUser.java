package com.groceryecom.platform.security;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The logged-in user, taken from a verified access token. Inject it into a
 * controller method with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 *
 * @param id       the user's public ID
 * @param username the username at the time the token was issued
 * @param roles    role names such as CUSTOMER or ADMIN
 * @param tokenId  the token's {@code jti}, needed to refuse this one token
 * @param sessionId the session (device) this token belongs to
 * @param issuedAt when the token was issued, compared against a revocation cut-off
 */
public record AuthenticatedUser(UUID id, String username, Set<String> roles, String tokenId, UUID sessionId,
                               Instant issuedAt)
        implements Principal, Serializable {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    @Override
    public String getName() {
        return username;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
