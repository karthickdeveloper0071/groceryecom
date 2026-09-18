package com.groceryecom.modules.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    Optional<SubscriptionPayment> findByProviderReference(String providerReference);

    List<SubscriptionPayment> findBySubscriptionOrderByCreatedAtDesc(VendorSubscription subscription);
}
