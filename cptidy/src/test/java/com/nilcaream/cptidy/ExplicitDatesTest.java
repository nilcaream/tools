package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExplicitDatesTest {

    private ExplicitDates underTest = new ExplicitDates();

    @Test
    void shouldReturnNullOnEmptyDates() {
        // then
        assertThat(underTest.getDate("")).isNull();
        assertThat(underTest.getDate("test")).isNull();
        assertThat(underTest.getDate("****[[[[")).isNull();
    }

    @Test
    void shouldReturnDates() {
        // given
        underTest.add("test.+txt", "2022-02-10");
        underTest.add("more.+", "2000-01-07");

        // then
        assertThat(underTest.getDate("")).isNull();
        assertThat(underTest.getDate("test")).isNull();
        assertThat(underTest.getDate("****[[[[")).isNull();
        assertThat(underTest.getDate("testtxt")).isNull();
        assertThat(underTest.getDate(" test-txt")).isNull();
        assertThat(underTest.getDate("more")).isNull();
        // then
        assertThat(underTest.getDate("test-txt").asLong()).isEqualTo("20220210");
        assertThat(underTest.getDate("more[").asLong()).isEqualTo("20000107");
    }
}