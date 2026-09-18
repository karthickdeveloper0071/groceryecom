package com.groceryecom.modules.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param username the username or the email address
 */
public record LoginRequest(
        @NotBlank @Size(max = 255) String username,
        @NotBlank @Size(max = 72) String password) {
}
