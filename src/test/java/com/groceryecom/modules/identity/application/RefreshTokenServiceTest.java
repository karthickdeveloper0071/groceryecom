package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String INVALID = "Invalid or expired refresh token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuditLog auditLog;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        when(tokenProvider.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));
        refreshTokenService = new RefreshTokenService(userRepository, tokenProvider, new AuthTokenIssuer(tokenProvider), auditLog);
    }

    @Test
    void issuesANewPairForAValidRefreshToken() {
        when(tokenProvider.parseRefreshToken("refresh")).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(tokenProvider.createAccessToken(USER_ID, "alice", java.util.List.of("CUSTOMER"))).thenReturn("newAccess");
        when(tokenProvider.createRefreshToken(USER_ID)).thenReturn("newRefresh");

        var response = refreshTokenService.execute("refresh");

        assertThat(response.accessToken()).isEqualTo("newAccess");
        assertThat(response.refreshToken()).isEqualTo("newRefresh");
    }

    @Test
    void rejectsATokenThatIsNotAValidRefreshToken() {
        when(tokenProvider.parseRefreshToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.execute("nope"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);
    }

    @Test
    void aDeletedUserGetsTheSameMessageAsAnInvalidToken() {
        when(tokenProvider.parseRefreshToken("refresh")).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.execute("refresh"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);
    }

    @Test
    void aDeactivatedUserCannotRefresh() {
        User user = activeUser();
        user.setIsActive(false);
        when(tokenProvider.parseRefreshToken("refresh")).thenReturn(Optional.of(USER_ID));
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> refreshTokenService.execute("refresh"))
                .hasMessage("User account is disabled");
    }

    private static User activeUser() {
        User user = new User();
        user.setPublicId(USER_ID);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole(Role.CUSTOMER);
        user.setIsActive(true);
        user.setIsDeleted(false);
        return user;
    }
}
