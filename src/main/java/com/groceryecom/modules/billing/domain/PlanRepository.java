package com.groceryecom.modules.billing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByCodeAndIsActiveTrue(String code);

    /** The price list a store chooses from: public plans only, cheapest first. */
    List<Plan> findByIsPublicTrueAndIsActiveTrueOrderByPriceAmountMinorAsc();
}
