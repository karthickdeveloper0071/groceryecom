package com.groceryecom.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Choosing a plan. The code, not a price: what a store pays is the platform's decision,
 * and a request that could name an amount is a request that can name the wrong one.
 */
public record SubscribeRequest(
        @NotBlank @Size(max = 30)
        String planCode) {
}
