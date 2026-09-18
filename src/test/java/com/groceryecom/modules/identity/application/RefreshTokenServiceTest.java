package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.contract.Role;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.modules.identity.domain.UserRepository;
import com.groceryecom.modules.identity.domain.UserSession;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.JwtTokenProvider;
import com.groceryecom.platform.security.JwtTokenProvider.RefreshTokenDetails;
import com.groceryecom.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The checks this service owns. Rotation and replay detection live in
 * {@link SessionService} and are covered against the database by
 * {@code SessionServiceIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TOKEN_ID = "refresh-token-id";
    private static final String INVALID = "Invalid or expired refresh token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthTokenIssuer tokenIssuer;

    @Mock
    private SessionService sessionService;

    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void continuesTheSessionForAValidToken() {
        givenValidRefreshToken();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(sessionService.findLiveSessionForRefresh(USER_ID, TOKEN_ID))
                .thenReturn(Optional.of(new UserSession()));
        when(tokenIssuer.continueSession(any(), any(), any()))
                .thenReturn(AuthTokenResponse.bearer("newAccess", "newRefresh", 900, null));

        AuthTokenResponse response = refreshTokenService.execute("refresh", ClientInfo.UNKNOWN);

        assertThat(response.accessToken()).isEqualTo("newAccess");
        verify(auditLog).tokenRefreshed(USER_ID);
    }

    /** A token that matches no live session is treated as leaked: all sessions end. */
    @Test
    void aTokenMatchingNoLiveSessionEndsEverySession() {
        givenValidRefreshToken();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(sessionService.findLiveSessionForRefresh(USER_ID, TOKEN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.execute("refresh", ClientInfo.UNKNOWN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);

        verify(sessionService).handleReplay(USER_ID);
        verify(tokenIssuer, never()).continueSession(any(), any(), any());
    }

    @Test
    void rejectsATokenThatIsNotAValidRefreshToken() {
        when(tokenProvider.parseRefreshToken("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.execute("nope", ClientInfo.UNKNOWN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);

        verify(tokenIssuer, never()).continueSession(any(), any(), any());
    }

    @Test
    void aDeletedUserGetsTheSameMessageAsAnInvalidToken() {
        givenValidRefreshToken();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.execute("refresh", ClientInfo.UNKNOWN))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage(INVALID);
    }

    @Test
    void aDeactivatedUserCannotRefresh() {
        User user = activeUser();
        user.setIsActive(false);
        givenValidRefreshToken();
        when(userRepository.findByPublicId(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> refreshTokenService.execute("refresh", ClientInfo.UNKNOWN))
                .hasMessage("User account is disabled");

        verify(tokenIssuer, never()).continueSession(any(), any(), any());
    }

    private void givenValidRefreshToken() {
        when(tokenProvider.parseRefreshToken("refresh")).thenReturn(Optional.of(
                new RefreshTokenDetails(USER_ID, TOKEN_ID, Instant.now().plusSeconds(604800))));
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
