package com.fareflow.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    @DisplayName("cents format as currency")
    void formats() {
        assertThat(Money.format(625)).isEqualTo("$6.25");
        assertThat(Money.format(300)).isEqualTo("$3.00");
        assertThat(Money.format(290)).isEqualTo("$2.90");
        assertThat(Money.format(0)).isEqualTo("$0.00");
        assertThat(Money.format(5)).isEqualTo("$0.05");
        assertThat(Money.format(1875)).isEqualTo("$18.75");
    }

    @Test
    @DisplayName("negative amounts keep the sign outside the currency symbol")
    void formatsNegatives() {
        assertThat(Money.format(-300)).isEqualTo("-$3.00");
        assertThat(Money.format(-5)).isEqualTo("-$0.05");
    }

    @Test
    @DisplayName("per-minute rates round to the nearest cent")
    void perMinute() {
        // $3.25 over 16 minutes = 20.3 cents/min -> $0.20
        assertThat(Money.formatPerMinute(325, 16)).isEqualTo("$0.20");
        assertThat(Money.formatPerMinute(100, 4)).isEqualTo("$0.25");
    }

    @Test
    @DisplayName("a zero-minute rate is refused rather than dividing by zero")
    void perMinuteRejectsZero() {
        assertThatThrownBy(() -> Money.formatPerMinute(325, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("minutes are pluralised correctly")
    void minutes() {
        assertThat(Money.formatMinutes(1)).isEqualTo("1 minute");
        assertThat(Money.formatMinutes(16)).isEqualTo("16 minutes");
        assertThat(Money.formatMinutes(0)).isEqualTo("0 minutes");
    }
}
