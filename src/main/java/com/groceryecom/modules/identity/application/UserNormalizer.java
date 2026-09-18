package com.groceryecom.modules.identity.application;

import java.util.Locale;

/**
 * Usernames and emails are stored and compared in lower case, so that "Alice" and
 * "alice" cannot become two accounts and login works in any letter case.
 * The database enforces the same rule with a CHECK constraint.
 */final class UserNormalizer {

    private UserNormalizer() {
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
