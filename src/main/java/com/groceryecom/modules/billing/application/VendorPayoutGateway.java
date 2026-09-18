package com.groceryecom.modules.billing.application;

import java.util.UUID;

/**
 * Registers a vendor's bank account with the payment provider, so the provider can pay
 * them directly.
 *
 * <p>A port, like {@link PaymentGateway}, and separate from it on purpose: taking a
 * customer's money and paying a vendor are two capabilities a provider may offer
 * independently. Razorpay Route does both; a provider that only does the first would
 * implement one interface and not the other.
 *
 * <p>The bank details are passed through and never returned. An implementation must not
 * store, log or echo them - the whole reason this call exists is so the application does
 * not hold them.
 */
public interface VendorPayoutGateway {

    String provider();

    /**
     * Creates the destination at the provider.
     *
     * @param accountNumber the vendor's bank account number. Passed through, never stored
     * @param ifscCode      the branch code, likewise
     * @return what the provider decided, including its id for the destination
     */
    Registration registerDestination(UUID vendorId, String accountHolderName, String accountNumber,
                                     String ifscCode, String contactEmail);

    /**
     * @param providerAccountId the provider's id for the destination; null when it refused
     * @param status            what the provider said
     * @param message           the reason, for a vendor to act on, when it refused
     */
    record Registration(String providerAccountId, RegistrationStatus status, String message) {

        public static Registration verified(String providerAccountId) {
            return new Registration(providerAccountId, RegistrationStatus.VERIFIED, null);
        }

        public static Registration pending(String providerAccountId) {
            return new Registration(providerAccountId, RegistrationStatus.PENDING, null);
        }

        public static Registration rejected(String message) {
            return new Registration(null, RegistrationStatus.REJECTED, message);
        }

        public boolean verified() {
            return status == RegistrationStatus.VERIFIED;
        }

        public boolean rejected() {
            return status == RegistrationStatus.REJECTED;
        }
    }

    enum RegistrationStatus {
        /** Usable now. */
        VERIFIED,
        /** The provider is still checking; the vendor's share is held until it is done. */
        PENDING,
        /** Refused, with a reason. */
        REJECTED
    }
}
