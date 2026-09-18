package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.UpdateVendorRequest;
import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.modules.vendor.mapper.VendorMapper;
import com.groceryecom.platform.security.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * A store edits what customers see about it.
 *
 * <p>Only the fields a vendor owns are editable. The storefront address is fixed once
 * issued (URLs and CORS origins depend on it), the legal name belongs to onboarding
 * and payouts, and the status belongs to admins - a vendor cannot approve itself by
 * sending a status field, because there is none to send.
 *
 * <p>Access is decided by {@link VendorScope}, never by the id in the request alone.
 */
@Service
public class UpdateVendorProfileService {

    private final VendorScope vendorScope;
    private final VendorRepository vendors;

    UpdateVendorProfileService(VendorScope vendorScope, VendorRepository vendors) {
        this.vendorScope = vendorScope;
        this.vendors = vendors;
    }

    @Transactional
    public VendorResponse execute(AuthenticatedUser user, UUID vendorId, UpdateVendorRequest request) {
        Vendor vendor = vendorScope.require(user, vendorId, VendorMemberRole.OWNER);

        if (request.displayName() != null) {
            vendor.setDisplayName(request.displayName().trim());
        }
        if (request.contactEmail() != null) {
            vendor.setContactEmail(request.contactEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (request.contactPhone() != null) {
            vendor.setContactPhone(request.contactPhone());
        }

        // Concurrent edits are caught by @Version on BaseEntity: the second write fails
        // with 409 CONCURRENT_MODIFICATION rather than overwriting the first.
        return VendorMapper.toResponse(vendors.save(vendor));
    }
}
