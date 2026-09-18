package com.groceryecom.modules.billing.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    Optional<SubscriptionPayment> findByProviderReference(String providerReference);

    List<SubscriptionPayment> findBySubscriptionOrderByCreatedAtDesc(VendorSubscription subscription);

    /**
     * The admin console's list, and with status=PENDING the queue of bank transfers
     * waiting to be confirmed.
     */
    @Query("SELECT p FROM SubscriptionPayment p WHERE (:status IS NULL OR p.status = :status)")
    Page<SubscriptionPayment> findForAdmin(@Param("status") SubscriptionPayment.PaymentStatus status,
                                           Pageable pageable);
}
