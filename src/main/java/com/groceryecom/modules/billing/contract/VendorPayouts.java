package com.groceryecom.modules.billing.contract;

import com.groceryecom.shared.money.Money;

import java.util.Optional;
import java.util.UUID;

/**
 * How a customer's payment is divided, and where the vendor's share goes.
 *
 * <p>This is what the order and payment modules will call at checkout: one question,
 * asked once, answered by the module that knows the vendor's plan and the vendor's
 * payout destination. Neither of those facts is theirs to hold.
 *
 * <p>The commission comes from the plan the store is on, so a vendor who upgrades pays a
 * lower rate from the next order, with nothing to redeploy.
 */
public interface VendorPayouts {

    /**
     * How to divide {@code orderTotal} between the platform and the store.
     *
     * @throws com.groceryecom.shared.exception.PaymentRequiredException when the store's
     *         licence does not permit trading. A store that cannot sell cannot be paid
     */
    PaymentSplit splitFor(UUID vendorId, Money orderTotal);

    /** Where the store's share may be sent, or empty when nowhere may receive it yet. */
    Optional<PayoutDestination> destinationFor(UUID vendorId);

    /**
     * One customer payment, divided.
     *
     * <p>{@code vendorShare} is what is left after the commission, computed by
     * subtraction rather than by a second percentage: the two parts then always add up
     * to exactly what the customer paid, whatever the rounding. A split that loses a sen
     * per order loses real money at a million orders, and nobody can say where it went.
     *
     * @param orderTotal            what the customer pays
     * @param platformCommission    the platform's cut, rounded down to the minor unit
     * @param vendorShare           orderTotal minus the commission
     * @param commissionBasisPoints the rate applied: 250 = 2.5%
     * @param destination           where the vendor's share goes, or empty while the
     *                              store has no verified account. The money is still
     *                              owed - it is held rather than sent
     */
    record PaymentSplit(Money orderTotal, Money platformCommission, Money vendorShare,
                        int commissionBasisPoints, Optional<PayoutDestination> destination) {

        /** False means take the payment but hold the vendor's share until they are verified. */
        public boolean payableNow() {
            return destination.isPresent();
        }
    }

    /**
     * A verified place to send money.
     *
     * @param providerAccountId the gateway's id for the destination. Never a bank account
     *                          number: the platform does not store one
     * @param accountLast4      so a human can recognise the account in a payout report
     */
    record PayoutDestination(String provider, String providerAccountId, String accountLast4) {
    }
}
