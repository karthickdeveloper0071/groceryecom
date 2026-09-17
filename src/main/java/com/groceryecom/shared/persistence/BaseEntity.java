package com.groceryecom.shared.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;

/**
 * Base Entity class for all entities in the system.
 * Uses getters/setters rather than @Data: generated equals/hashCode over all
 * fields breaks JPA entities (lazy loading, entities in hash-based collections).
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    // Stored as TIMESTAMPTZ; Instant is always UTC
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    protected Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    protected Instant updatedAt;

    @Column(name = "is_active", nullable = false)
    protected Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    protected Boolean isDeleted = false;
}
