package com.groceryecom.modules.vendor.api.dto;

import com.groceryecom.modules.vendor.contract.VendorStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A store as an API client sees it.
 *
 * <p>Two shapes come out of {@code VendorMapper}: the members' and admins' view, and
 * the storefront view, which leaves the contact details and the status reason null so
 * they are omitted from the JSON entirely. Null fields are not serialised.
 *
 * @param id            the store's public id, the one used in URLs
 * @param slug          storefront address label
 * @param legalName     null in the public view
 * @param displayName   what customers see
 * @param contactEmail  null in the public view
 * @param contactPhone  null in the public view
 * @param status        PENDING, APPROVED, REJECTED or SUSPENDED
 * @param statusReason  why it was rejected or suspended; null otherwise
 * @param registeredAt  when the application was made
 */
public record VendorResponse(
        UUID id,
        String slug,
        String legalName,
        String displayName,
        String contactEmail,
        String contactPhone,
        VendorStatus status,
        String statusReason,
        Instant registeredAt) {
}
