package com.nilcaream.cptidy;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.nio.file.StandardOpenOption.READ;

// Every result is exact. Each file is read sequentially in large chunks, which keeps seeks rare on an HDD.
@Singleton
public class FileCompare {

    static final int HEAD_SIZE = 1024 * 1024;

    private byte[] internalBufferA = new byte[16 * 1024 * 1024];
    private byte[] internalBufferB = new byte[16 * 1024 * 1024];

    public void updateBufferSize(int bufferSize) {
        int size = Math.max(1024, 1024 * (int) (Math.ceil(bufferSize / 1024.0)));
        internalBufferA = new byte[size];
        internalBufferB = new byte[size];
    }

    // One pair: reads both files alternately, one buffer at a time, and stops at the first difference.
    public boolean sameContent(Path pathA, Path pathB) throws IOException {
        if (areExplicitlyDifferent(pathA, pathB)) {
            return false;
        }
        try (InputStream inputStreamA = Files.newInputStream(pathA, READ); InputStream inputStreamB = Files.newInputStream(pathB, READ)) {
            return sameContent(inputStreamA, inputStreamB);
        }
    }

    boolean sameContent(InputStream inputStreamA, InputStream inputStreamB) throws IOException {
        while (true) {
            int bytesReadA = inputStreamA.readNBytes(internalBufferA, 0, internalBufferA.length);
            int bytesReadB = inputStreamB.readNBytes(internalBufferB, 0, internalBufferB.length);
            if (bytesReadA != bytesReadB || !Arrays.equals(internalBufferA, 0, bytesReadA, internalBufferB, 0, bytesReadB)) {
                return false;
            } else if (bytesReadA < internalBufferA.length) {
                return true;
            }
        }
    }

    // Many files: groups by size, then by a hash of the first megabyte, then by a hash of the whole file.
    // Each file is read at most twice (its head, then all of it) and only while it still has a possible twin.
    // Returns the groups of two or more files with the same content, each sorted by path.
    public List<List<Path>> groupBySameContent(Collection<Path> paths) throws IOException {
        Map<Long, List<Path>> sizeToPaths = new TreeMap<>();
        for (Path path : paths) {
            sizeToPaths.computeIfAbsent(Files.size(path), k -> new ArrayList<>()).add(path);
        }

        List<List<Path>> result = new ArrayList<>();
        for (Map.Entry<Long, List<Path>> entry : sizeToPaths.entrySet()) {
            for (List<Path> sameHead : groupByHash(entry.getValue(), HEAD_SIZE)) {
                if (entry.getKey() <= HEAD_SIZE) {
                    result.add(sameHead);
                } else {
                    result.addAll(groupByHash(sameHead, Long.MAX_VALUE));
                }
            }
        }
        return result;
    }

    private List<List<Path>> groupByHash(List<Path> paths, long limit) throws IOException {
        Map<String, List<Path>> hashToPaths = new LinkedHashMap<>();
        if (paths.size() > 1) {
            for (Path path : paths.stream().sorted().toList()) {
                hashToPaths.computeIfAbsent(hash(path, limit), k -> new ArrayList<>()).add(path);
            }
        }
        return hashToPaths.values().stream().filter(group -> group.size() > 1).toList();
    }

    private String hash(Path path, long limit) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream inputStream = Files.newInputStream(path, READ)) {
            long remaining = limit;
            int bytesRead;
            while (remaining > 0 && (bytesRead = inputStream.readNBytes(internalBufferA, 0, (int) Math.min(internalBufferA.length, remaining))) > 0) {
                digest.update(internalBufferA, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean areExplicitlyDifferent(Path pathA, Path pathB) throws IOException {
        return !Files.exists(pathA) || !Files.exists(pathB) || Files.size(pathA) != Files.size(pathB);
    }

    public int getInternalBufferSize() {
        return internalBufferA.length;
    }
}
