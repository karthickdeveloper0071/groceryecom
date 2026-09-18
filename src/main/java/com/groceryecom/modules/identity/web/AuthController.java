package com.groceryecom.modules.identity.web;

import com.groceryecom.modules.identity.internal.AuthService;
import com.groceryecom.modules.identity.web.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.web.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.web.dto.LoginRequest;
import com.groceryecom.modules.identity.web.dto.RefreshTokenRequest;
import com.groceryecom.modules.identity.web.dto.RegisterRequest;
import com.groceryecom.modules.identity.web.dto.UserResponse;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account registration, login and password management.
 * Served under the /api context path, so the public URL is /api/v1/auth.
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account")
    ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request), "Registered");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username or email and password")
    ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Exchange a refresh token for new tokens")
    ApiResponse<AuthTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Get the logged-in user's profile")
    ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(authService.getUser(user.id()));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Change the logged-in user's password")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request);
        return ApiResponse.ok(null, "Password changed");
    }
}
