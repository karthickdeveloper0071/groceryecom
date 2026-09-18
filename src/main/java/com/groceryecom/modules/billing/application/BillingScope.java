package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.vendor.contract.VendorDirectory;
import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may spend a store's money.
 *
 * <p>Only an {@code OWNER} may buy, change or cancel a plan. Staff run the shop; they do
 * not commit the business to a monthly payment. That is a deliberately narrower rule
 * than the one on the store's profile, which staff may edit.
 *
 * <p>Billing asks the vendor module through its contract and never reads its tables, so
 * the dependency runs one way: billing knows about vendors, vendors know nothing about
 * billing. As in the vendor module, a caller with no membership gets 404 rather than
 * 403, so ids cannot be probed.
 */
@Slf4j
@Component
public class BillingScope {

    private final VendorDirectory vendors;

    BillingScope(VendorDirectory vendors) {
        this.vendors = vendors;
    }

    public void requireOwner(AuthenticatedUser user, UUID vendorId) {
        VendorMemberRole role = vendors.membershipOf(user.id(), vendorId)
                .orElseThrow(() -> {
                    log.info("User {} has no membership for vendor {}", user.id(), vendorId);
                    return new NotFoundException("Vendor", vendorId);
                });

        if (role != VendorMemberRole.OWNER) {
            log.info("User {} is {} of vendor {} and may not manage its plan", user.id(), role, vendorId);
            throw new NotFoundException("Vendor", vendorId);
        }
    }
}
