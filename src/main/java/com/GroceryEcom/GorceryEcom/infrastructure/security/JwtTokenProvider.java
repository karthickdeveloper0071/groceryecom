package com.GroceryEcom.GorceryEcom.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.*;

/**
 * JWT Token Provider - Generates and validates JWT tokens
 * Handles token creation, validation, and claims extraction
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:900000}")
    private Long jwtExpirationMs;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private Long refreshTokenExpirationMs;

    /**
     * Generate JWT Access Token
     */
    public String generateAccessToken(Long userId, String username, String email, List<String> roles) {
        return generateToken(userId, username, email, roles, jwtExpirationMs, "ACCESS");
    }

    /**
     * Generate JWT Refresh Token
     */
    public String generateRefreshToken(Long userId, String username) {
        return generateToken(userId, username, null, Collections.emptyList(), 
                            refreshTokenExpirationMs, "REFRESH");
    }

    /**
     * Generate token with custom claims
     */
    private String generateToken(Long userId, String username, String email, 
                                List<String> roles, Long expirationMs, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("email", email);
        claims.put("roles", roles);
        claims.put("tokenType", tokenType);

        log.debug("Generating {} token for user: {}", tokenType, username);

        return Jwts.builder()
                // setClaims replaces all claims, so it must come before setSubject
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Get user ID from token
     */
    public Long getUserIdFromToken(String token) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            }
            return Long.parseLong(userIdObj.toString());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            throw new JwtAuthenticationException("Token expired", e);
        } catch (MalformedJwtException | SignatureException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid token", e);
        }
    }

    /**
     * Get username from token
     */
    public String getUsernameFromToken(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    /**
     * Get email from token
     */
    public String getEmailFromToken(String token) {
        return (String) getAllClaimsFromToken(token).get("email");
    }

    /**
     * Get roles from token
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return (List<String>) getAllClaimsFromToken(token).get("roles");
    }

    /**
     * Check if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("JWT token is malformed: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.warn("JWT signature validation failed: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if token is refresh token
     */
    public boolean isRefreshToken(String token) {
        return hasTokenType(token, "REFRESH");
    }

    /**
     * Check if token is access token
     */
    public boolean isAccessToken(String token) {
        return hasTokenType(token, "ACCESS");
    }

    private boolean hasTokenType(String token, String expectedType) {
        try {
            Claims claims = getAllClaimsFromToken(token);
            return expectedType.equals(claims.get("tokenType"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get all claims from token
     */
    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extract token from Bearer string
     */
    public String extractTokenFromBearer(String bearerToken) {
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return bearerToken;
    }
}

