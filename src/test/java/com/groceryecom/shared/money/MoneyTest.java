package com.groceryecom.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void convertsDecimalAmountsToMinorUnits() {
        assertThat(Money.of(new BigDecimal("49.99"), "INR").amountMinor()).isEqualTo(4999);
        assertThat(Money.of(new BigDecimal("1500"), "JPY").amountMinor()).isEqualTo(1500);
    }

    @Test
    void rejectsMorePrecisionThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.of(new BigDecimal("0.005"), "INR"))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void addsExactlyWhereDoublesWouldNot() {
        Money sum = Money.of(new BigDecimal("0.10"), "INR").plus(Money.of(new BigDecimal("0.20"), "INR"));

        assertThat(sum).isEqualTo(Money.of(new BigDecimal("0.30"), "INR"));
        assertThat(sum.toString()).isEqualTo("0.30 INR");
    }

    @Test
    void multipliesByQuantity() {
        assertThat(Money.ofMinor(4999, "INR").times(3)).isEqualTo(Money.ofMinor(14997, "INR"));
    }

    @Test
    void calculatesCommissionInBasisPoints() {
        Money orderTotal = Money.ofMinor(99_999, "INR");

        assertThat(orderTotal.percentage(1_250, RoundingMode.HALF_UP)).isEqualTo(Money.ofMinor(12_500, "INR"));
        assertThat(orderTotal.percentage(1_250, RoundingMode.DOWN)).isEqualTo(Money.ofMinor(12_499, "INR"));
    }

    @Test
    void refusesToMixCurrencies() {
        assertThatThrownBy(() -> Money.ofMinor(100, "INR").plus(Money.ofMinor(100, "USD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");
    }

    @Test
    void detectsOverflowInsteadOfWrappingAround() {
        assertThatThrownBy(() -> Money.ofMinor(Long.MAX_VALUE, "INR").plus(Money.ofMinor(1, "INR")))
                .isInstanceOf(ArithmeticException.class);
    }
}
