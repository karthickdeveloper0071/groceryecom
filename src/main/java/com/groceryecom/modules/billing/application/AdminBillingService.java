package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.AdminPaymentResponse;
import com.groceryecom.modules.billing.api.dto.AdminSubscriptionResponse;
import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import com.groceryecom.modules.billing.domain.SubscriptionPayment.PaymentStatus;
import com.groceryecom.modules.billing.domain.SubscriptionPaymentRepository;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.shared.web.PageRequestParams;
import com.groceryecom.shared.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the admin console needs to see about money.
 *
 * <p>Two lists, answering the two questions an operator actually asks: "who is paid up?"
 * and "what is waiting for me to confirm?". Payments come oldest first for the same
 * reason the approval queue does - it is a queue, and the vendor who has waited longest
 * is the one about to complain.
 */
@Service
public class AdminBillingService {

    private final VendorSubscriptionRepository subscriptions;
    private final SubscriptionPaymentRepository payments;

    AdminBillingService(VendorSubscriptionRepository subscriptions, SubscriptionPaymentRepository payments) {
        this.subscriptions = subscriptions;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionResponse> subscriptions(SubscriptionStatus status, int page, int size) {
        return PageResponse.of(
                subscriptions.findForAdmin(status, PageRequestParams.newestFirst(page, size)),
                AdminSubscriptionResponse::of);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> payments(PaymentStatus status, int page, int size) {
        // Pending first-in-first-out: this is a work queue, not a report
        var pageable = status == PaymentStatus.PENDING
                ? PageRequestParams.oldestFirst(page, size)
                : PageRequestParams.newestFirst(page, size);

        return PageResponse.of(payments.findForAdmin(status, pageable), AdminPaymentResponse::of);
    }
}
