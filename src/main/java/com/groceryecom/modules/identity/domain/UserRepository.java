package com.groceryecom.modules.identity.domain;

import com.groceryecom.modules.identity.contract.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link User}. Spring Data provides the implementation, so
 * there is no hand-written repository class (see ADR-0004).
 * Callers pass usernames and emails already normalized to lower case.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * The admin console's account lookup: a support call starts with a name or an email
     * somebody read out, so both are searched, case-insensitively.
     *
     * <p>Deliberately not a "list every user" method. At a million accounts that is not a
     * screen anybody can use, and a search keeps the habit of narrowing before reading.
     */
    @Query("""
            SELECT u FROM User u
             WHERE (:role IS NULL OR u.role = :role)
               AND (LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
                    OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
            """)
    Page<User> search(@Param("role") Role role, @Param("search") String search, Pageable pageable);
}
