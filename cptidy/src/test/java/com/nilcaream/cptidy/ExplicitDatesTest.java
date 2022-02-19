package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExplicitDatesTest {

    private ExplicitDates underTest = new ExplicitDates();

    @Test
    void shouldReturnNullOnEmptyDates() throws IOException {
        // then
        assertThat(underTest.getDate(Paths.get(""))).isNull();
        assertThat(underTest.getDate(Paths.get("test"))).isNull();
        assertThat(underTest.getDate(Paths.get("****[[[["))).isNull();
    }

    @Test
    void shouldReturnDates() throws IOException {
        // given
        underTest.add("test.+txt", "2022-02-10");
        underTest.add("more.+", "2000-01-07");

        // then
        assertThat(underTest.getDate(Paths.get(""))).isNull();
        assertThat(underTest.getDate(Paths.get("test"))).isNull();
        assertThat(underTest.getDate(Paths.get("****[[[["))).isNull();
        assertThat(underTest.getDate(Paths.get("testtxt"))).isNull();
        assertThat(underTest.getDate(Paths.get(" test-txt"))).isNull();
        assertThat(underTest.getDate(Paths.get("more"))).isNull();
        // then
        assertThat(underTest.getDate(Paths.get("test-txt")).asLong()).isEqualTo("20220210");
        assertThat(underTest.getDate(Paths.get("more[")).asLong()).isEqualTo("20000107");
    }
}