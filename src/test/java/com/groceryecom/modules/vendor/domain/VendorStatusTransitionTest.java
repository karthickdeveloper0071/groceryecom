package com.groceryecom.modules.vendor.domain;

import com.groceryecom.modules.vendor.contract.VendorStatus;
import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What may happen to a store, and what may not. These are the entity's own rules, so
 * they hold no matter which service, admin screen or script asks.
 */
class VendorStatusTransitionTest {

    private static final String REASON = "Documents do not match the registered business";
    private static final Instant EXPIRES = Instant.parse("2026-12-31T00:00:00Z");

    @Test
    void aNewApplicationIsPendingAndCannotSell() {
        Vendor vendor = newApplication();

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.PENDING);
        assertThat(vendor.isSellable()).isFalse();
    }

    /**
     * Approval is one of two gates. An approved store with no licence is a shop with the
     * lights on and the door locked, which is exactly what the platform wants until it
     * is paid.
     */
    @Test
    void approvalAloneDoesNotLetAStoreSell() {
        Vendor vendor = newApplication();

        vendor.approve();

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.APPROVED);
        assertThat(vendor.isSellable()).isFalse();
        assertThat(vendor.getStatusChangedAt()).isNotNull();
    }

    @Test
    void anApprovedStoreWithALicenceSells() {
        Vendor vendor = newApplication();
        vendor.approve();

        vendor.planActivated(EXPIRES);

        assertThat(vendor.isSellable()).isTrue();
        assertThat(vendor.getPlanExpiresAt()).isEqualTo(EXPIRES);
    }

    /** Paying is the approval: nobody has to click anything for a store to open. */
    @Test
    void aLicenceOpensAStoreThatWasStillWaitingForAnAdmin() {
        Vendor vendor = newApplication();

        boolean approvedByThis = vendor.planActivated(EXPIRES);

        assertThat(approvedByThis).isTrue();
        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.APPROVED);
        assertThat(vendor.isSellable()).isTrue();
    }

    /** A payment does not overturn an admin who closed a store. */
    @Test
    void aLicenceDoesNotReopenASuspendedStore() {
        Vendor vendor = newApplication();
        vendor.approve();
        vendor.planActivated(EXPIRES);
        vendor.suspend(REASON);

        boolean approvedByThis = vendor.planActivated(EXPIRES);

        assertThat(approvedByThis).isFalse();
        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.SUSPENDED);
        assertThat(vendor.isSellable()).isFalse();
    }

    @Test
    void aLicenceRunningOutTakesTheStoreOffTheStorefront() {
        Vendor vendor = newApplication();
        vendor.planActivated(EXPIRES);

        vendor.planExpired(EXPIRES);

        assertThat(vendor.isSellable()).isFalse();
        // Still approved: what ended is the licence, not the admin's decision
        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.APPROVED);
    }

    @Test
    void aSuspendedStoreStopsSellingAndKeepsTheReason() {
        Vendor vendor = newApplication();
        vendor.approve();
        vendor.planActivated(EXPIRES);

        vendor.suspend(REASON);

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.SUSPENDED);
        assertThat(vendor.isSellable()).isFalse();
        assertThat(vendor.getStatusReason()).isEqualTo(REASON);
    }

    @Test
    void aSuspendedStoreCanBeRestored() {
        Vendor vendor = newApplication();
        vendor.approve();
        vendor.planActivated(EXPIRES);
        vendor.suspend(REASON);

        vendor.approve();

        assertThat(vendor.isSellable()).isTrue();
        // The old reason must not linger on a store that is trading again
        assertThat(vendor.getStatusReason()).isNull();
    }

    @Test
    void aRejectedApplicationIsFinal() {
        Vendor vendor = newApplication();
        vendor.reject(REASON);

        assertThatThrownBy(vendor::approve)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("REJECTED");
    }

    /** A store that never traded cannot be suspended; an admin rejects it instead. */
    @Test
    void aPendingApplicationCannotBeSuspended() {
        assertThatThrownBy(() -> newApplication().suspend(REASON))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot suspend");
    }

    /** Refusing a store that is already trading would be a suspension, and reads differently. */
    @Test
    void aTradingStoreCannotBeRejected() {
        Vendor vendor = newApplication();
        vendor.approve();

        assertThatThrownBy(() -> vendor.reject(REASON))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot reject");
    }

    /** Two admins deciding at once: the second gets a conflict, not a silent overwrite. */
    @Test
    void approvingTwiceIsARejectedTransition() {
        Vendor vendor = newApplication();
        vendor.approve();

        assertThatThrownBy(vendor::approve)
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Cannot approve");
    }

    private static Vendor newApplication() {
        return Vendor.apply("fresh-mart", "Fresh Mart Sdn Bhd", "Fresh Mart",
                "owner@freshmart.example", "+60123456789");
    }
}
