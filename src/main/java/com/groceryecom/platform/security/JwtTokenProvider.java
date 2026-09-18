package com.groceryecom.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and verifies signed JWTs (HS512).
 *
 * <p>The subject is the user's public ID. A {@code token_type} claim keeps access and
 * refresh tokens from being used in each other's place, and every token carries a unique
 * id ({@code jti}) so a single session can be revoked without invalidating the others.
 */
@Slf4j
@Component
public final class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    /** Session this token belongs to, so one device can be signed out precisely. */
    private static final String CLAIM_SESSION_ID = "sid";
    /**
     * Issue time in milliseconds. The standard {@code iat} claim is only accurate to the
     * second, which is too coarse for revocation: "log out everywhere" must refuse a token
     * issued 200 ms earlier while accepting the one from logging straight back in.
     */
    private static final String CLAIM_ISSUED_AT_MS = "iat_ms";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the token, and the id and issue time needed to revoke it later */
    public IssuedToken createAccessToken(UUID userId, UUID sessionId, String username, Collection<String> roles) {
        Instant now = clock.instant();
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, ACCESS)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_SESSION_ID, sessionId != null ? sessionId.toString() : null)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_ISSUED_AT_MS, now.toEpochMilli())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
        return new IssuedToken(token, tokenId, now);
    }

    public IssuedToken createRefreshToken(UUID userId) {
        Instant now = clock.instant();
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(tokenId)
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenTtl())))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
        return new IssuedToken(token, tokenId, now);
    }

    /**
     * The user behind a valid, unexpired access token; empty for anything else.
     * Whether the token was revoked is a separate question, answered by the token registry.
     */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        return parse(token, ACCESS).map(claims -> new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                rolesOf(claims),
                claims.getId(),
                sessionIdOf(claims),
                issuedAtOf(claims)));
    }

    /** The user and token id behind a valid, unexpired refresh token; empty for anything else. */
    public Optional<RefreshTokenDetails> parseRefreshToken(String token) {
        return parse(token, REFRESH).map(claims -> new RefreshTokenDetails(
                UUID.fromString(claims.getSubject()),
                claims.getId(),
                claims.getExpiration().toInstant()));
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
    }

    public Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }

    /** How much of an access token's life is left, used as the revocation entry's lifetime. */
    public Duration remainingLifetime(Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private Optional<Claims> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                log.debug("Rejected token: expected {} token", expectedType);
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID sessionIdOf(Claims claims) {
        String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
        return sessionId != null ? UUID.fromString(sessionId) : null;
    }

    /** Prefers the millisecond claim; falls back to iat for a token issued by an older build. */
    private static Instant issuedAtOf(Claims claims) {
        Long issuedAtMs = claims.get(CLAIM_ISSUED_AT_MS, Long.class);
        return issuedAtMs != null ? Instant.ofEpochMilli(issuedAtMs) : claims.getIssuedAt().toInstant();
    }

    private static Set<String> rolesOf(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (!(roles instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @param token    the signed token to hand to the client
     * @param tokenId  its {@code jti}
     * @param issuedAt its issue time
     */
    public record IssuedToken(String token, String tokenId, Instant issuedAt) {
    }

    /**
     * @param expiresAt used as the lifetime of the registry entry, so it disappears with the token
     */
    public record RefreshTokenDetails(UUID userId, String tokenId, Instant expiresAt) {
    }
}
