package com.groceryecom.platform.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes secrets from text before it reaches a log file.
 *
 * <p>Logging a failed request's body is the fastest way to see what a client actually
 * sent, but a request body can hold a password or a card number, and logs are copied
 * into places with weaker access control than the database. So the values of known
 * sensitive fields are replaced before anything is written.
 *
 * <p>The list is deny-based on purpose: a new sensitive field must be added here. When
 * you add a field to a request that holds a secret, add its name to {@link #SECRET_FIELDS}
 * in the same change.
 */
public final class SensitiveData {

    private static final String MASK = "\"***\"";

    private static final String SECRET_FIELDS = String.join("|",
            "password", "oldPassword", "newPassword", "confirmPassword",
            "token", "accessToken", "refreshToken", "resetToken",
            // Payment gateway keys an admin pastes: the one request that carries a live
            // secret, so its exact field names have to be here
            "secret", "clientSecret", "apiKey", "keySecret", "webhookSecret",
            "cardNumber", "cvv", "pin", "otp");

    /** Matches "field": <any JSON scalar>, including numbers, null and strings with escapes. */
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(\"(?:" + SECRET_FIELDS + ")\"\\s*:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|-?[0-9.]+|true|false|null)",
            Pattern.CASE_INSENSITIVE);

    private SensitiveData() {
    }

    /**
     * @param json request body, possibly invalid JSON
     * @return the same text with the values of sensitive fields replaced by {@code "***"}
     */
    public static String mask(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Matcher matcher = SECRET_VALUE.matcher(json);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(matcher.group(1) + MASK));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    /**
     * Masks and shortens a body for a single log line.
     *
     * @param maxLength characters kept before truncation
     */
    public static String maskAndTruncate(String json, int maxLength) {
        String masked = mask(json).replaceAll("\\s+", " ").trim();
        return masked.length() <= maxLength ? masked : masked.substring(0, maxLength) + "...[truncated]";
    }
}
