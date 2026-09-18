package com.groceryecom.modules.billing.api;

import com.groceryecom.modules.billing.api.dto.GatewayCredentialResponse;
import com.groceryecom.modules.billing.api.dto.SaveGatewayCredentialRequest;
import com.groceryecom.modules.billing.application.GatewayCredentialsService;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Where the platform owner installs the payment gateway keys, from the admin console.
 *
 * <p>The point of these three endpoints: signing up with Razorpay, rotating a key when
 * Razorpay asks, and swapping test keys for live ones on launch day are all things the
 * business does on its own schedule. None of them should need an engineer, a deployment
 * window, or a secret pasted into a config file that then lives in someone's shell
 * history.
 *
 * <p>Admin only, and audited. No response ever contains a secret - the console shows the
 * last four characters of the public key id, which is enough to tell which key is
 * installed.
 */
@RestController
@RequestMapping("/v1/admin/payment-gateways")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Admin", description = "Platform configuration")
class AdminGatewayController {

    private final GatewayCredentialsService credentials;

    AdminGatewayController(GatewayCredentialsService credentials) {
        this.credentials = credentials;
    }

    @GetMapping
    @Operation(summary = "Which payment gateways are installed",
            description = "Never returns a secret: the key id is shown as its last four "
                    + "characters, plus whether webhooks can be verified.")
    ApiResponse<List<GatewayCredentialResponse>> list() {
        return ApiResponse.ok(credentials.list());
    }

    @PutMapping("/{provider}")
    @Operation(summary = "Install or replace a gateway's keys",
            description = "Paste the Key Id and Key Secret from the Razorpay dashboard, and the "
                    + "webhook secret from its webhook settings. Takes effect on the next payment, "
                    + "with no restart. Leave webhookSecret out when rotating only the API key.")
    ApiResponse<GatewayCredentialResponse> save(@AuthenticationPrincipal AuthenticatedUser admin,
                                                @PathVariable String provider,
                                                @Valid @RequestBody SaveGatewayCredentialRequest request) {
        return ApiResponse.ok(credentials.save(admin, provider, request), "Payment gateway updated");
    }

    @PostMapping("/{provider}/disable")
    @Operation(summary = "Stop using a gateway without losing its keys",
            description = "Payments fall back to bank transfer confirmed by an admin. For a "
                    + "gateway outage, or for backing out of a launch.")
    ApiResponse<GatewayCredentialResponse> disable(@AuthenticationPrincipal AuthenticatedUser admin,
                                                   @PathVariable String provider) {
        return ApiResponse.ok(credentials.setEnabled(admin, provider, false), "Payment gateway disabled");
    }

    @PostMapping("/{provider}/enable")
    @Operation(summary = "Use a gateway that was switched off")
    ApiResponse<GatewayCredentialResponse> enable(@AuthenticationPrincipal AuthenticatedUser admin,
                                                  @PathVariable String provider) {
        return ApiResponse.ok(credentials.setEnabled(admin, provider, true), "Payment gateway enabled");
    }
}
