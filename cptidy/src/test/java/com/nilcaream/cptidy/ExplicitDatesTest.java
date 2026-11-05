package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

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

    @Test
    void shouldReturnModificationDateOfFile(@TempDir Path root) throws IOException {
        // given
        Path path = Files.writeString(root.resolve("scan-001.jpg"), "test");
        Files.setLastModifiedTime(path, FileTime.from(Instant.parse("2015-06-20T10:00:00Z")));
        underTest.add("scan-.+", "FILE_DATE");

        // then
        assertThat(underTest.getDate(path).asLong()).isEqualTo("20150620");
    }
}