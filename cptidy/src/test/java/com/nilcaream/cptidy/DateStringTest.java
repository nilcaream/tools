package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class DateStringTest {

    @Test
    void shouldExtractFromYMDString() {
        // given
        DateString actual = new DateString("2020-10-08");

        // then
        assertThat(actual.asLong()).isEqualTo("20201008");
        assertThat(actual.asShort()).isEqualTo("2020-10");
        assertThat(actual.toString()).isEqualTo("2020-10-08");
    }

    @Test
    void shouldExtractFromYMDArguments() {
        // given
        DateString actual = new DateString("2120", "11", "18");

        // then
        assertThat(actual.asLong()).isEqualTo("21201118");
        assertThat(actual.asShort()).isEqualTo("2120-11");
        assertThat(actual.toString()).isEqualTo("2120-11-18");
    }

    @Test
    void shouldExtractFromDate() {
        // given
        DateString actual = new DateString(new Date(0));

        // then
        assertThat(actual.asLong()).isEqualTo("19700101");
        assertThat(actual.asShort()).isEqualTo("1970-01");
        assertThat(actual.toString()).isEqualTo("1970-01-01");
    }
}