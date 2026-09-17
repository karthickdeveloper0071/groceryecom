package com.groceryecom.platform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret",
                "test-secret-for-junit-only-must-be-at-least-sixty-four-bytes-long-for-hs512");
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpirationMs", 900000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void accessTokenKeepsSubjectAndClaims() {
        String token = jwtTokenProvider.generateAccessToken(42L, "alice", "alice@example.com", List.of("CUSTOMER"));

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(jwtTokenProvider.getUserIdFromToken(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getEmailFromToken(token)).isEqualTo("alice@example.com");
        assertThat(jwtTokenProvider.getRolesFromToken(token)).containsExactly("CUSTOMER");
        assertThat(jwtTokenProvider.isRefreshToken(token)).isFalse();
    }

    @Test
    void refreshTokenKeepsSubject() {
        String token = jwtTokenProvider.generateRefreshToken(42L, "alice");

        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(jwtTokenProvider.isRefreshToken(token)).isTrue();
    }
}
