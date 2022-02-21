package com.nilcaream.cptidy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.nanoTime;
import static java.nio.file.StandardOpenOption.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class FileCompareTest {

    @InjectMocks
    private FileCompare underTest = new FileCompare();

    @Spy
    private Logger logger = new Logger();

    private Path pathA;
    private Path pathB;
    private String content;

    private static byte[] bufferA = new byte[4 * 1024 * 1024];
    private static byte[] bufferB = new byte[4 * 1024 * 1024];

    private Io io = new Io();

    private List<Path> toDelete = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        content = io.read(Paths.get("pom.xml"));
        assertThat(content).hasSizeGreaterThan(8000);
        pathA = Files.createTempFile("cptidy-", ".tmp");
        pathB = Files.createTempFile("cptidy-", ".tmp");
        toDelete.add(pathA);
        toDelete.add(pathB);
    }

    @AfterEach
    void tearDown() throws IOException {
        for (Path path : toDelete) {
            Files.deleteIfExists(path);
        }
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
    void shouldBeSameForLargeFile2() throws IOException {
        // given
        io.write(pathA, content + "a" + content);
        io.write(pathB, content + "a" + content);

        // then
        assertComparison(true);
    }

    @Test
    void shouldBeSameForLargeFile3() throws IOException {
        // given
        io.write(pathA, "a" + content + content);
        io.write(pathB, "a" + content + content);

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

    @Test
    void shouldFailOnWithArraysOfDifferentSize() throws IOException {
        // given
        byte[] bufferA = new byte[1024];
        byte[] bufferB = new byte[1025];

        io.write(pathA, content);
        io.write(pathB, content);

        // then
        assertThatThrownBy(() -> underTest.byteByByte(pathA, pathB, bufferA, bufferB)).hasMessageContaining("Buffers have different sizes");
    }

    @Test
    void shouldFailOnWithSameArrays() throws IOException {
        // given
        byte[] buffer = new byte[1024];

        io.write(pathA, content);
        io.write(pathB, content);

        // then
        assertThatThrownBy(() -> underTest.byteByByte(pathA, pathB, buffer, buffer)).hasMessageContaining("Buffers reference the same array");
    }

    @Test
    void shouldTestFastOnDifferentFilesNearBufferSize1() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size - 1, size - 10);
        pathB = create(pathA.getParent(), size - 1, size);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
        assertThat(underTest.fast(pathB, pathA)).isFalse();
    }

    @Test
    void shouldTestFastOnDifferentFilesNearBufferSize2() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size, size - 10);
        pathB = create(pathA.getParent(), size, size);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
        assertThat(underTest.fast(pathB, pathA)).isFalse();
    }

    @Test
    void shouldTestFastOnDifferentFilesNearBufferSize3() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size + 1, size - 10);
        pathB = create(pathA.getParent(), size + 1, size);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
        assertThat(underTest.fast(pathB, pathA)).isFalse();
    }

    @Test
    void shouldTestFastOnSameFilesNearBufferSize1() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size - 1, size - 10);
        pathB = create(pathA.getParent(), size - 1, size - 10);

        // then
        assertThat(underTest.fast(pathA, pathB)).isTrue();
        assertThat(underTest.fast(pathB, pathA)).isTrue();
    }

    @Test
    void shouldTestFastOnSameFilesNearBufferSize2() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size, size - 10);
        pathB = create(pathA.getParent(), size, size - 10);

        // then
        assertThat(underTest.fast(pathA, pathB)).isTrue();
        assertThat(underTest.fast(pathB, pathA)).isTrue();
    }

    @Test
    void shouldTestFastOnSameFilesNearBufferSize3() throws IOException {
        // given
        int size = underTest.getInternalBufferSize();
        pathA = create(pathA.getParent(), size + 1, size - 10);
        pathB = create(pathA.getParent(), size + 1, size - 10);

        // then
        assertThat(underTest.fast(pathA, pathB)).isTrue();
        assertThat(underTest.fast(pathB, pathA)).isTrue();
    }

    @Test
    void shouldTestFastOnDifferentFiles() throws IOException {
        // given
        pathA = create(pathA.getParent(), 64 * 1024 * 1024, 1024 * 1024);
        pathB = create(pathA.getParent(), 64 * 1024 * 1024, 128 * 1024 * 1024);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
    }

    @Test
    void shouldTestFastOnSameFiles() throws IOException {
        // given
        pathA = create(pathA.getParent(), 64 * 1024 * 1024, 1024 * 1024);
        pathB = create(pathA.getParent(), 64 * 1024 * 1024, 1024 * 1024);

        // then
        assertThat(underTest.fast(pathA, pathB)).isTrue();
    }

    @Test
    void shouldTestFastOnSlightlyDifferentFilesStart() throws IOException {
        // given
        pathA = create(pathA.getParent(), 64 * 1024 * 1024, 2 * 1024 * 1024);
        pathB = create(pathA.getParent(), 64 * 1024 * 1024, 2 * 1024 * 1024 + 1);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
    }

    @Test
    void shouldTestFastOnSlightlyDifferentFilesMiddle() throws IOException {
        // given
        pathA = create(pathA.getParent(), 64 * 1024 * 1024, 32 * 1024 * 1024);
        pathB = create(pathA.getParent(), 64 * 1024 * 1024, 32 * 1024 * 1024 + 1);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
    }

    @Test
    void shouldTestFastOnSlightlyDifferentFilesEnd() throws IOException {
        // given
        pathA = create(pathA.getParent(), 64 * 1024 * 1024, 63 * 1024 * 1024);
        pathB = create(pathA.getParent(), 64 * 1024 * 1024, 63 * 1024 * 1024 + 1);

        // then
        assertThat(underTest.fast(pathA, pathB)).isFalse();
    }

    void assertComparison(boolean expected) throws IOException {
        Random random = new Random(currentTimeMillis());
        for (int i = 2; i <= 1024 * 1024; i *= 2) {
            System.out.printf("Assert buffer %d for %s %s\n", i, pathA, pathB);
            assertThat(underTest.byByteChannel(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byByteChannel(pathB, pathA, i)).isEqualTo(expected);
            assertThat(underTest.byHash(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byHash(pathB, pathA, i)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathA, pathB, i)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathB, pathA, i)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathA, pathB, bufferA, bufferB)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathB, pathA, bufferA, bufferB)).isEqualTo(expected);

            assertThat(underTest.byteByByte(pathA, pathB)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathB, pathA)).isEqualTo(expected);

            random.nextBytes(bufferB);
            assertThat(underTest.byteByByte(pathB, pathA, bufferA, bufferB)).isEqualTo(expected);

            assertThat(underTest.byteByByte(pathA, pathB, bufferB, bufferA)).isEqualTo(expected);
            assertThat(underTest.byteByByte(pathB, pathA, bufferA, bufferB)).isEqualTo(expected);

            random.nextBytes(bufferA);
        }
        assertThat(underTest.fast(pathB, pathA)).isEqualTo(expected);
        assertThat(underTest.fast(pathA, pathB)).isEqualTo(expected);
    }

    private Path create(Path root, int size, int changeEvery) throws IOException {
        Path file = Files.createTempFile(root, "cptidy-", ".tmp").toAbsolutePath();
        toDelete.add(file);

        try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file, WRITE, TRUNCATE_EXISTING, READ)) {
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            byte b = 0;
            for (int i = 0; i < size; i++, b++) {
                if (i % changeEvery == 0) {
                    buffer.put((byte) (b + 1));
                } else {
                    buffer.put(b);
                }
            }
        }

        return file;
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