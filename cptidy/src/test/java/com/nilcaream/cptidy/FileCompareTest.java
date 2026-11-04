package com.nilcaream.cptidy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class FileCompareTest {

    private static final int MB = 1024 * 1024;

    private final FileCompare underTest = new FileCompare();

    private Path pathA;
    private Path pathB;
    private String content;

    private final Io io = new Io();

    @TempDir
    private Path root;

    @BeforeEach
    void setUp() throws IOException {
        content = "0123456789abcdef".repeat(600);
        pathA = Files.createFile(root.resolve("a.tmp"));
        pathB = Files.createFile(root.resolve("b.tmp"));
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
    void shouldBeDifferentForSameSizeFiles2() throws IOException {
        // given
        io.write(pathA, content + "a" + content);
        io.write(pathB, content + "b" + content);

        // then
        assertComparison(false);
    }

    @Test
    void shouldBeDifferentForSameSizeFiles3() throws IOException {
        // given
        io.write(pathA, "a" + content + content);
        io.write(pathB, "b" + content + content);

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
    void shouldBeSameForEmptyFiles() throws IOException {
        assertComparison(true);
    }

    @Test
    void shouldNotFailForNotExistingFile() throws IOException {
        // given
        Files.delete(pathB);

        // then
        assertThat(underTest.sameContent(pathA, pathB)).isFalse();
        assertThat(underTest.sameContent(pathB, pathA)).isFalse();
    }

    @Test
    void shouldBeSameForSameLargeFiles() throws IOException {
        // given
        byte[] bytes = random(64 * MB, 1);
        Files.write(pathA, bytes);
        Files.write(pathB, bytes);

        // then
        assertComparison(true);
    }

    @Test
    void shouldBeDifferentForLargeFilesDifferingAnywhere() throws IOException {
        for (int offset : new int[]{0, 5 * MB + 3, 20 * MB, 32 * MB, 47 * MB + 11, 64 * MB - 1}) {
            // given
            byte[] bytes = random(64 * MB, 2);
            Files.write(pathA, bytes);
            bytes[offset]++;
            Files.write(pathB, bytes);

            // then
            assertComparison(false);
        }
    }

    @Test
    void shouldBeDifferentForLargeFilesWithZeroedHole() throws IOException {
        // given
        byte[] bytes = random(64 * MB, 3);
        Files.write(pathA, bytes);
        Arrays.fill(bytes, 20 * MB, 21 * MB, (byte) 0);
        Files.write(pathB, bytes);

        // then
        assertComparison(false);
    }

    @Test
    void shouldBeSameDespiteShortReads() throws IOException {
        // given
        byte[] bytes = random(3 * MB + 5, 4);

        // then
        assertThat(underTest.sameContent(new ByteArrayInputStream(bytes), oneByteAtATime(bytes))).isTrue();
        assertThat(underTest.sameContent(oneByteAtATime(bytes), new ByteArrayInputStream(bytes))).isTrue();
    }

    @Test
    void shouldGroupBySameContent() throws IOException {
        // given
        Path x1 = write("x1", random(2 * MB, 10));
        Path x2 = write("x2", random(2 * MB, 10));
        Path x3 = write("x3", random(2 * MB, 10));
        Path y1 = write("y1", random(2 * MB, 11));
        Path y2 = write("y2", random(2 * MB, 11));
        Path other = write("other", random(2 * MB, 12));
        Path small1 = write("small1", random(1000, 13));
        Path small2 = write("small2", random(1000, 13));
        Path smallOther = write("small-other", random(1000, 14));

        // when
        List<List<Path>> actual = underTest.groupBySameContent(List.of(y2, other, x3, small2, x1, smallOther, y1, x2, small1));

        // then
        assertThat(actual).containsExactlyInAnyOrder(List.of(x1, x2, x3), List.of(y1, y2), List.of(small1, small2));
    }

    @Test
    void shouldNotGroupFilesWithSameHeadButDifferentTail() throws IOException {
        // given
        byte[] bytes = random(FileCompare.HEAD_SIZE + 10, 20);
        Path a = write("a", bytes);
        bytes[FileCompare.HEAD_SIZE + 5]++;
        Path b = write("b", bytes);

        // then
        assertThat(underTest.groupBySameContent(List.of(a, b))).isEmpty();
    }

    @Test
    void shouldGroupFilesOfExactlyHeadSize() throws IOException {
        // given
        Path a = write("a", random(FileCompare.HEAD_SIZE, 21));
        Path b = write("b", random(FileCompare.HEAD_SIZE, 21));
        Path c = write("c", random(FileCompare.HEAD_SIZE, 22));

        // then
        assertThat(underTest.groupBySameContent(List.of(c, b, a))).containsExactly(List.of(a, b));
    }

    @Test
    void shouldUseUpdatedBufferSize() {
        // when
        underTest.updateBufferSize(1500);

        // then
        assertThat(underTest.getInternalBufferSize()).isEqualTo(2048);
    }

    private void assertComparison(boolean expected) throws IOException {
        for (int bufferSize : new int[]{1024, 64 * 1024, 16 * MB}) {
            underTest.updateBufferSize(bufferSize);
            assertThat(underTest.sameContent(pathA, pathB)).as("buffer %d", bufferSize).isEqualTo(expected);
            assertThat(underTest.sameContent(pathB, pathA)).as("buffer %d", bufferSize).isEqualTo(expected);
            assertThat(underTest.groupBySameContent(List.of(pathB, pathA))).as("buffer %d", bufferSize).isEqualTo(expected ? List.of(List.of(pathA, pathB)) : List.of());
        }
    }

    private Path write(String name, byte[] bytes) throws IOException {
        return Files.write(root.resolve(name), bytes);
    }

    private static byte[] random(int size, int seed) {
        byte[] bytes = new byte[size];
        new Random(seed).nextBytes(bytes);
        return bytes;
    }

    private static InputStream oneByteAtATime(byte[] bytes) {
        return new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(len, 1));
            }
        };
    }
}
