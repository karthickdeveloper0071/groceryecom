package com.groceryecom.modules.vendor.domain;

import com.groceryecom.modules.vendor.contract.VendorStatus;
import com.groceryecom.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What may happen to a store, and what may not. These are the entity's own rules, so
 * they hold no matter which service, admin screen or script asks.
 */
class VendorStatusTransitionTest {

    private static final String REASON = "Documents do not match the registered business";

    @Test
    void aNewApplicationIsPendingAndCannotSell() {
        Vendor vendor = newApplication();

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.PENDING);
        assertThat(vendor.isSellable()).isFalse();
    }

    @Test
    void approvingAPendingApplicationLetsItSell() {
        Vendor vendor = newApplication();

        vendor.approve();

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.APPROVED);
        assertThat(vendor.isSellable()).isTrue();
        assertThat(vendor.getStatusChangedAt()).isNotNull();
    }

    @Test
    void aSuspendedStoreStopsSellingAndKeepsTheReason() {
        Vendor vendor = newApplication();
        vendor.approve();

        vendor.suspend(REASON);

        assertThat(vendor.getStatus()).isEqualTo(VendorStatus.SUSPENDED);
        assertThat(vendor.isSellable()).isFalse();
        assertThat(vendor.getStatusReason()).isEqualTo(REASON);
    }

    @Test
    void aSuspendedStoreCanBeRestored() {
        Vendor vendor = newApplication();
        vendor.approve();
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
