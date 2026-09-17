package com.groceryecom.shared.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Base Entity class for all entities in the system
 */
@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    protected LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    protected LocalDateTime updatedAt;

    @Column(name = "is_active")
    protected Boolean isActive = true;

    @Column(name = "is_deleted")
    protected Boolean isDeleted = false;
}

