package com.groceryecom.modules.vendor.api;

import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.application.AdminVendorService;
import com.groceryecom.modules.vendor.contract.VendorStatus;
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
 * The admin console's view of stores.
 *
 * <p>The list that makes the approve, reject and suspend endpoints usable: without it an
 * admin has no way to discover the application waiting for a decision.
 */
@RestController
@RequestMapping("/v1/admin/vendors")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Admin", description = "Platform configuration and operational lists")
class AdminVendorController {

    private final AdminVendorService adminVendorService;

    AdminVendorController(AdminVendorService adminVendorService) {
        this.adminVendorService = adminVendorService;
    }

    @GetMapping
    @Operation(summary = "Stores, for the admin console",
            description = "Filter by status to get the approval queue (PENDING, oldest first). "
                    + "Search matches the display name or the storefront address. "
                    + "Page size is capped at 100 however large a value is asked for.")
    ApiResponse<PageResponse<VendorResponse>> list(
            @RequestParam(required = false) VendorStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestParams.DEFAULT_SIZE) int size) {

        return ApiResponse.ok(adminVendorService.list(status, search, page, size));
    }
}
