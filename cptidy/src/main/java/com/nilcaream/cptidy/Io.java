package com.nilcaream.cptidy;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.nio.file.StandardOpenOption.*;

@Singleton
public class Io {

    public String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path));
    }

    public Path write(Path path, String content) throws IOException {
        createParentDirectories(path);
        Files.write(path, content.getBytes(), CREATE, TRUNCATE_EXISTING);
        return path;
    }

    public String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream inputStream = Files.newInputStream(path, READ)) {
                int bytesRead;
                byte[] buffer = new byte[1024];

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    public boolean haveSameContent(Path pathA, Path pathB) throws IOException {
        byte[] bufferA = new byte[16 * 1024];
        byte[] bufferB = new byte[16 * 1024];

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

    public void copy(Path source, Path target) throws IOException {
        createParentDirectories(target);
        Files.copy(source, target);
    }

    public void move(Path source, Path target) throws IOException {
        createParentDirectories(target);
        Files.move(source, target);
    }

    private void createParentDirectories(Path path) throws IOException {
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
    }

    public void delete(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    public long size(Path path) throws IOException {
        return Files.size(path);
    }

    public boolean isSameFile(Path source, Path target) throws IOException {
        return Files.exists(source) && Files.exists(target) && Files.isSameFile(source, target);
    }

    public List<String> getExif(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            Metadata metadata = ImageMetadataReader.readMetadata(inputStream);
            return StreamSupport.stream(metadata.getDirectories().spliterator(), false)
                    .map(Directory::getTags)
                    .flatMap(Collection::stream)
                    .map(Tag::toString)
                    .sorted()
                    .collect(Collectors.toList());
        } catch (ImageProcessingException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getFileName(String input) {
        int index = input.lastIndexOf(".");
        if (index == -1) {
            return input;
        } else {
            return input.substring(0, index);
        }
    }

    public String getFileExtension(String input) {
        int index = input.lastIndexOf(".");
        if (index == -1) {
            return "";
        } else {
            return input.substring(index + 1);
        }
    }
}
