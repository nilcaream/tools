package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static java.nio.file.StandardOpenOption.READ;

@Singleton
public class FileCompare {

    private final MessageDigest digest;

    private byte[] internalBufferA = new byte[16 * 1024 * 1024];
    private byte[] internalBufferB = new byte[16 * 1024 * 1024];

    @Inject
    private Logger logger;

    {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void updateBufferSize(int bufferSize) {
        int size = Math.max(1024, 1024 * (int) (Math.ceil(bufferSize / 1024.0)));
        internalBufferA = new byte[size];
        internalBufferB = new byte[size];
    }

    public boolean byHash(Path pathA, Path pathB, int bufferSize) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else {
            return Arrays.equals(hash(pathA, bufferSize), hash(pathB, bufferSize));
        }
    }

    public boolean byteByByte(Path pathA, Path pathB, int bufferSize) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else {
            byte[] bufferA = new byte[bufferSize];
            byte[] bufferB = new byte[bufferSize];

            return byteByByteWithProvidedBuffers(pathA, pathB, bufferA, bufferB);
        }
    }

    public boolean fast(Path pathA, Path pathB) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else if (Files.size(pathA) < internalBufferA.length) {
            return byteByByteWithProvidedBuffers(pathA, pathB, internalBufferA, internalBufferB);
        } else {
            long fileSize = Files.size(pathA);
            long bufferSize = internalBufferA.length / 4;

            try (FileChannel channelA = (FileChannel) Files.newByteChannel(pathA); FileChannel channelB = (FileChannel) Files.newByteChannel(pathB)) {
                if (isNotEqual(channelA, channelB, 0, bufferSize)) { // start
                    logger.infoStat("diff start", pathA, "<->", pathB);
                    return false;
                } else if (isNotEqual(channelA, channelB, fileSize - bufferSize, fileSize)) { // end
                    logger.infoStat("diff end", pathA, "<->", pathB);
                    return false;
                } else if (isNotEqual(channelA, channelB, fileSize / 2 - bufferSize, fileSize / 2 + bufferSize)) { // middle
                    logger.infoStat("diff middle", pathA, "<->", pathB);
                    return false;
                }
            }
            return true;
        }
    }

    private boolean isNotEqual(FileChannel channelA, FileChannel channelB, long start, long end) throws IOException {
        MappedByteBuffer byteBufferA = channelA.map(FileChannel.MapMode.READ_ONLY, start, end - start);
        MappedByteBuffer byteBufferB = channelB.map(FileChannel.MapMode.READ_ONLY, start, end - start);
        return !byteBufferA.equals(byteBufferB);
    }

    public boolean byteByByte(Path pathA, Path pathB, byte[] bufferA, byte[] bufferB) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else {
            return byteByByteWithProvidedBuffers(pathA, pathB, bufferA, bufferB);
        }
    }

    public boolean byteByByte(Path pathA, Path pathB) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else {
            return byteByByteWithProvidedBuffers(pathA, pathB, internalBufferA, internalBufferB);
        }
    }

    private boolean byteByByteWithProvidedBuffers(Path pathA, Path pathB, byte[] bufferA, byte[] bufferB) throws IOException {
        if (bufferA.length != bufferB.length) {
            throw new IllegalArgumentException("Buffers have different sizes: " + bufferA.length + " and " + bufferB.length);
        } else if (bufferA == bufferB) {
            throw new IllegalArgumentException("Buffers reference the same array");
        } else {
            int bytesReadA = 0;
            int bytesReadB;

            try (InputStream inputStreamA = Files.newInputStream(pathA, READ); InputStream inputStreamB = Files.newInputStream(pathB, READ)) {
                while (bytesReadA != -1) {
                    bytesReadA = inputStreamA.read(bufferA);
                    bytesReadB = inputStreamB.read(bufferB);
                    if (bytesReadA != bytesReadB) {
                        return false;
                    } else {
                        if (bytesReadA != -1 && !Arrays.equals(bufferA, 0, bytesReadA, bufferB, 0, bytesReadA)) {
                            return false;
                        }
                    }
                }
            }

            return true;
        }
    }

    // https://codereview.stackexchange.com/a/90152
    public boolean byByteChannel(final Path pathA, final Path pathB, int bufferSize) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        } else {
            long size = Files.size(pathA);

            try (FileChannel channelA = (FileChannel) Files.newByteChannel(pathA); FileChannel channelB = (FileChannel) Files.newByteChannel(pathB)) {
                for (long position = 0; position < size; position += bufferSize) {
                    MappedByteBuffer bufferA = toBuffer(channelA, position, size, bufferSize);
                    MappedByteBuffer bufferB = toBuffer(channelB, position, size, bufferSize);
                    if (!bufferA.equals(bufferB)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private MappedByteBuffer toBuffer(FileChannel channel, long position, long size, long bufferSize) throws IOException {
        long end = Math.min(size, position + bufferSize);
        long length = end - position;
        return channel.map(FileChannel.MapMode.READ_ONLY, position, length);
    }

    private byte[] hash(Path path, int bufferSize) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path, READ)) {
            int bytesRead;
            byte[] buffer = new byte[bufferSize];

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return digest.digest();
    }

    private boolean areExplicitlyDifferent(Path pathA, Path pathB) throws IOException {
        return !Files.exists(pathA) || !Files.exists(pathB) || Files.size(pathA) != Files.size(pathB);
    }

    public int getInternalBufferSize() {
        return internalBufferA.length;
    }
}
