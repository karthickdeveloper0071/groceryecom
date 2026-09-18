package com.groceryecom.modules.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    /** The session a refresh token belongs to. Empty means the token was rotated or never existed. */
    Optional<UserSession> findByRefreshTokenId(String refreshTokenId);

    Optional<UserSession> findByPublicIdAndUserPublicId(UUID publicId, UUID userPublicId);

    /** A user's devices, newest login first. */
    List<UserSession> findByUserPublicIdAndRevokedAtIsNullOrderByCreatedAtDesc(UUID userPublicId);

    /**
     * Ends every live session of one user in a single statement, for "log out everywhere",
     * a password change or an admin lock-out.
     *
     * @return how many sessions were ended
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserSession s
               set s.revokedAt = :now, s.isActive = false
             where s.user.publicId = :userPublicId
               and s.revokedAt is null
            """)
    int revokeAllForUser(@Param("userPublicId") UUID userPublicId, @Param("now") Instant now);

    /** Housekeeping: sessions that expired long ago are of no interest to anyone. */
    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
