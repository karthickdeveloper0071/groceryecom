package com.groceryecom.modules.billing.api;

import com.groceryecom.modules.billing.api.dto.CheckoutResponse;
import com.groceryecom.modules.billing.api.dto.PayoutAccountResponse;
import com.groceryecom.modules.billing.api.dto.PlanResponse;
import com.groceryecom.modules.billing.api.dto.SavePayoutAccountRequest;
import com.groceryecom.modules.billing.api.dto.SubscribeRequest;
import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.application.CancelSubscriptionService;
import com.groceryecom.modules.billing.application.ConfirmSubscriptionPaymentService;
import com.groceryecom.modules.billing.application.GetSubscriptionService;
import com.groceryecom.modules.billing.application.SubscribeToPlanService;
import com.groceryecom.modules.billing.application.VendorPayoutService;
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
import java.util.UUID;

/**
 * Plans, a store's licence, and confirming a payment.
 * Served under the /api context path, so the public URL is /api/v1/...
 *
 * <p>Thin by design: read the request, call one application service, return its result.
 *
 * <p>Who may do what: the price list is public, because somebody deciding whether to
 * sell here should not have to sign up to see the price. A store's licence is its
 * owner's business and nobody else's, checked by membership inside the services.
 * Confirming a payment is an admin action while money arrives by bank transfer; when a
 * gateway is wired in, its webhook calls the same service with its own authentication.
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "Billing", description = "Vendor plans, licences and subscription payments")
class BillingController {

    private final GetSubscriptionService getSubscriptionService;
    private final SubscribeToPlanService subscribeToPlanService;
    private final CancelSubscriptionService cancelSubscriptionService;
    private final ConfirmSubscriptionPaymentService confirmPaymentService;
    private final VendorPayoutService vendorPayoutService;

    BillingController(GetSubscriptionService getSubscriptionService,
                      SubscribeToPlanService subscribeToPlanService,
                      CancelSubscriptionService cancelSubscriptionService,
                      ConfirmSubscriptionPaymentService confirmPaymentService,
                      VendorPayoutService vendorPayoutService) {
        this.getSubscriptionService = getSubscriptionService;
        this.subscribeToPlanService = subscribeToPlanService;
        this.cancelSubscriptionService = cancelSubscriptionService;
        this.confirmPaymentService = confirmPaymentService;
        this.vendorPayoutService = vendorPayoutService;
    }

    @GetMapping("/plans")
    @Operation(summary = "The plans a store can buy",
            description = "Public. Prices are given both in minor units and as a decimal.")
    ApiResponse<List<PlanResponse>> plans() {
        return ApiResponse.ok(getSubscriptionService.publicPlans());
    }

    @GetMapping("/vendors/{vendorId}/subscription")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "The store's licence",
            description = "Owner only. Carries the sentence to show the vendor about the state of "
                    + "their plan, including when it expired.")
    ApiResponse<SubscriptionResponse> subscription(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable UUID vendorId) {
        return ApiResponse.ok(getSubscriptionService.ofVendor(user, vendorId));
    }

    @PostMapping("/vendors/{vendorId}/subscription")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Choose or change the store's plan",
            description = "Owner only. A plan with a free trial starts the store selling immediately "
                    + "and returns no payment; otherwise the response says how to pay.")
    ApiResponse<CheckoutResponse> subscribe(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID vendorId,
                                            @Valid @RequestBody SubscribeRequest request) {
        CheckoutResponse response = subscribeToPlanService.execute(user, vendorId, request);
        return ApiResponse.ok(response, response.payment() == null
                ? "Your plan is active" : "Plan chosen. Complete the payment to start selling.");
    }

    @PostMapping("/vendors/{vendorId}/subscription/cancel")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Cancel the store's plan",
            description = "Owner only. The store keeps selling until the end of the period already "
                    + "paid for.")
    ApiResponse<SubscriptionResponse> cancel(@AuthenticationPrincipal AuthenticatedUser user,
                                             @PathVariable UUID vendorId) {
        return ApiResponse.ok(cancelSubscriptionService.execute(user, vendorId), "Plan cancelled");
    }

    @GetMapping("/vendors/{vendorId}/payout-account")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Where the store's earnings are paid",
            description = "Owner only. Shows the last four digits of the account and whether it is "
                    + "verified; the account number itself is not stored and cannot be returned.")
    ApiResponse<PayoutAccountResponse> payoutAccount(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @PathVariable UUID vendorId) {
        return ApiResponse.ok(vendorPayoutService.ofVendor(user, vendorId));
    }

    @PutMapping("/vendors/{vendorId}/payout-account")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Set where the store's earnings are paid",
            description = "Owner only. The bank details go to the payment provider and are never "
                    + "stored here. Sending new details replaces the destination and starts "
                    + "verification again; the store keeps selling meanwhile and its earnings are held.")
    ApiResponse<PayoutAccountResponse> savePayoutAccount(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @PathVariable UUID vendorId,
                                                         @Valid @RequestBody SavePayoutAccountRequest request) {
        return ApiResponse.ok(vendorPayoutService.submit(user, vendorId, request),
                "Payout account saved");
    }

    @PostMapping("/billing/payments/{reference}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Confirm a subscription payment (admin)",
            description = "Starts or extends the licence, and opens a store that was waiting for "
                    + "approval. Safe to call twice with the same reference: the second call "
                    + "changes nothing and returns the same licence.")
    ApiResponse<SubscriptionResponse> confirmPayment(@AuthenticationPrincipal AuthenticatedUser admin,
                                                     @PathVariable String reference) {
        return ApiResponse.ok(confirmPaymentService.execute(admin, reference), "Payment confirmed");
    }
}
