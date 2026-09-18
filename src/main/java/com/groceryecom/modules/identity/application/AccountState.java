package com.groceryecom.modules.identity.application;

import com.groceryecom.modules.identity.domain.User;
import com.groceryecom.shared.exception.UnauthorizedException;

/**
 * One rule, used by every use case that hands out or accepts tokens: a deactivated
 * or deleted account can neither log in nor refresh.
 */
final class AccountState {

    private AccountState() {
    }

    static void requireUsable(User user) {
        if (!Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new UnauthorizedException("User account is disabled");
        }
    }
}
