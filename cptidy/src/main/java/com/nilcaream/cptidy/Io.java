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
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

@Singleton
public class Io {

    public String read(Path path) throws IOException {
        return Files.readString(path);
    }

    public Path write(Path path, String content) throws IOException {
        createParentDirectories(path);
        Files.write(path, content.getBytes(), CREATE, TRUNCATE_EXISTING);
        return path;
    }

    public void copy(Path source, Path target) throws IOException {
        createParentDirectories(target);
        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
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
        if (Files.exists(source) && Files.exists(target)) {
            if (!Files.isRegularFile(source) || !Files.isRegularFile(target)) {
                throw new IllegalArgumentException("Symlinks are not supported " + source + ", " + target);
            } else {
                return source.toRealPath().toString().equals(target.toRealPath().toString()) || Files.isSameFile(source, target);
            }
        } else {
            return false;
        }
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
}
