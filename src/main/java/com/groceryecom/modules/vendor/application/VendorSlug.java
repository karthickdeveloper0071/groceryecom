package com.groceryecom.modules.vendor.application;

import com.groceryecom.shared.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The storefront address label: {@code <slug>.groceryecom.com}.
 *
 * <p>It ends up in a host name, a CORS origin pattern and every customer-facing URL for
 * that store, so it is normalised once, here, and never changed afterwards. The same
 * rules are repeated as CHECK constraints in the migration, because the database is
 * what protects the format when a row arrives another way.
 */
final class VendorSlug {

    // A DNS label: letters and digits, single hyphens inside, 3 to 63 characters
    private static final Pattern VALID = Pattern.compile("^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])?$");
    private static final Pattern REPEATED_HYPHEN = Pattern.compile("-{2,}");
    private static final int MIN_LENGTH = 3;

    /** Reserved because they are, or will be, hosts of our own. */
    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "www", "api", "admin", "app", "mail", "static", "cdn", "assets", "status", "shop");

    private VendorSlug() {
    }

    static String normalize(String raw) {
        String slug = REPEATED_HYPHEN.matcher(raw.trim().toLowerCase(Locale.ROOT)).replaceAll("-");

        if (slug.length() < MIN_LENGTH || !VALID.matcher(slug).matches()) {
            throw new ValidationException(
                    "Store address must be %d-63 characters of lower-case letters, digits and hyphens"
                            .formatted(MIN_LENGTH));
        }
        if (RESERVED.contains(slug)) {
            throw new ValidationException("Store address '%s' is reserved".formatted(slug));
        }
        return slug;
    }
}
