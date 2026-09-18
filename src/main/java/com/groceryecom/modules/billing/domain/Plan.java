package com.groceryecom.modules.billing.domain;

import com.groceryecom.shared.money.Money;
import com.groceryecom.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * What a store can buy: a price, a period, and the limits that come with it.
 *
 * <p>Reference data, seeded by a migration. Plans are not deleted once a store is on
 * one - an old price is hidden with {@code isPublic = false} and keeps working for the
 * vendors who bought it, because changing what somebody already pays for is a different
 * conversation from changing a price list.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "code", nullable = false, unique = true, updatable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "price_amount_minor", nullable = false)
    private Long priceAmountMinor;

    @Column(name = "price_currency", nullable = false, length = 3)
    private String priceCurrency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "billing_period", nullable = false, length = 10)
    private BillingPeriod billingPeriod;

    @Column(name = "trial_days", nullable = false)
    private Integer trialDays;

    // Null means no limit
    @Column(name = "max_products")
    private Integer maxProducts;

    @Column(name = "max_staff")
    private Integer maxStaff;

    @Column(name = "commission_basis_points", nullable = false)
    private Integer commissionBasisPoints;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true;

    public Money price() {
        return Money.ofMinor(priceAmountMinor, priceCurrency);
    }

    public boolean hasTrial() {
        return trialDays != null && trialDays > 0;
    }

    /** The end of one paid period starting at {@code from}. */
    public Instant periodEndFrom(Instant from) {
        return billingPeriod.endFrom(from);
    }

    public Instant trialEndFrom(Instant from) {
        return from.plus(trialDays, ChronoUnit.DAYS);
    }

    @PrePersist
    void assignPublicId() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
    }
}
