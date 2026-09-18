package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.contract.VendorDirectory;
import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorMember;
import com.groceryecom.modules.vendor.domain.VendorMemberRepository;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The vendor module's answers to the rest of the platform.
 *
 * <p>Package-private class behind a public interface: catalog, inventory and order get
 * {@link VendorDirectory}, and nothing else of this module. They cannot reach the
 * entity, the repository or the table, so the vendor module can change all three
 * without a coordinated release.
 */
@Service
class VendorDirectoryService implements VendorDirectory {

    private final VendorRepository vendors;
    private final VendorMemberRepository members;

    VendorDirectoryService(VendorRepository vendors, VendorMemberRepository members) {
        this.vendors = vendors;
        this.members = members;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSellable(UUID vendorId) {
        return vendors.findByPublicId(vendorId)
                .filter(Vendor::isSellable)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VendorMemberRole> membershipOf(UUID userId, UUID vendorId) {
        return members.findByVendorPublicIdAndUserPublicId(vendorId, userId)
                .map(VendorMember::getMemberRole);
    }
}
