package com.groceryecom.modules.billing.api;

import com.groceryecom.modules.billing.api.dto.AdminPaymentResponse;
import com.groceryecom.modules.billing.api.dto.AdminSubscriptionResponse;
import com.groceryecom.modules.billing.application.AdminBillingService;
import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.modules.billing.domain.SubscriptionPayment.PaymentStatus;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import com.groceryecom.shared.web.PageRequestParams;
import com.groceryecom.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Licences and charges, for the admin console.
 */
@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Admin", description = "Platform configuration and operational lists")
class AdminBillingController {

    private final AdminBillingService adminBillingService;

    AdminBillingController(AdminBillingService adminBillingService) {
        this.adminBillingService = adminBillingService;
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Licences, for the admin console",
            description = "Filter by status to find stores that are past due or expired.")
    ApiResponse<PageResponse<AdminSubscriptionResponse>> subscriptions(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestParams.DEFAULT_SIZE) int size) {

        return ApiResponse.ok(adminBillingService.subscriptions(status, page, size));
    }

    @GetMapping("/payments")
    @Operation(summary = "Subscription charges, for the admin console",
            description = "Filter by PENDING for the bank transfers waiting to be confirmed, "
                    + "oldest first. Confirm one with POST /v1/billing/payments/{reference}/confirm.")
    ApiResponse<PageResponse<AdminPaymentResponse>> payments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestParams.DEFAULT_SIZE) int size) {

        return ApiResponse.ok(adminBillingService.payments(status, page, size));
    }
}
