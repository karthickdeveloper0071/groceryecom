package com.groceryecom.modules.vendor.api;

import com.groceryecom.modules.vendor.api.dto.RegisterVendorRequest;
import com.groceryecom.modules.vendor.api.dto.UpdateVendorRequest;
import com.groceryecom.modules.vendor.api.dto.VendorDecisionRequest;
import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.application.GetVendorService;
import com.groceryecom.modules.vendor.application.RegisterVendorService;
import com.groceryecom.modules.vendor.application.UpdateVendorProfileService;
import com.groceryecom.modules.vendor.application.VendorLifecycleService;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.platform.web.OpenApiConfig;
import com.groceryecom.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Stores: applying to open one, managing your own, and the admin decisions about them.
 * Served under the /api context path, so the public URL is /api/v1/vendors.
 *
 * <p>Thin by design: read the request, call one application service, return its result.
 *
 * <p>Two kinds of authorisation appear here, and they are not interchangeable. The
 * admin endpoints are guarded by role with {@code @PreAuthorize}. The vendor endpoints
 * are guarded by <b>membership</b>, inside the service, through {@code VendorScope} -
 * a role cannot express "this store and no other", which is the rule that keeps 100
 * vendors apart.
 */
@RestController
@RequestMapping("/v1/vendors")
@Tag(name = "Vendors", description = "Store registration, store profile and admin decisions")
class VendorController {

    private final RegisterVendorService registerVendorService;
    private final GetVendorService getVendorService;
    private final UpdateVendorProfileService updateVendorProfileService;
    private final VendorLifecycleService vendorLifecycleService;

    VendorController(RegisterVendorService registerVendorService, GetVendorService getVendorService,
                     UpdateVendorProfileService updateVendorProfileService,
                     VendorLifecycleService vendorLifecycleService) {
        this.registerVendorService = registerVendorService;
        this.getVendorService = getVendorService;
        this.updateVendorProfileService = updateVendorProfileService;
        this.vendorLifecycleService = vendorLifecycleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Apply to open a store",
            description = "The store starts PENDING and cannot sell until an admin approves it. "
                    + "The caller becomes its owner. One store per account.")
    ApiResponse<VendorResponse> register(@AuthenticationPrincipal AuthenticatedUser user,
                                         @Valid @RequestBody RegisterVendorRequest request) {
        return ApiResponse.ok(registerVendorService.execute(user, request),
                "Store registered. An admin will review it.");
    }

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "The stores the caller acts for",
            description = "Includes a store that is still pending or was rejected, with the reason.")
    ApiResponse<List<VendorResponse>> myVendors(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(getVendorService.myVendors(user));
    }

    @GetMapping("/{vendorId}")
    @Operation(summary = "A store's public profile",
            description = "Approved stores only. Anything else returns 404, including a store that "
                    + "exists but is pending or suspended.")
    ApiResponse<VendorResponse> publicProfile(@PathVariable UUID vendorId) {
        return ApiResponse.ok(getVendorService.publicProfile(vendorId));
    }

    @PatchMapping("/{vendorId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Update your store's profile",
            description = "Owners only, and only their own store. Fields left out are unchanged.")
    ApiResponse<VendorResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                       @PathVariable UUID vendorId,
                                       @Valid @RequestBody UpdateVendorRequest request) {
        return ApiResponse.ok(updateVendorProfileService.execute(user, vendorId, request), "Store updated");
    }

    @PostMapping("/{vendorId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Approve a store so it can sell (admin)",
            description = "Allowed for a pending application and for restoring a suspended store.")
    ApiResponse<VendorResponse> approve(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @PathVariable UUID vendorId) {
        return ApiResponse.ok(vendorLifecycleService.approve(admin, vendorId), "Store approved");
    }

    @PostMapping("/{vendorId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Refuse an application (admin)",
            description = "Only a pending application can be rejected, and the reason is shown to the vendor.")
    ApiResponse<VendorResponse> reject(@AuthenticationPrincipal AuthenticatedUser admin,
                                       @PathVariable UUID vendorId,
                                       @Valid @RequestBody VendorDecisionRequest request) {
        return ApiResponse.ok(vendorLifecycleService.reject(admin, vendorId, request.reason()), "Store rejected");
    }

    @PostMapping("/{vendorId}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    @Operation(summary = "Stop a store trading (admin)",
            description = "The store disappears from the storefront. Approve it again to restore it.")
    ApiResponse<VendorResponse> suspend(@AuthenticationPrincipal AuthenticatedUser admin,
                                        @PathVariable UUID vendorId,
                                        @Valid @RequestBody VendorDecisionRequest request) {
        return ApiResponse.ok(vendorLifecycleService.suspend(admin, vendorId, request.reason()), "Store suspended");
    }
}
