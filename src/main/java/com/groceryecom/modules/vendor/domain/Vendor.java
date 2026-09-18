package com.groceryecom.modules.vendor.domain;

import com.groceryecom.modules.vendor.contract.VendorStatus;
import com.groceryecom.shared.exception.ConflictException;
import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A store on the platform.
 *
 * <p>The status transitions live here rather than in a service, because they are the
 * entity's own rule: an approved store may be suspended, a rejected one is finished,
 * and nothing jumps straight from pending to suspended. A service that set the field
 * directly could break that; {@link #approve}, {@link #reject} and {@link #suspend}
 * cannot.
 */
@Entity
@Table(name = "vendors")
@Getter
@Setter
@NoArgsConstructor
public class Vendor extends BaseEntity {

    private static final long serialVersionUID = 1L;
    private static final String INVALID_TRANSITION = "INVALID_VENDOR_STATUS_TRANSITION";

    // Exposed outside the database instead of the sequential id
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    // Storefront host label: <slug>.groceryecom.com. Lower case, set once.
    @Column(name = "slug", nullable = false, unique = true, updatable = false, length = 63)
    private String slug;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    // Always stored in lower case
    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private VendorStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    /** A new application: pending until an admin decides. */
    public static Vendor apply(String slug, String legalName, String displayName,
                               String contactEmail, String contactPhone) {
        Vendor vendor = new Vendor();
        vendor.slug = slug;
        vendor.legalName = legalName;
        vendor.displayName = displayName;
        vendor.contactEmail = contactEmail;
        vendor.contactPhone = contactPhone;
        vendor.status = VendorStatus.PENDING;
        vendor.statusChangedAt = Instant.now();
        return vendor;
    }

    /** Lets the store trade. Allowed from PENDING (a decision) and SUSPENDED (a restore). */
    public void approve() {
        requireStatusIn("approve", VendorStatus.PENDING, VendorStatus.SUSPENDED);
        changeStatus(VendorStatus.APPROVED, null);
    }

    /** Refuses the application. Terminal: a rejected store does not come back. */
    public void reject(String reason) {
        requireStatusIn("reject", VendorStatus.PENDING);
        changeStatus(VendorStatus.REJECTED, reason);
    }

    /** Stops a trading store. Its products must disappear from the storefront. */
    public void suspend(String reason) {
        requireStatusIn("suspend", VendorStatus.APPROVED);
        changeStatus(VendorStatus.SUSPENDED, reason);
    }

    public boolean isSellable() {
        return status.isSellable();
    }

    private void changeStatus(VendorStatus next, String reason) {
        this.status = next;
        this.statusReason = reason;
        this.statusChangedAt = Instant.now();
    }

    private void requireStatusIn(String action, VendorStatus... allowed) {
        for (VendorStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        // 409, not 400: the request was well formed, the store is simply not in a state
        // where this makes sense, usually because somebody else already decided.
        throw new ConflictException(
                "Cannot %s a vendor that is %s".formatted(action, status), INVALID_TRANSITION);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
