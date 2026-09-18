package com.groceryecom.modules.identity.api;

import com.groceryecom.modules.identity.api.dto.AuthTokenResponse;
import com.groceryecom.modules.identity.api.dto.ChangePasswordRequest;
import com.groceryecom.modules.identity.api.dto.LoginRequest;
import com.groceryecom.modules.identity.api.dto.RefreshTokenRequest;
import com.groceryecom.modules.identity.api.dto.RegisterRequest;
import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.application.ChangePasswordService;
import com.groceryecom.modules.identity.application.GetUserService;
import com.groceryecom.modules.identity.application.LoginService;
import com.groceryecom.modules.identity.application.RefreshTokenService;
import com.groceryecom.modules.identity.application.RegisterCustomerService;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
 *
 * <p>Thin by design: validate the request, call one application service, return its
 * result. Business rules live in {@code modules.identity.application}.
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, tokens and passwords")
class AuthController {

    private final RegisterCustomerService registerCustomerService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final GetUserService getUserService;
    private final ChangePasswordService changePasswordService;

    AuthController(RegisterCustomerService registerCustomerService, LoginService loginService,
                   RefreshTokenService refreshTokenService, GetUserService getUserService,
                   ChangePasswordService changePasswordService) {
        this.registerCustomerService = registerCustomerService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.getUserService = getUserService;
        this.changePasswordService = changePasswordService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer account",
            description = "Always creates a CUSTOMER. Returns tokens, so the caller is logged in.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
            description = "USERNAME_EXISTS or EMAIL_EXISTS"))
    ApiResponse<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(registerCustomerService.execute(request), "Registered");
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with username or email and password")
    ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(loginService.execute(request));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Exchange a refresh token for a new token pair")
    ApiResponse<AuthTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(refreshTokenService.execute(request.refreshToken()));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Get the logged-in user's profile")
    ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(getUserService.execute(user.id()));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Change the logged-in user's password")
    ApiResponse<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody ChangePasswordRequest request) {
        changePasswordService.execute(user.id(), request);
        return ApiResponse.ok(null, "Password changed");
    }
}
