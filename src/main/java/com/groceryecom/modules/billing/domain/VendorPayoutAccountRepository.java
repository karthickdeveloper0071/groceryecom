package com.groceryecom.modules.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorPayoutAccountRepository extends JpaRepository<VendorPayoutAccount, Long> {

    Optional<VendorPayoutAccount> findByVendorPublicId(UUID vendorPublicId);

    List<VendorPayoutAccount> findByStatusOrderByCreatedAtAsc(VendorPayoutAccount.PayoutAccountStatus status);
}
