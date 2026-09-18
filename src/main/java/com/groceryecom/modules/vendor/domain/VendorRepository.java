package com.groceryecom.modules.vendor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByPublicId(UUID publicId);

    boolean existsBySlug(String slug);
}
