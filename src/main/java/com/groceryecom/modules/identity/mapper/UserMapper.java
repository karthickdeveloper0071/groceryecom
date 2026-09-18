package com.groceryecom.modules.identity.mapper;

import com.groceryecom.modules.identity.api.dto.UserResponse;
import com.groceryecom.modules.identity.domain.User;

/**
 * Keeps entities out of API responses: everything a client sees is built here.
 */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getCreatedAt());
    }
}
