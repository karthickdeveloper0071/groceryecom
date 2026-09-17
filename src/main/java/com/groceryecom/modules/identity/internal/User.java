package com.groceryecom.modules.identity.internal;

import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.GenerationType;
import jakarta.persistence.GeneratedValue;

import java.time.LocalDateTime;

/**
 * User entity
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_username", columnList = "username")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "email_verified")
    private Boolean emailVerified = false;

    @Column(name = "phone_verified")
    private Boolean phoneVerified = false;

    public enum UserRole {
        ADMIN, VENDOR, CUSTOMER
    }
}

