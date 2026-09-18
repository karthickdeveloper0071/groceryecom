package com.groceryecom.modules.billing.domain;

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
 * Where a store's share of a customer's payment goes.
 *
 * <p>Note what is <b>not</b> a field: the account number and the IFSC. They are sent to
 * the gateway when the destination is created and never stored, so this table cannot be
 * used to move anybody's money. What is kept is the gateway's id for the destination and
 * the last four digits, which let a vendor recognise their own account and identify
 * nothing by themselves.
 */
@Entity
@Table(name = "vendor_payout_accounts")
@Getter
@Setter
@NoArgsConstructor
public class VendorPayoutAccount extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "vendor_public_id", nullable = false, unique = true, updatable = false)
    private UUID vendorPublicId;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_account_id", length = 100)
    private String providerAccountId;

    @Column(name = "account_holder_name", nullable = false, length = 160)
    private String accountHolderName;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutAccountStatus status;

    @Column(name = "status_reason", length = 500)
    private String statusReason;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    public static VendorPayoutAccount submitted(UUID vendorId, String provider, String accountHolderName,
                                                String bankName, String accountLast4) {
        VendorPayoutAccount account = new VendorPayoutAccount();
        account.vendorPublicId = vendorId;
        account.provider = provider;
        account.accountHolderName = accountHolderName;
        account.bankName = bankName;
        account.accountLast4 = accountLast4;
        account.status = PayoutAccountStatus.PENDING_VERIFICATION;
        return account;
    }

    /**
     * Replaces the details, which always starts verification again. A changed bank
     * account is the classic takeover: an attacker with a vendor's session points the
     * money at themselves. Re-verification is what makes that visible before a payout
     * leaves.
     */
    public void replaceWith(String accountHolderName, String bankName, String accountLast4) {
        this.accountHolderName = accountHolderName;
        this.bankName = bankName;
        this.accountLast4 = accountLast4;
        this.providerAccountId = null;
        this.status = PayoutAccountStatus.PENDING_VERIFICATION;
        this.statusReason = null;
        this.verifiedAt = null;
    }

    public void verified(String providerAccountId, Instant when) {
        this.providerAccountId = providerAccountId;
        this.status = PayoutAccountStatus.VERIFIED;
        this.statusReason = null;
        this.verifiedAt = when;
    }

    public void rejected(String reason) {
        this.status = PayoutAccountStatus.REJECTED;
        this.statusReason = reason;
        this.verifiedAt = null;
    }

    /** True only when money may actually be sent here. */
    public boolean canReceiveMoney() {
        return status == PayoutAccountStatus.VERIFIED && providerAccountId != null;
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    public enum PayoutAccountStatus {

        /** Submitted, not yet confirmed by the gateway. The vendor's share is held. */
        PENDING_VERIFICATION,

        /** Confirmed. Money may be sent here. */
        VERIFIED,

        /** The gateway or an admin refused it; the reason is shown to the vendor. */
        REJECTED,

        /** Stopped by an admin, usually while a dispute or a takeover is investigated. */
        SUSPENDED
    }
}
