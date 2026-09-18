package com.groceryecom.modules.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param id the user's public ID
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String phoneNumber,
        String role,
        boolean emailVerified,
        Instant createdAt) {
}
