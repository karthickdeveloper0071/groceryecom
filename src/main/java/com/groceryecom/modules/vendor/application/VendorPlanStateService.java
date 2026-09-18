package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.contract.VendorPlanState;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Applies what billing says about a licence to the store itself.
 *
 * <p>Package-private class behind the public {@link VendorPlanState} interface, so
 * billing can say "this store is paid up" without being able to touch anything else
 * about the vendor.
 *
 * <p>An automatic approval is audited exactly like an admin's, with the platform as the
 * actor. "Who let this store on?" must have an answer even when the answer is "a
 * payment did, at 03:14 on a Sunday".
 */
@Slf4j
@Service
class VendorPlanStateService implements VendorPlanState {

    private final VendorRepository vendors;
    private final AuditLog auditLog;

    VendorPlanStateService(VendorRepository vendors, AuditLog auditLog) {
        this.vendors = vendors;
        this.auditLog = auditLog;
    }

    @Override
    @Transactional
    public void planActivated(UUID vendorId, Instant expiresAt) {
        Vendor vendor = require(vendorId);

        if (vendor.planActivated(expiresAt)) {
            auditLog.vendorStatusChanged(null, vendorId, vendor.getStatus().name(),
                    "approved automatically on payment");
            log.info("Vendor {} started trading on a paid plan without an admin step", vendorId);
        }
        vendors.save(vendor);
    }

    @Override
    @Transactional
    public void planExpired(UUID vendorId, Instant expiredAt) {
        Vendor vendor = require(vendorId);

        vendor.planExpired(expiredAt);
        vendors.save(vendor);
        log.info("Vendor {} left the storefront: licence ended at {}", vendorId, expiredAt);
    }

    private Vendor require(UUID vendorId) {
        return vendors.findByPublicId(vendorId)
                .orElseThrow(() -> new NotFoundException("Vendor", vendorId));
    }
}
