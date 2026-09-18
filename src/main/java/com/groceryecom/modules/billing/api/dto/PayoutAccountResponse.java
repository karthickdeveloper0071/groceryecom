package com.groceryecom.modules.billing.api.dto;

import com.groceryecom.modules.billing.domain.VendorPayoutAccount;
import com.groceryecom.modules.billing.domain.VendorPayoutAccount.PayoutAccountStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A store's payout destination, as its owner sees it.
 *
 * <p>There is no account number here, because there is none to return: the platform
 * stores the provider's id for the destination and the last four digits, and nothing
 * else. Four digits let a person recognise their own account and identify nobody.
 *
 * @param accountLast4 the last four digits of the account they registered
 * @param canReceive   whether money may be sent there yet
 * @param message      what to show the vendor about the state of their payout setup
 */
public record PayoutAccountResponse(
        UUID id,
        String provider,
        String accountHolderName,
        String bankName,
        String accountLast4,
        PayoutAccountStatus status,
        String statusReason,
        boolean canReceive,
        Instant verifiedAt,
        String message) {

    public static PayoutAccountResponse of(VendorPayoutAccount account) {
        return new PayoutAccountResponse(
                account.getPublicId(),
                account.getProvider(),
                account.getAccountHolderName(),
                account.getBankName(),
                account.getAccountLast4(),
                account.getStatus(),
                account.getStatusReason(),
                account.canReceiveMoney(),
                account.getVerifiedAt(),
                message(account));
    }

    private static String message(VendorPayoutAccount account) {
        return switch (account.getStatus()) {
            case PENDING_VERIFICATION -> "We are verifying the account ending %s. You can keep selling; "
                    .formatted(account.getAccountLast4())
                    + "your earnings are held until it is verified, then paid out.";
            case VERIFIED -> "Your earnings are paid to the account ending %s."
                    .formatted(account.getAccountLast4());
            case REJECTED -> "We could not verify the account ending %s. %s"
                    .formatted(account.getAccountLast4(),
                            account.getStatusReason() == null ? "Add it again." : account.getStatusReason());
            case SUSPENDED -> "Payouts to this account are on hold. Contact support.";
        };
    }
}
