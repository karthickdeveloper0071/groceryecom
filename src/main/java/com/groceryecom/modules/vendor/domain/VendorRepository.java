package com.groceryecom.modules.vendor.domain;

import com.groceryecom.modules.vendor.contract.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    Optional<Vendor> findByPublicId(UUID publicId);

    boolean existsBySlug(String slug);

    /**
     * The admin list: every store, optionally narrowed by status and by a name or address
     * the admin typed.
     *
     * <p>One query with optional parameters rather than four derived methods, because the
     * combinations multiply and a missed one becomes "the filter does nothing". The
     * search is case-insensitive and matches anywhere in the name, which is how a person
     * searching for "mart" expects it to behave; at 100 vendors that costs nothing. When
     * the platform has thousands of stores this needs a trigram index, and the query has
     * to be measured before that is added, not guessed at.
     *
     * <p>An empty search matches everything, and the parameter is never null: PostgreSQL
     * cannot type a null inside CONCAT and fails with "function lower(bytea) does not
     * exist", which is a confusing way to learn that a filter was left blank.
     */
    @Query("""
            SELECT v FROM Vendor v
             WHERE (:status IS NULL OR v.status = :status)
               AND (LOWER(v.displayName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                    OR LOWER(v.slug) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<Vendor> search(@Param("status") VendorStatus status, @Param("search") String search,
                        Pageable pageable);
}
