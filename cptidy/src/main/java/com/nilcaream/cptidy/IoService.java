package com.nilcaream.cptidy;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Singleton;

import com.google.common.hash.Hashing;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IoService {

    private static final Pattern TARGET_FILE_NAME = Pattern
            .compile(".*(20[0-9]{2})([01][0-9])([0-9]){2}([^0-9]).+\\.(.{3,4})");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "mp4", "mpeg");

    private Logger logger = LoggerFactory.getLogger(getClass());

    private boolean dryRun = true;

    public Path buildMatchingTarget(Path source, String targetRoot) {
        String sourceName = source.getFileName().toString();
        Matcher matcher = TARGET_FILE_NAME.matcher(sourceName.toLowerCase());
        Path result = null;

        if (matcher.matches()) {
            String extension = matcher.group(5);
            if (EXTENSIONS.contains(extension)) {
                result = Paths.get(targetRoot, matcher.group(1) + "-" + matcher.group(2), sourceName);
            } else {
                info("extension", source);
            }
        } else {
            debug("no match", source);
        }

        return result;
    }

    public boolean hasSameContent(Path source, Path target) throws IOException {
        File sourceFile = source.toFile();
        File targetFile = target.toFile();
        return sourceFile.exists() && targetFile.exists() && sourceFile.length() == targetFile.length()
                && checkSum(sourceFile).equals(checkSum(targetFile));
    }

    public Path buildUniquePath(Path path) {
        String fileName = path.getFileName().toString();
        String name = Files.getNameWithoutExtension(fileName);
        String extension = Files.getFileExtension(fileName);
        Path root = path.getParent();

        int index = 0;
        Path result = path;
        while (result.toFile().exists()) {
            result = root.resolve(name + "-" + index + "." + extension);
            index++;
        }
        return result;
    }

    private String checkSum(File source) throws IOException {
        return Files.asByteSource(source).hash(Hashing.goodFastHash(128)).toString();
    }

    public void info(String status, Path source) {
        logger.info("{} {}", formatStatus(status), source.toString());
    }

    private void debug(String status, Path source) {
        logger.debug("{} {}", formatStatus(status), source.toString());
    }

    public void info(String status, Path source, Path target) {
        logger.info("{} {} > {}", formatStatus(status), source.toString(), target.toString());
    }

    private String formatStatus(String status) {
        String upper = status.toUpperCase().replace(" ", "-").trim();
        return (upper + "                                ").substring(0, 10);
    }

    public void delete(Path source) {
        info("delete", source);
        if (!dryRun) {
            source.toFile().delete();
        }
    }

    public void move(Path source, Path target) throws IOException {
        info("move", source, target);
        if (!dryRun) {
            target.getParent().toFile().mkdirs();
            Files.move(source.toFile(), target.toFile());
        }
    }

    public boolean isSameFile(Path source, Path target) throws IOException {
        return source.toFile().exists() && target.toFile().exists() && java.nio.file.Files.isSameFile(source, target);
    }
}
