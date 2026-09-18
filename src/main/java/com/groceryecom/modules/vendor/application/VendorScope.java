package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorMember;
import com.groceryecom.modules.vendor.domain.VendorMemberRepository;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The one place that answers "may this caller act for this store?".
 *
 * <p>With 100 vendors on one database, the thing that must never happen is one vendor
 * reading or changing another's data by putting a different id in a URL. Every
 * vendor-scoped request therefore goes through here, and gets back the {@link Vendor}
 * only if the caller has a membership for it. Services never load a vendor by id on
 * their own; that is the habit this class exists to remove.
 *
 * <p>A caller without a membership gets <b>404, not 403</b>. 403 would confirm that the
 * store exists, which lets an outsider map the platform's vendors by trying ids. The
 * caller cannot tell "no such store" from "not yours", which is the intent.
 *
 * <p>Admins are not given a way in here. They act through the admin endpoints, which
 * are authorised by role and audited, so an admin action is never indistinguishable
 * from the vendor's own.
 */
@Slf4j
@Component
public class VendorScope {

    private final VendorMemberRepository members;

    VendorScope(VendorMemberRepository members) {
        this.members = members;
    }

    /**
     * The store the caller may manage, or a 404.
     *
     * @param user         the authenticated caller
     * @param vendorId     the store's public id, as it arrived in the request
     * @param requiredRole the least privileged membership that may do this
     */
    @Transactional(readOnly = true)
    public Vendor require(AuthenticatedUser user, UUID vendorId, VendorMemberRole requiredRole) {
        VendorMember membership = members.findByVendorPublicIdAndUserPublicId(vendorId, user.id())
                .orElseThrow(() -> {
                    // Worth an audit trail: this is what probing for other vendors looks like
                    log.info("User {} has no membership for vendor {}", user.id(), vendorId);
                    return new NotFoundException("Vendor", vendorId);
                });

        if (!permits(membership.getMemberRole(), requiredRole)) {
            log.info("User {} is {} of vendor {}, which is not enough for this action",
                    user.id(), membership.getMemberRole(), vendorId);
            throw new NotFoundException("Vendor", vendorId);
        }
        return membership.getVendor();
    }

    /**
     * OWNER can do anything STAFF can. Kept as an explicit check rather than an
     * ordinal comparison, so adding a role cannot silently grant more than intended.
     */
    private static boolean permits(VendorMemberRole held, VendorMemberRole required) {
        return held == required || held == VendorMemberRole.OWNER;
    }
}
