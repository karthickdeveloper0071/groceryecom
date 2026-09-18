package com.groceryecom.modules.vendor.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * A partial update of what a store shows and how the platform reaches it. A null field
 * means "leave it alone", so a client can send only what changed.
 *
 * <p>The storefront address, the legal name and the status are not here: the first is
 * fixed once URLs depend on it, the second belongs to onboarding and payouts, and the
 * third is an admin decision.
 */
public record UpdateVendorRequest(
        @Size(min = 2, max = 120)
        String displayName,

        @Email @Size(max = 255)
        String contactEmail,

        @Size(max = 20)
        String contactPhone) {
}
