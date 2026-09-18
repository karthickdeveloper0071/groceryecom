package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorMember;
import com.groceryecom.modules.vendor.domain.VendorMemberRepository;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.modules.vendor.mapper.VendorMapper;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reading a store, from the two sides that are allowed to.
 *
 * <p>The public view and the members' view are separate methods on purpose. A customer
 * may see an approved store, and must not learn that a pending or rejected application
 * exists; a member sees their own store whatever its status, because they need to know
 * it was rejected and why.
 */
@Service
public class GetVendorService {

    private final VendorRepository vendors;
    private final VendorMemberRepository members;

    GetVendorService(VendorRepository vendors, VendorMemberRepository members) {
        this.vendors = vendors;
        this.members = members;
    }

    /**
     * The storefront view: approved stores only, and no contact details, because this
     * is served to anonymous callers.
     *
     * <p>A pending, rejected or suspended store returns 404 rather than 403, so trying
     * ids tells an outsider nothing.
     */
    @Transactional(readOnly = true)
    public VendorResponse publicProfile(UUID vendorId) {
        Vendor vendor = vendors.findByPublicId(vendorId)
                .filter(Vendor::isSellable)
                .orElseThrow(() -> new NotFoundException("Vendor", vendorId));

        return VendorMapper.toPublicResponse(vendor);
    }

    /** The store the caller belongs to, whatever its status, with its contact details. */
    @Transactional(readOnly = true)
    public List<VendorResponse> myVendors(AuthenticatedUser user) {
        return members.findByUserPublicId(user.id()).stream()
                .map(VendorMember::getVendor)
                .map(VendorMapper::toResponse)
                .toList();
    }
}
