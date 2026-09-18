package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.PayoutAccountResponse;
import com.groceryecom.modules.billing.api.dto.SavePayoutAccountRequest;
import com.groceryecom.modules.billing.contract.VendorEntitlements;
import com.groceryecom.modules.billing.contract.VendorPayouts;
import com.groceryecom.modules.billing.domain.VendorPayoutAccount;
import com.groceryecom.modules.billing.domain.VendorPayoutAccountRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import com.groceryecom.shared.money.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The money that flows the other way: from a customer's payment to the vendor.
 *
 * <p>Two jobs, and they are one class because they answer the same question from two
 * sides — a vendor asking "where will you send my money?" and the order module asking
 * "where do I send it, and how much?".
 *
 * <p>The bank details never reach this application's storage. They go to the gateway,
 * which creates a destination and returns an id; that id is what is kept. So a database
 * dump cannot be used to move anybody's money, and the platform stays out of the
 * compliance scope that storing account numbers brings.
 *
 * <p>A store with no verified destination is not blocked from selling. Its share is
 * <b>held</b>, not lost, and paid once verification completes. Refusing orders because
 * the paperwork is late would cost the vendor customers over a form.
 */
@Slf4j
@Service
public class VendorPayoutService implements VendorPayouts {

    private final BillingScope billingScope;
    private final VendorPayoutAccountRepository accounts;
    private final VendorEntitlements entitlements;
    private final VendorPayoutGateway payoutGateway;
    private final AuditLog auditLog;

    VendorPayoutService(BillingScope billingScope, VendorPayoutAccountRepository accounts,
                        VendorEntitlements entitlements, VendorPayoutGateway payoutGateway,
                        AuditLog auditLog) {
        this.billingScope = billingScope;
        this.accounts = accounts;
        this.entitlements = entitlements;
        this.payoutGateway = payoutGateway;
        this.auditLog = auditLog;
    }

    /**
     * A vendor gives the platform somewhere to send their money.
     *
     * <p>Submitting again replaces the destination and starts verification over, because
     * a changed bank account is what an account takeover looks like. Only the store's
     * owner may do this: staff run the shop, they do not redirect its income.
     */
    @Transactional
    public PayoutAccountResponse submit(AuthenticatedUser user, UUID vendorId,
                                        SavePayoutAccountRequest request) {
        billingScope.requireOwner(user, vendorId);

        String last4 = last4Of(request.accountNumber());
        VendorPayoutAccount account = accounts.findByVendorPublicId(vendorId)
                .map(existing -> {
                    existing.replaceWith(request.accountHolderName().trim(), request.bankName(), last4);
                    return existing;
                })
                .orElseGet(() -> VendorPayoutAccount.submitted(vendorId, payoutGateway.provider(),
                        request.accountHolderName().trim(), request.bankName(), last4));

        // The account number and IFSC go to the gateway and are not stored here. After
        // this call the only copy in this process is the request object, which the
        // request log masks and the garbage collector takes.
        VendorPayoutGateway.Registration registration = payoutGateway.registerDestination(
                vendorId, request.accountHolderName().trim(), request.accountNumber(),
                request.ifscCode(), request.contactEmail());

        if (registration.verified()) {
            account.verified(registration.providerAccountId(), Instant.now());
        } else if (registration.rejected()) {
            account.rejected(registration.message());
        }

        VendorPayoutAccount saved = accounts.save(account);
        auditLog.payoutAccountChanged(user.id(), vendorId, saved.getAccountLast4(),
                saved.getStatus().name());
        log.info("Payout destination for vendor {} is {}", vendorId, saved.getStatus());

        return PayoutAccountResponse.of(saved);
    }

    /** What the vendor sees about where their money goes. 404 while they have set none. */
    @Transactional(readOnly = true)
    public PayoutAccountResponse ofVendor(AuthenticatedUser user, UUID vendorId) {
        billingScope.requireOwner(user, vendorId);

        return accounts.findByVendorPublicId(vendorId)
                .map(PayoutAccountResponse::of)
                .orElseThrow(() -> new NotFoundException("Payout account", vendorId));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSplit splitFor(UUID vendorId, Money orderTotal) {
        // A store that may not sell may not be paid either. This throws the same 402 the
        // storefront would, so the caller does not need a second rule for it.
        entitlements.requireTrading(vendorId);

        int basisPoints = entitlements.of(vendorId)
                .map(VendorEntitlements.Entitlement::commissionBasisPoints)
                .orElseThrow(() -> new NotFoundException("Subscription", vendorId));

        // Rounded DOWN, so rounding never invents commission the vendor did not agree to
        Money commission = orderTotal.percentage(basisPoints, RoundingMode.DOWN);
        // By subtraction, so the two parts always add up to exactly what the customer paid
        Money vendorShare = orderTotal.minus(commission);

        return new PaymentSplit(orderTotal, commission, vendorShare, basisPoints, destinationFor(vendorId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PayoutDestination> destinationFor(UUID vendorId) {
        return accounts.findByVendorPublicId(vendorId)
                .filter(VendorPayoutAccount::canReceiveMoney)
                .map(account -> new PayoutDestination(account.getProvider(),
                        account.getProviderAccountId(), account.getAccountLast4()));
    }

    private static String last4Of(String accountNumber) {
        String digits = accountNumber.replaceAll("\\D", "");
        return digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
    }
}
