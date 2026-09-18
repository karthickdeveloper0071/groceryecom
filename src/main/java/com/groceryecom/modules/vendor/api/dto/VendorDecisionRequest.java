package com.groceryecom.modules.vendor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Why an admin refused or stopped a store. Required, and shown to the vendor: a
 * rejection nobody can explain becomes a support ticket, and later an argument.
 */
public record VendorDecisionRequest(
        @NotBlank @Size(min = 10, max = 500)
        String reason) {
}
