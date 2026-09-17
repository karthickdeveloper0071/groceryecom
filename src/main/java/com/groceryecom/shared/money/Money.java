package com.groceryecom.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in the currency's smallest unit (paise, cents).
 * Never use double or float for money: 0.1 + 0.2 != 0.3.
 * Stored in the database as BIGINT amount plus a 3-letter currency code.
 */
public record Money(long amountMinor, Currency currency) implements Comparable<Money> {

    private static final long BASIS_POINTS_PER_WHOLE = 10_000;

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money ofMinor(long amountMinor, String currencyCode) {
        return new Money(amountMinor, Currency.getInstance(currencyCode));
    }

    /**
     * Converts a decimal amount such as 49.99; rejects more decimal places than the currency allows.
     */
    public static Money of(BigDecimal amount, String currencyCode) {
        Currency currency = Currency.getInstance(currencyCode);
        BigDecimal minor = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.UNNECESSARY)
                .movePointRight(currency.getDefaultFractionDigits());
        return new Money(minor.longValueExact(), currency);
    }

    public static Money zero(String currencyCode) {
        return ofMinor(0, currencyCode);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money times(long quantity) {
        return new Money(Math.multiplyExact(amountMinor, quantity), currency);
    }

    /**
     * A share of this amount, such as a commission: 1250 basis points = 12.5%.
     */
    public Money percentage(long basisPoints, RoundingMode roundingMode) {
        BigDecimal share = BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(basisPoints))
                .divide(BigDecimal.valueOf(BASIS_POINTS_PER_WHOLE), 0, roundingMode);
        return new Money(share.longValueExact(), currency);
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(amountMinor, currency.getDefaultFractionDigits());
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amountMinor, other.amountMinor);
    }

    @Override
    public String toString() {
        return toDecimal().toPlainString() + " " + currency.getCurrencyCode();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " and " + other.currency.getCurrencyCode());
        }
    }
}
