package com.groceryecom.modules.billing.domain;

import com.groceryecom.shared.money.Money;
import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * One charge for a licence: what was asked for, and whether it arrived.
 *
 * <p>Rows are never rewritten to hide a failure. A store that paid late has a failed
 * attempt and a successful one, in that order, and both stay - the billing history is
 * the answer to "you charged me twice", and an answer nobody can edit is worth more
 * than a tidy table.
 */
@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPayment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false, updatable = false)
    private VendorSubscription subscription;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private Long amountMinor;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "provider", nullable = false, updatable = false, length = 30)
    private String provider;

    /**
     * The provider's id for this charge. Unique in the database, which is what makes a
     * webhook delivered twice harmless: the second one finds the payment already
     * settled and changes nothing.
     */
    @Column(name = "provider_reference", nullable = false, unique = true, updatable = false, length = 100)
    private String providerReference;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    public static SubscriptionPayment pending(VendorSubscription subscription, Money amount,
                                              String provider, String reference) {
        SubscriptionPayment payment = new SubscriptionPayment();
        payment.subscription = subscription;
        payment.amountMinor = amount.amountMinor();
        payment.currency = amount.currency().getCurrencyCode();
        payment.provider = provider;
        payment.providerReference = reference;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    /** @return false when this payment was already settled, so the caller does nothing twice */
    public boolean succeed(Instant paidAt) {
        if (status != PaymentStatus.PENDING) {
            return false;
        }
        this.status = PaymentStatus.SUCCEEDED;
        this.paidAt = paidAt;
        return true;
    }

    public boolean fail(String reason) {
        if (status != PaymentStatus.PENDING) {
            return false;
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
        return true;
    }

    public Money amount() {
        return Money.ofMinor(amountMinor, currency);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    public enum PaymentStatus {
        PENDING,
        SUCCEEDED,
        FAILED
    }
}
