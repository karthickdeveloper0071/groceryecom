package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.PayoutAccountResponse;
import com.groceryecom.modules.billing.api.dto.SavePayoutAccountRequest;
import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.modules.billing.contract.VendorEntitlements;
import com.groceryecom.modules.billing.contract.VendorPayouts.PaymentSplit;
import com.groceryecom.modules.billing.domain.VendorPayoutAccount;
import com.groceryecom.modules.billing.domain.VendorPayoutAccount.PayoutAccountStatus;
import com.groceryecom.modules.billing.domain.VendorPayoutAccountRepository;
import com.groceryecom.platform.audit.AuditLog;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.PaymentRequiredException;
import com.groceryecom.shared.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Dividing a customer's payment, and where the vendor's half goes.
 *
 * <p>This is the arithmetic that decides what a vendor is paid. At a million orders a
 * rounding rule that leans the wrong way is real money moving quietly in one direction,
 * so the awkward amounts are here on purpose.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VendorPayoutServiceTest {

    private static final UUID VENDOR = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();

    @Mock
    private BillingScope billingScope;

    @Mock
    private VendorPayoutAccountRepository accounts;

    @Mock
    private VendorEntitlements entitlements;

    @Mock
    private VendorPayoutGateway payoutGateway;

    @Mock
    private AuditLog auditLog;

    @InjectMocks
    private VendorPayoutService payouts;

    @Test
    void theCommissionAndTheVendorShareAddUpToWhatTheCustomerPaid() {
        givenPlanCommission(350);           // 3.5%
        givenVerifiedDestination();

        PaymentSplit split = payouts.splitFor(VENDOR, Money.ofMinor(10_000, "MYR"));

        assertThat(split.platformCommission()).isEqualTo(Money.ofMinor(350, "MYR"));
        assertThat(split.vendorShare()).isEqualTo(Money.ofMinor(9_650, "MYR"));
        assertThat(split.platformCommission().plus(split.vendorShare())).isEqualTo(split.orderTotal());
    }

    /**
     * The case that decides whether the arithmetic can be trusted: an amount where the
     * commission is not a whole sen. Rounded down, so the platform never invents a cut
     * the vendor did not agree to, and the remainder goes to the vendor.
     */
    @Test
    void aFractionOfASenGoesToTheVendorNotToThePlatform() {
        givenPlanCommission(350);
        givenVerifiedDestination();

        // 3.5% of 3,333 sen is 116.655 sen
        PaymentSplit split = payouts.splitFor(VENDOR, Money.ofMinor(3_333, "MYR"));

        assertThat(split.platformCommission()).isEqualTo(Money.ofMinor(116, "MYR"));
        assertThat(split.vendorShare()).isEqualTo(Money.ofMinor(3_217, "MYR"));
        assertThat(split.platformCommission().plus(split.vendorShare())).isEqualTo(split.orderTotal());
    }

    @Test
    void theRateComesFromThePlanTheStoreIsOn() {
        givenPlanCommission(200);           // the Scale plan's 2%
        givenVerifiedDestination();

        PaymentSplit split = payouts.splitFor(VENDOR, Money.ofMinor(50_000, "MYR"));

        assertThat(split.commissionBasisPoints()).isEqualTo(200);
        assertThat(split.platformCommission()).isEqualTo(Money.ofMinor(1_000, "MYR"));
    }

    @Test
    void aTinyOrderTakesNoCommissionRatherThanARoundedUpOne() {
        givenPlanCommission(350);
        givenVerifiedDestination();

        // 3.5% of 10 sen is 0.35 sen: less than the smallest unit there is
        PaymentSplit split = payouts.splitFor(VENDOR, Money.ofMinor(10, "MYR"));

        assertThat(split.platformCommission().isZero()).isTrue();
        assertThat(split.vendorShare()).isEqualTo(Money.ofMinor(10, "MYR"));
    }

    /** A store that may not sell may not be paid. Same 402, same words, one rule. */
    @Test
    void aStoreWithAnExpiredPlanCannotBePaid() {
        doThrow(new PaymentRequiredException("Your plan expired", "SUBSCRIPTION_EXPIRED"))
                .when(entitlements).requireTrading(VENDOR);

        assertThatThrownBy(() -> payouts.splitFor(VENDOR, Money.ofMinor(10_000, "MYR")))
                .isInstanceOf(PaymentRequiredException.class);
    }

    /**
     * Not being verified yet does not cost the vendor the sale. The split still says what
     * they are owed; it just has nowhere to send it, so the money is held.
     */
    @Test
    void withoutAVerifiedAccountTheShareIsStillOwedButNotPayableYet() {
        givenPlanCommission(350);
        when(accounts.findByVendorPublicId(VENDOR)).thenReturn(Optional.of(pendingAccount()));

        PaymentSplit split = payouts.splitFor(VENDOR, Money.ofMinor(10_000, "MYR"));

        assertThat(split.vendorShare()).isEqualTo(Money.ofMinor(9_650, "MYR"));
        assertThat(split.payableNow()).isFalse();
        assertThat(split.destination()).isEmpty();
    }

    @Test
    void onlyTheLastFourDigitsOfTheAccountAreEverStored() {
        when(payoutGateway.provider()).thenReturn("RAZORPAY");
        when(payoutGateway.registerDestination(any(), any(), any(), any(), any()))
                .thenReturn(VendorPayoutGateway.Registration.verified("acc_LinkedAccount1"));
        when(accounts.findByVendorPublicId(VENDOR)).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));

        PayoutAccountResponse response = payouts.submit(owner(), VENDOR, request("000123456789"));

        ArgumentCaptor<VendorPayoutAccount> saved = ArgumentCaptor.forClass(VendorPayoutAccount.class);
        org.mockito.Mockito.verify(accounts).save(saved.capture());

        assertThat(saved.getValue().getAccountLast4()).isEqualTo("6789");
        assertThat(response.accountLast4()).isEqualTo("6789");
        // Nothing anywhere in the stored row or the response carries the full number
        assertThat(saved.getValue().toString()).doesNotContain("000123456789");
        assertThat(response.toString()).doesNotContain("000123456789");
    }

    /** Changing the destination is what an account takeover looks like: verify it again. */
    @Test
    void newBankDetailsStartVerificationAgain() {
        VendorPayoutAccount existing = verifiedAccount();
        when(payoutGateway.provider()).thenReturn("RAZORPAY");
        when(payoutGateway.registerDestination(any(), any(), any(), any(), any()))
                .thenReturn(VendorPayoutGateway.Registration.pending("acc_New"));
        when(accounts.findByVendorPublicId(VENDOR)).thenReturn(Optional.of(existing));
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));

        PayoutAccountResponse response = payouts.submit(owner(), VENDOR, request("999988887777"));

        assertThat(response.status()).isEqualTo(PayoutAccountStatus.PENDING_VERIFICATION);
        assertThat(response.canReceive()).isFalse();
        assertThat(response.accountLast4()).isEqualTo("7777");
        assertThat(response.message()).contains("held until it is verified");
    }

    @Test
    void aRefusedAccountTellsTheVendorWhatToDo() {
        when(payoutGateway.provider()).thenReturn("RAZORPAY");
        when(payoutGateway.registerDestination(any(), any(), any(), any(), any()))
                .thenReturn(VendorPayoutGateway.Registration.rejected("The account number and IFSC do not match."));
        when(accounts.findByVendorPublicId(VENDOR)).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));

        PayoutAccountResponse response = payouts.submit(owner(), VENDOR, request("000123456789"));

        assertThat(response.status()).isEqualTo(PayoutAccountStatus.REJECTED);
        assertThat(response.message()).contains("do not match");
    }

    private void givenPlanCommission(int basisPoints) {
        when(entitlements.of(VENDOR)).thenReturn(Optional.of(new VendorEntitlements.Entitlement(
                true, SubscriptionStatus.ACTIVE, "GROWTH", Instant.now(), Instant.now(),
                1000, 10, basisPoints)));
    }

    private void givenVerifiedDestination() {
        when(accounts.findByVendorPublicId(VENDOR)).thenReturn(Optional.of(verifiedAccount()));
    }

    private static VendorPayoutAccount verifiedAccount() {
        VendorPayoutAccount account = VendorPayoutAccount.submitted(
                VENDOR, "RAZORPAY", "Fresh Mart Sdn Bhd", "Maybank", "6789");
        account.verified("acc_LinkedAccount1", Instant.now());
        return account;
    }

    private static VendorPayoutAccount pendingAccount() {
        return VendorPayoutAccount.submitted(VENDOR, "RAZORPAY", "Fresh Mart Sdn Bhd", "Maybank", "6789");
    }

    private static AuthenticatedUser owner() {
        return new AuthenticatedUser(OWNER, "asha", Set.of("CUSTOMER"), "token", UUID.randomUUID(),
                Instant.now());
    }

    private static SavePayoutAccountRequest request(String accountNumber) {
        return new SavePayoutAccountRequest("Fresh Mart Sdn Bhd", accountNumber, "MBBEMYKL",
                "Maybank", "owner@freshmart.example");
    }
}
