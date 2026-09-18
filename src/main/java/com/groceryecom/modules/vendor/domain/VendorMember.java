package com.groceryecom.modules.vendor.domain;

import com.groceryecom.modules.vendor.contract.VendorMemberRole;
import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * One person's right to act for one store. No row, no access.
 *
 * <p>The user is referenced by the identity module's <b>public id</b>, not by its
 * primary key: modules are joined by the identifier a module publishes, never by
 * another module's internal column. ADR-0014 records why, and what it costs.
 */
@Entity
@Table(name = "vendor_members")
@Getter
@Setter
@NoArgsConstructor
public class VendorMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false, updatable = false)
    private Vendor vendor;

    @Column(name = "user_public_id", nullable = false, updatable = false)
    private UUID userPublicId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "member_role", nullable = false, length = 20)
    private VendorMemberRole memberRole;

    public static VendorMember of(Vendor vendor, UUID userPublicId, VendorMemberRole role) {
        VendorMember member = new VendorMember();
        member.vendor = vendor;
        member.userPublicId = userPublicId;
        member.memberRole = role;
        return member;
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
