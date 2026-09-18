package com.groceryecom.modules.identity.api;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.api.dto.LoginRequest;
import com.groceryecom.modules.identity.api.dto.RefreshTokenRequest;
import com.groceryecom.modules.identity.api.dto.RegisterRequest;
import com.groceryecom.modules.identity.api.dto.SessionResponse;
import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.application.ChangePasswordService;
import com.groceryecom.modules.identity.application.GetUserService;
import com.groceryecom.modules.identity.application.LoginService;
import com.groceryecom.modules.identity.application.RefreshTokenService;
import com.groceryecom.modules.identity.application.RegisterCustomerService;
import com.groceryecom.modules.identity.application.SessionService;
import com.groceryecom.modules.identity.domain.ClientInfo;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Registration, login, tokens, passwords and the caller's own sessions.
 * Served under the /api context path, so the public URL is /api/v1/auth.
 *
 * <p>Thin by design: read the request, call one application service, return its result.
 * Business rules live in {@code modules.identity.application}.
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, tokens, passwords and sessions")
class AuthController {

    private final RegisterCustomerService registerCustomerService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final GetUserService getUserService;
    private final ChangePasswordService changePasswordService;
    private final SessionService sessionService;

    AuthController(RegisterCustomerService registerCustomerService, LoginService loginService,
                   RefreshTokenService refreshTokenService, GetUserService getUserService,
                   ChangePasswordService changePasswordService, SessionService sessionService) {
        this.registerCustomerService = registerCustomerService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.getUserService = getUserService;
        this.changePasswordService = changePasswordService;
        this.sessionService = sessionService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account",
            description = "Always creates a CUSTOMER. Returns tokens, so the caller is logged in.")
    ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                            HttpServletRequest httpRequest) {
        return ApiResponse.ok(registerCustomerService.execute(request, clientInfo(httpRequest)), "Registered");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username or email and password",
            description = "Starts a session, which appears in GET /v1/auth/sessions.")
    ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletRequest httpRequest) {
        return ApiResponse.ok(loginService.execute(request, clientInfo(httpRequest)));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Exchange a refresh token for a new token pair",
            description = "The refresh token works once. Reusing one ends every session of that user.")
    ApiResponse<AuthTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest httpRequest) {
        return ApiResponse.ok(refreshTokenService.execute(request.refreshToken(), clientInfo(httpRequest)));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Get the logged-in user's profile")
    ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(getUserService.execute(user.id()));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Change the logged-in user's password",
            description = "Ends every session, including this one, so the client must log in again.")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordService.execute(user.id(), request);
        return ApiResponse.ok(null, "Password changed. Please log in again.");
    }

    @GetMapping("/sessions")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "List the devices logged in to this account",
            description = "The entry with current=true is the device making this request.")
    ApiResponse<List<SessionResponse>> sessions(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(sessionService.listSessions(user));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Sign out one device",
            description = "Use an id from GET /v1/auth/sessions. Only your own sessions can be ended.")
    ApiResponse<Void> revokeSession(@AuthenticationPrincipal AuthenticatedUser user,
                                    @PathVariable UUID sessionId) {
        sessionService.revokeSession(user, sessionId);
        return ApiResponse.ok(null, "Device signed out");
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Log out of this device",
            description = "Ends the current session and refuses the access token it was used with. "
                    + "Other devices stay logged in.")
    ApiResponse<Void> logout(@AuthenticationPrincipal AuthenticatedUser user) {
        sessionService.revokeCurrentSession(user);
        return ApiResponse.ok(null, "Logged out");
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Log out of every device",
            description = "Ends all sessions of the logged-in user, for a lost or stolen device.")
    ApiResponse<Void> logoutEverywhere(@AuthenticationPrincipal AuthenticatedUser user) {
        sessionService.revokeAllSessions(user.id(), "logout of all devices");
        return ApiResponse.ok(null, "Logged out of all devices");
    }

    /**
     * The client address is trustworthy because X-Forwarded-For is only honoured from
     * configured proxies (see {@code server.forward-headers-strategy}).
     */
    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT));
    }
}
