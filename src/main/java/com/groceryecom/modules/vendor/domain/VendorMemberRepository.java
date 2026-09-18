package com.groceryecom.modules.vendor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorMemberRepository extends JpaRepository<VendorMember, Long> {

    /**
     * The membership that decides whether a caller may touch a store's data. Both ids
     * are in one query on purpose: fetching the vendor first and checking the
     * membership afterwards is the shape that leaks data the day somebody forgets the
     * second step.
     */
    Optional<VendorMember> findByVendorPublicIdAndUserPublicId(UUID vendorPublicId, UUID userPublicId);

    List<VendorMember> findByUserPublicId(UUID userPublicId);

    boolean existsByUserPublicId(UUID userPublicId);
}
