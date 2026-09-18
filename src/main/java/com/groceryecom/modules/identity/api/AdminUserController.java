package com.groceryecom.modules.identity.api;

import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.application.AdminUserService;
import com.groceryecom.modules.identity.contract.Role;
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
 * Account lookup for the admin console. Read-only.
 */
@RestController
@RequestMapping("/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Tag(name = "Admin", description = "Platform configuration and operational lists")
class AdminUserController {

    private final AdminUserService adminUserService;

    AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Find an account",
            description = "Search matches the username or the email address. Returns the same "
                    + "profile the account holder sees: no password, no tokens.")
    ApiResponse<PageResponse<UserResponse>> search(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PageRequestParams.DEFAULT_SIZE) int size) {

        return ApiResponse.ok(adminUserService.search(role, search, page, size));
    }
}
