package com.groceryecom.modules.billing.application;

import com.groceryecom.modules.billing.api.dto.PlanResponse;
import com.groceryecom.modules.billing.api.dto.SubscriptionResponse;
import com.groceryecom.modules.billing.domain.PlanRepository;
import com.groceryecom.modules.billing.domain.VendorSubscriptionRepository;
import com.groceryecom.modules.billing.mapper.BillingMapper;
import com.groceryecom.platform.security.AuthenticatedUser;
import com.groceryecom.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reading the price list, and reading your own licence.
 *
 * <p>The price list is public: somebody deciding whether to sell here should not have to
 * create an account to see what it costs. A licence is not: it belongs to the store's
 * owner.
 */
@Service
public class GetSubscriptionService {

    private final BillingScope billingScope;
    private final PlanRepository plans;
    private final VendorSubscriptionRepository subscriptions;

    GetSubscriptionService(BillingScope billingScope, PlanRepository plans,
                           VendorSubscriptionRepository subscriptions) {
        this.billingScope = billingScope;
        this.plans = plans;
        this.subscriptions = subscriptions;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> publicPlans() {
        return plans.findByIsPublicTrueAndIsActiveTrueOrderByPriceAmountMinorAsc().stream()
                .map(BillingMapper::toResponse)
                .toList();
    }

    /**
     * The store's licence, with the sentence to show its owner.
     *
     * <p>404 when the store never chose a plan: there is nothing to report, and the
     * client's next step is the price list either way.
     */
    @Transactional(readOnly = true)
    public SubscriptionResponse ofVendor(AuthenticatedUser user, UUID vendorId) {
        billingScope.requireOwner(user, vendorId);

        return subscriptions.findByVendorPublicId(vendorId)
                .map(subscription -> BillingMapper.toResponse(subscription, Instant.now()))
                .orElseThrow(() -> new NotFoundException("Subscription", vendorId));
    }
}
