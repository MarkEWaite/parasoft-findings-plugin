package com.parasoft.findings.jenkins.coverage.model;

import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link SafeFraction}.
 *
 * @author Ullrich Hafner
 */
class SafeFractionTest {
    @Test
    void shouldDelegateToFraction() {
        var ten = Fraction.getFraction(10, 1);
        var safeFraction = new SafeFraction(ten);
        assertThat(safeFraction.multiplyBy(ten).doubleValue()).isEqualTo(100.0);
        assertThat(safeFraction.subtract(ten).doubleValue()).isEqualTo(0);
        assertThat(safeFraction.add(ten).doubleValue()).isEqualTo(20.0);
    }

    @Test
    void shouldHandleOverflowForMultiply() {
        var fraction = Fraction.getFraction(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var safeFraction = new SafeFraction(fraction);
        assertThat(safeFraction.multiplyBy(Fraction.getFraction("100.0")).doubleValue()).isEqualTo(100.0);
    }

    @Test
    void shouldHandleOverflowForSubtract() {
        var fraction = Fraction.getFraction(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var safeFraction = new SafeFraction(fraction);
        assertThat(safeFraction.subtract(Fraction.getFraction("100.0")).doubleValue()).isEqualTo(-99.0);
    }

    @Test
    void shouldHandleOverflowForAdd() {
        var fraction = Fraction.getFraction(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var safeFraction = new SafeFraction(fraction);
        assertThat(safeFraction.add(Fraction.getFraction("100.0")).doubleValue()).isEqualTo(101.0);
    }
}
