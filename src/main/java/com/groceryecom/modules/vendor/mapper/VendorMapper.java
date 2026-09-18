package com.groceryecom.modules.vendor.mapper;

import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.domain.Vendor;

/**
 * Keeps entities out of API responses: everything a client sees is built here.
 */
public final class VendorMapper {

    private VendorMapper() {
    }

    /** For the store's own people and for admins: everything about it. */
    public static VendorResponse toResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getPublicId(),
                vendor.getSlug(),
                vendor.getLegalName(),
                vendor.getDisplayName(),
                vendor.getContactEmail(),
                vendor.getContactPhone(),
                vendor.getStatus(),
                vendor.getStatusReason(),
                vendor.isSellable(),
                vendor.getPlanExpiresAt(),
                vendor.getCreatedAt());
    }

    /**
     * For the storefront, which is served to anyone. The contact details and the legal
     * name are left out: they are business contact data, not shop-window content, and
     * publishing them would hand every scraper a vendor mailing list.
     */
    public static VendorResponse toPublicResponse(Vendor vendor) {
        return new VendorResponse(
                vendor.getPublicId(),
                vendor.getSlug(),
                null,
                vendor.getDisplayName(),
                null,
                null,
                vendor.getStatus(),
                null,
                // A store only reaches the public view when it can sell, and when its
                // licence runs out belongs between the store and the platform
                null,
                null,
                vendor.getCreatedAt());
    }
}
