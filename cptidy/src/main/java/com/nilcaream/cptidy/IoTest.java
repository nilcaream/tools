package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.nio.file.StandardOpenOption.*;

@Singleton
public class IoTest {

    @Inject
    private Logger logger;

    private FileCompare fileCompare = new FileCompare();

    public void test2(Path root, int fileSize, int bufferSize) throws IOException {
        Path test = testCreate(root, fileSize);
        Path copy1 = Files.createTempFile(root, "cptidy-copy-1-", ".tmp").toAbsolutePath();
        Files.copy(test, copy1, StandardCopyOption.REPLACE_EXISTING);
        Path copy2 = Files.createTempFile(root, "cptidy-copy-2-", ".tmp").toAbsolutePath();
        Files.copy(test, copy2, StandardCopyOption.REPLACE_EXISTING);

        fast(copy1, copy2);
        byteByByte(copy1, copy2, bufferSize);
        Files.delete(copy1);
        Files.delete(copy2);
    }

    public void test(Path root, int fileSize, int bufferSize) throws IOException {
        Path pathA = testCreate(root, fileSize);
        Path pathB = testCreate(root, fileSize);
        createCacheClearFiles(root, fileSize);
        byteByByte(pathA, pathB, bufferSize);

        // -----
        pathA = testCreate(root, fileSize);
        pathB = testCreate(root, fileSize);
        createCacheClearFiles(root, fileSize);
        byHash(pathA, pathB, bufferSize);

        // -----
        pathA = testCreate(root, fileSize);
        pathB = testCreate(root, fileSize);
        createCacheClearFiles(root, fileSize);
        byByteChannel(pathA, pathB, bufferSize);

        // -----
        pathA = testCreate(root, fileSize);
        pathB = testCreate(root, fileSize);
        createCacheClearFiles(root, fileSize);
        byteByByte(pathA, pathB, bufferSize);

        // -----
        pathA = testCreate(root, fileSize);
        pathB = testCreate(root, fileSize);
        createCacheClearFiles(root, fileSize);
        fast(pathA, pathB);
    }

    private void createCacheClearFiles(Path root, int fileSize) throws IOException {
        for (int i = 0; i < 4; i++) {
            testCreate(root, fileSize);
        }
    }

    private Path testCreate(Path path, int size) throws IOException {
        Path file = Files.createTempFile(path, "cptidy-", ".tmp").toAbsolutePath();

        long time = nanoTime();
        try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file, WRITE, TRUNCATE_EXISTING, READ)) {
            MappedByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, size);
            byte b = 0;
            for (int i = 0; i < size; i++, b++) {
                buffer.put(b);
            }
        }
        time = nanoTime() - time;
        logger.info("create", format(": % 10.3f ms | %7.3f MB | %s", time / 1000000.0, size / (1024.0 * 1024.0), file));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "delete-" + file.getFileName().toString()));
        return file;
    }

    private void byByteChannel(Path pathA, Path pathB, int bufferSize) throws IOException {
        double size = Files.size(pathA) / (1024.0 * 1024.0);
        long time = nanoTime();
        boolean result = fileCompare.byByteChannel(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        logger.info("channel", format(": % 10.3f ms | %7.3f MB | %s", time / 1000000.0, size, result));
    }

    private void byteByByte(Path pathA, Path pathB, int bufferSize) throws IOException {
        double size = Files.size(pathA) / (1024.0 * 1024.0);
        long time = nanoTime();
        boolean result = fileCompare.byteByByte(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        logger.info("by byte", format(": % 10.3f ms | %7.3f MB | %s", time / 1000000.0, size, result));
    }

    private void byHash(Path pathA, Path pathB, int bufferSize) throws IOException {
        double size = Files.size(pathA) / (1024.0 * 1024.0);
        long time = nanoTime();
        boolean result = fileCompare.byHash(pathA, pathB, bufferSize);
        time = nanoTime() - time;
        logger.info("by hash", format(": % 10.3f ms | %7.3f MB | %s", time / 1000000.0, size, result));
    }

    private void fast(Path pathA, Path pathB) throws IOException {
        double size = Files.size(pathA) / (1024.0 * 1024.0);
        long time = nanoTime();
        boolean result = fileCompare.fast(pathA, pathB);
        time = nanoTime() - time;
        logger.info("fast", format(": % 10.3f ms | %7.3f MB | %s", time / 1000000.0, size, result));
    }
}
