package com.nilcaream.cptidy;

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
            .compile("(20[0-9]{2})([01][0-9])([0-9]){2}([^0-9]).+\\.(.{3,4})");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "mp4", "mpeg");

    private Logger logger = LoggerFactory.getLogger(getClass());

    public Path buildTarget(Path source, String targetRoot) {
        String sourceName = source.getFileName().toString();
        Matcher matcher = TARGET_FILE_NAME.matcher(sourceName.toLowerCase());
        Path result = null;

        if (matcher.matches()) {
            String extension = matcher.group(5);
            if (EXTENSIONS.contains(extension)) {
                result = Paths.get(targetRoot, matcher.group(1) + "-" + matcher.group(2), sourceName);
            } else {
                log("unsupported extension", source);
            }
        } else {
            log("match not found", source);
        }

        return result;
    }

    public Path checkDuplicate(Path source, Path target) throws IOException {
        Path result = null;
        if (target.toFile().exists()) {
            if (checkSum(source).equals(checkSum(target))) {
                log("duplicate found", source, target);
            } else {
                result = uniqueName(target);
                log("renamed", result);
            }
        } else {
            result = target;
        }
        return result;
    }

    private Path uniqueName(Path path) {
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

    private String checkSum(Path path) throws IOException {
        return Files.asByteSource(path.toFile()).hash(Hashing.goodFastHash(128)).toString();
    }

    private void log(String status, Path source) {
        logger.info("{} {}", formatStatus(status), source.toString());
    }

    private void log(String status, Path source, Path target) {
        logger.info("{} {} > {}", formatStatus(status), source.toString(), target.toString());
    }

    private String formatStatus(String status) {
        String upper = status.toUpperCase().replace(" ", "-").trim();
        return (upper + "                                ").substring(0, 20);
    }

	public void delete(Path source) {
        log("delete", source);
	}

	public void move(Path source, Path target) {
        log("move", source, target);
	}
}
