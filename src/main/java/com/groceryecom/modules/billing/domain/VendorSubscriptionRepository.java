package com.groceryecom.modules.billing.domain;

import com.groceryecom.modules.billing.contract.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorSubscriptionRepository extends JpaRepository<VendorSubscription, Long> {

    Optional<VendorSubscription> findByVendorPublicId(UUID vendorPublicId);

    Optional<VendorSubscription> findByPublicId(UUID publicId);

    /**
     * Licences that have run past a date and still count as trading: the work list for
     * the job that moves stores to past due and then off the storefront. Bounded by the
     * index on {@code grace_until}, so it stays a small read however many vendors there are.
     */
    List<VendorSubscription> findByStatusInAndCurrentPeriodEndBefore(
            Collection<SubscriptionStatus> statuses, Instant when);

    /**
     * The admin console's list. One query with an optional status rather than two
     * methods, so a caller cannot pick the one that ignores the filter.
     */
    @Query("SELECT s FROM VendorSubscription s WHERE (:status IS NULL OR s.status = :status)")
    Page<VendorSubscription> findForAdmin(@Param("status") SubscriptionStatus status, Pageable pageable);
}
