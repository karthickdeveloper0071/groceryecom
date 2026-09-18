package com.groceryecom.modules.vendor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An application to open a store.
 *
 * <p>No status field: a store cannot apply as approved. The owner is the authenticated
 * caller, so there is no user field to tamper with either.
 *
 * @param slug         storefront address label, {@code <slug>.groceryecom.com}. Fixed once issued
 * @param legalName    the registered business, used on invoices and for payouts
 * @param displayName  what customers see
 * @param contactEmail where the platform reaches the store about orders and payouts
 * @param contactPhone optional
 */
public record RegisterVendorRequest(
        @NotBlank
        @Size(min = 3, max = 63)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9-]*[A-Za-z0-9]$",
                message = "may contain only letters, digits and hyphens, and must not start or end with a hyphen")
        String slug,

        @NotBlank @Size(max = 255)
        String legalName,

        @NotBlank @Size(min = 2, max = 120)
        String displayName,

        @NotBlank @Email @Size(max = 255)
        String contactEmail,

        @Size(max = 20)
        String contactPhone) {
}
