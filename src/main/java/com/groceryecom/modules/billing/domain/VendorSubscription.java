package com.groceryecom.modules.billing.domain;

import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.shared.exception.ConflictException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One store's licence. The rules about when it starts, ends and comes back live here,
 * because they are the same whoever asks: a vendor paying, an admin fixing something,
 * or the job that runs at night.
 *
 * <p>Renewal extends this row rather than adding another, so "is this store paid up?"
 * has exactly one answer. What was charged, and when, is in {@code subscription_payments}.
 */
@Entity
@Table(name = "vendor_subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class VendorSubscription extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    // The vendor module's public id; billing does not reach into its tables
    @Column(name = "vendor_public_id", nullable = false, unique = true, updatable = false)
    private UUID vendorPublicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    /** When trading actually stops: the period end plus the platform's grace days. */
    @Column(name = "grace_until", nullable = false)
    private Instant graceUntil;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * A store choosing a plan for the first time.
     *
     * <p>A plan with trial days starts trading immediately and pays later; without
     * them the store waits for the money to arrive. That difference is the whole of
     * "can I try it before I pay", and it belongs to the plan, not to the checkout.
     */
    public static VendorSubscription start(UUID vendorId, Plan plan, Instant now, Duration grace) {
        VendorSubscription subscription = new VendorSubscription();
        subscription.vendorPublicId = vendorId;
        subscription.plan = plan;
        subscription.currentPeriodStart = now;

        if (plan.hasTrial()) {
            subscription.status = SubscriptionStatus.TRIALING;
            subscription.currentPeriodEnd = plan.trialEndFrom(now);
        } else {
            subscription.status = SubscriptionStatus.PENDING_PAYMENT;
            // Nothing is granted until payment: the period is empty until then
            subscription.currentPeriodEnd = now;
        }
        subscription.graceUntil = subscription.currentPeriodEnd.plus(grace);
        return subscription;
    }

    /**
     * Money arrived: the licence runs for another period.
     *
     * <p>A renewal that arrives before the period ends extends from the end date, so
     * paying early never costs the store the days it already paid for. One that arrives
     * after expiry starts from today, because nobody should pay for a month they spent
     * switched off.
     */
    public void paymentReceived(Instant now, Duration grace) {
        Instant from = currentPeriodEnd.isAfter(now) && status.permitsTrading() ? currentPeriodEnd : now;

        this.currentPeriodStart = now;
        this.currentPeriodEnd = plan.periodEndFrom(from);
        this.graceUntil = currentPeriodEnd.plus(grace);
        this.status = SubscriptionStatus.ACTIVE;
        this.cancelledAt = null;
    }

    /** Moving to another plan. Takes effect when the next payment arrives. */
    public void switchTo(Plan newPlan, Duration grace) {
        if (newPlan.getId().equals(plan.getId()) && status.permitsTrading()) {
            throw new ConflictException("The store is already on this plan", "SUBSCRIPTION_PLAN_UNCHANGED");
        }
        this.plan = newPlan;
        if (!status.permitsTrading()) {
            this.status = SubscriptionStatus.PENDING_PAYMENT;
            this.graceUntil = currentPeriodEnd.plus(grace);
        }
    }

    /**
     * The period ended without payment. The store keeps trading through its grace days,
     * and is told why.
     */
    public void periodEnded() {
        if (status == SubscriptionStatus.TRIALING || status == SubscriptionStatus.ACTIVE) {
            // A store that asked to leave is not chased for payment: it simply ends when
            // the period it paid for does
            this.status = cancelledAt != null ? SubscriptionStatus.CANCELLED : SubscriptionStatus.PAST_DUE;
        }
    }

    /** Grace is over. The store stops trading until it pays. */
    public void expire() {
        if (status.permitsTrading()) {
            this.status = SubscriptionStatus.EXPIRED;
        }
    }

    /**
     * The vendor ends the licence. It keeps working until the period they already paid
     * for runs out; taking it away the same day would be keeping their money for
     * nothing.
     */
    public void cancel(Instant now) {
        if (status == SubscriptionStatus.CANCELLED) {
            throw new ConflictException("This plan is already cancelled", "SUBSCRIPTION_ALREADY_CANCELLED");
        }
        this.cancelledAt = now;
        if (!status.permitsTrading()) {
            this.status = SubscriptionStatus.CANCELLED;
        }
    }

    /** True while the store may sell. */
    public boolean permitsTrading() {
        return status.permitsTrading();
    }

    /** When trading stops unless something is paid. */
    public Instant accessEndsAt() {
        return graceUntil;
    }

    public boolean isPastDueAt(Instant now) {
        return now.isAfter(currentPeriodEnd);
    }

    public boolean isOutOfGraceAt(Instant now) {
        return now.isAfter(graceUntil);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
