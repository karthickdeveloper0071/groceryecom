package com.groceryecom.modules.vendor.application;

import com.groceryecom.modules.vendor.api.dto.RegisterVendorRequest;
import com.groceryecom.modules.vendor.api.dto.VendorResponse;
import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.modules.vendor.contract.VendorRegisteredEvent;
import com.groceryecom.modules.vendor.domain.Vendor;
import com.groceryecom.modules.vendor.domain.VendorMember;
import com.groceryecom.modules.vendor.domain.VendorMemberRepository;
import com.groceryecom.modules.vendor.domain.VendorRepository;
import com.groceryecom.modules.vendor.mapper.VendorMapper;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.ConflictException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * A logged-in user applies to open a store.
 *
 * <p>Business rules:
 * <ul>
 *   <li>The store starts {@code PENDING}. Nothing may be sold until an admin approves
 *       it, so anyone can apply without that being a risk.</li>
 *   <li>The applicant becomes the store's {@code OWNER}. That membership, not the
 *       account's platform role, is what grants access to the store's data.</li>
 *   <li>One store per account for now: a second store means staff, switching between
 *       stores and per-store permissions, which is a feature rather than a side effect
 *       of applying twice.</li>
 *   <li>The storefront address is unique, and fixed from here on.</li>
 * </ul>
 */
@Slf4j
@Service
public class RegisterVendorService {

    private final VendorRepository vendors;
    private final VendorMemberRepository members;
    private final ApplicationEventPublisher events;
    private final AuditLog auditLog;

    RegisterVendorService(VendorRepository vendors, VendorMemberRepository members,
                          ApplicationEventPublisher events, AuditLog auditLog) {
        this.vendors = vendors;
        this.members = members;
        this.events = events;
        this.auditLog = auditLog;
    }

    @Transactional
    public VendorResponse execute(AuthenticatedUser applicant, RegisterVendorRequest request) {
        if (members.existsByUserPublicId(applicant.id())) {
            throw new ConflictException("This account already belongs to a store", "VENDOR_ALREADY_OWNED");
        }

        String slug = VendorSlug.normalize(request.slug());
        if (vendors.existsBySlug(slug)) {
            throw new ConflictException("Store address already taken", "VENDOR_SLUG_EXISTS");
        }

        Vendor vendor = Vendor.apply(slug, request.legalName().trim(), request.displayName().trim(),
                request.contactEmail().trim().toLowerCase(Locale.ROOT), request.contactPhone());

        Vendor saved = save(vendor);
        members.save(VendorMember.of(saved, applicant.id(), VendorMemberRole.OWNER));

        auditLog.vendorRegistered(applicant.id(), saved.getPublicId());
        events.publishEvent(new VendorRegisteredEvent(
                saved.getPublicId(), applicant.id(), saved.getDisplayName()));

        return VendorMapper.toResponse(saved);
    }

    /**
     * The slug check above cannot stop two applications for the same address arriving
     * together: both can pass it before either inserts. The unique constraint is the
     * real guard, so the one that loses gets the same 409 it would have got a moment
     * earlier instead of a 500.
     */
    private Vendor save(Vendor vendor) {
        try {
            return vendors.saveAndFlush(vendor);
        } catch (DataIntegrityViolationException e) {
            log.info("Vendor registration lost a race on the slug");
            throw new ConflictException("Store address already taken", "VENDOR_SLUG_EXISTS");
        }
    }
}
