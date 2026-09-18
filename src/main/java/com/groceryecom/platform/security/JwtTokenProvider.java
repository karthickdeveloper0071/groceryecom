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
 * The subject is the user's public ID; a {@code token_type} claim keeps access and
 * refresh tokens from being used in each other's place.
 */
@Slf4j
@Component
public final class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
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

    public String createAccessToken(UUID userId, String username, Collection<String> roles) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, ACCESS)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenTtl())))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    /** The user behind a valid, unexpired access token; empty for anything else. */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        return parse(token, ACCESS).map(claims -> new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                rolesOf(claims)));
    }

    /** The user ID behind a valid, unexpired refresh token; empty for anything else. */
    public Optional<UUID> parseRefreshToken(String token) {
        return parse(token, REFRESH).map(claims -> UUID.fromString(claims.getSubject()));
    }

    public Duration accessTokenTtl() {
        return properties.accessTokenTtl();
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

    private static Set<String> rolesOf(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        if (!(roles instanceof Collection<?> values)) {
            return Set.of();
        }
        return values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }
}
