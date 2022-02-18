package com.nilcaream.cptidy;

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

    private MessageDigest digest;

    {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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

            int i;
            int bytesReadA = 0;
            int bytesReadB;

            try (InputStream inputStreamA = Files.newInputStream(pathA, READ); InputStream inputStreamB = Files.newInputStream(pathB, READ)) {
                while (bytesReadA != -1) {
                    bytesReadA = inputStreamA.read(bufferA);
                    bytesReadB = inputStreamB.read(bufferB);
                    if (bytesReadA != bytesReadB) {
                        return false;
                    } else {
                        for (i = 0; i < bytesReadA; i++) {
                            if (bufferA[i] != bufferB[i]) {
                                return false;
                            }
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

    private MappedByteBuffer toBuffer(FileChannel channel, long position, long size, int bufferSize) throws IOException {
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
}
