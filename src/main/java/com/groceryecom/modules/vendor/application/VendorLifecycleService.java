package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.contract.VendorStatusChangedEvent;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.modules.vendor.mapper.VendorMapper;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Admin decisions about a store: approve it, refuse it, or stop it trading.
 *
 * <p>These are the only ways a status changes. Each one is recorded in the audit log
 * with the admin who did it and the reason given, because "who let this vendor on to
 * the platform?" and "why was this store suspended?" are asked after the fact, often
 * by somebody who was not there.
 *
 * <p>Whether the caller is an admin is decided in the controller by role, not here.
 * The transitions themselves belong to {@link Vendor}, so an impossible one (approving
 * a rejected store) fails the same way whoever asks.
 */
@Slf4j
@Service
public class VendorLifecycleService {

    private final VendorRepository vendors;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    VendorLifecycleService(VendorRepository vendors, ApplicationEventPublisher events, AuditLog auditLog) {
        this.vendors = vendors;
        this.events = events;
        this.auditLog = auditLog;
    }

    @Transactional
    public VendorResponse approve(AuthenticatedUser admin, UUID vendorId) {
        Vendor vendor = require(vendorId);
        vendor.approve();
        return recordAndRespond(admin, vendor, null);
    }

    @Transactional
    public VendorResponse reject(AuthenticatedUser admin, UUID vendorId, String reason) {
        Vendor vendor = require(vendorId);
        vendor.reject(reason);
        return recordAndRespond(admin, vendor, reason);
    }

    @Transactional
    public VendorResponse suspend(AuthenticatedUser admin, UUID vendorId, String reason) {
        Vendor vendor = require(vendorId);
        vendor.suspend(reason);
        return recordAndRespond(admin, vendor, reason);
    }

    private Vendor require(UUID vendorId) {
        return vendors.findByPublicId(vendorId)
                .orElseThrow(() -> new NotFoundException("Vendor", vendorId));
    }

    private VendorResponse recordAndRespond(AuthenticatedUser admin, Vendor vendor, String reason) {
        Vendor saved = vendors.save(vendor);

        auditLog.vendorStatusChanged(admin.id(), saved.getPublicId(), saved.getStatus().name(), reason);
        // Catalog will hide or restore the store's products on this; notification will
        // tell the vendor. Written to the outbox in this transaction, so a listener
        // that fails cannot undo the decision.
        events.publishEvent(new VendorStatusChangedEvent(saved.getPublicId(), saved.getStatus(), reason));

        log.info("Vendor {} is now {}", saved.getPublicId(), saved.getStatus());
        return VendorMapper.toResponse(saved);
    }
}
