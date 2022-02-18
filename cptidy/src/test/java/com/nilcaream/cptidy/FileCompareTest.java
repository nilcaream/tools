package com.nilcaream.cptidy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static java.lang.System.nanoTime;
import static org.assertj.core.api.Assertions.assertThat;

class FileCompareTest {

    private FileCompare underTest = new FileCompare();

    private Path pathA;
    private Path pathB;
    private String content;

    private Io io = new Io();

    @BeforeEach
    void setUp() throws IOException {
        content = io.read(Paths.get("pom.xml"));
        assertThat(content).hasSizeGreaterThan(8000);
        pathA = Files.createTempFile("cptidy-", ".tmp");
        pathB = Files.createTempFile("cptidy-", ".tmp");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(pathA);
        Files.deleteIfExists(pathB);
    }

    @Test
    void shouldBeDifferentForDifferentSizeFiles() throws IOException {
        // given
        io.write(pathA, content);
        io.write(pathB, "test");

        // then
        assertComparison(false);
    }

    @Test
    void shouldBeDifferentForSimilarSizeFiles() throws IOException {
        // given
        io.write(pathA, content);
        io.write(pathB, content + "a");

        // then
        assertComparison(false);
    }

    @Test
    void shouldBeDifferentForSameSizeFiles() throws IOException {
        // given
        io.write(pathA, content + content + "a");
        io.write(pathB, content + content + "b");

        // then
        assertComparison(false);
    }

    @Test
    void shouldBeSameForLargeFile() throws IOException {
        // given
        io.write(pathA, content + content + "a");
        io.write(pathB, content + content + "a");

        // then
        assertComparison(true);
    }

    @Test
    void shouldBeSameForSmallFile() throws IOException {
        // given
        io.write(pathA, "a");
        io.write(pathB, "a");

        // then
        assertComparison(true);
    }

    @Test
    void shouldNotFailForNotExistingFile() throws IOException {
        // given
        Files.delete(pathB);
        assertThat(pathA).exists();
        assertThat(pathB).doesNotExist();

        // then
        assertComparison(false);
    }

    void assertComparison(boolean expected) throws IOException {
        for (int i = 1024; i <= 1024 * 1024; i *= 2) {
            System.out.printf("Assert buffer %d for %s %s\n", i, pathA, pathB);
            assertThat(underTest.byByteChannel(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byByteChannel(pathB, pathA, i)).isEqualTo(expected);
            assertThat(underTest.byHash(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byHash(pathB, pathA, i)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathB, pathA, i)).isEqualTo(expected);
        }
    }

    @Disabled
    void manuallyCheckPerformance() throws IOException {
        Path rootA = Paths.get("/home/copter/cptidy-work/output/2013-09/copy");
        Path rootB = Paths.get("/home/copter/cptidy-work/output/2013-09");

        for (Path pathA : Files.list(rootA).sorted().collect(Collectors.toList())) {
            Path pathB = rootB.resolve(pathA.getFileName());
            compare(pathA, pathB, 16 * 1024 * 1024);
        }
    }

    private void compare(Path pathA, Path pathB, int bufferSize) throws IOException {
        double size = Files.size(pathA) / (1024.0 * 1024.0);

        System.out.printf("-------- %.3f MB | %d | %s : %s\n", size, bufferSize, pathA, pathB);

        byByteChannel(pathA, pathB, bufferSize, size);

        byHash(pathA, pathB, bufferSize, size);
        byHash(pathA, pathB, bufferSize, size);

        byteByByte(pathA, pathB, bufferSize, size);
        byteByByte(pathA, pathB, bufferSize, size);

        byByteChannel(pathA, pathB, bufferSize, size);
        byByteChannel(pathA, pathB, bufferSize, size);

        byteByByte(pathA, pathB, bufferSize, size);
        byteByByte(pathA, pathB, bufferSize, size);

        byHash(pathA, pathB, bufferSize, size);
        byHash(pathA, pathB, bufferSize, size);
    }

    private void byByteChannel(Path pathA, Path pathB, int bufferSize, double size) throws IOException {
        boolean result;
        long time;
        time = nanoTime();
        result = underTest.byByteChannel(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        System.out.printf("channel %.3f ms %s %.3f MB\n", time / 1000.0, result, size);
    }

    private void byteByByte(Path pathA, Path pathB, int bufferSize, double size) throws IOException {
        long time;
        boolean result;
        time = nanoTime();
        result = underTest.byteByByte(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        System.out.printf("byByte  %.3f ms %s %.3f MB\n", time / 1000.0, result, size);
    }

    private void byHash(Path pathA, Path pathB, int bufferSize, double size) throws IOException {
        long time;
        boolean result;
        time = nanoTime();
        result = underTest.byHash(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        System.out.printf("byHash  %.3f ms %s %.3f MB\n", time / 1000.0, result, size);
    }
}