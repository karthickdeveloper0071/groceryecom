package com.groceryecom.modules.identity.api;

import java.util.UUID;

/**
 * Published when a new account is created. Other modules react to it with
 * {@code @ApplicationModuleListener}; the publication is stored in the outbox
 * table in the same transaction as the user.
 */
public record UserRegisteredEvent(UUID userId, String email, Role role) {
}
