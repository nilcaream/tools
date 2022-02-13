package com.nilcaream.cptidy;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class IoService {

    private static final Pattern SOURCE_FILE_NAME_PATTERN = Pattern.compile(".*(20[0-9]{2})([01][0-9])([0-9]{2})([^0-9]).+\\.(.{3,4})");
    private static final Pattern EXIF_DATE_PATTERN = Pattern.compile(".*exif.*date.*(20[012][0-9]).([01][0-9]).([0123][0-9]).*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "mp4", "mpeg", "avi", "mov");

    private Logger logger = LoggerFactory.getLogger(getClass());
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM");

    private boolean dryRun;
    private Statistics statistics = new Statistics();

    public String dateFromExif(Path source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(source.toFile());
            Iterable<Directory> directories = metadata.getDirectories();
            for (Directory directory : directories) {
                String date = dateFromExif1(source, directory);
                if (date == null) {
                    date = dateFromExif2(source, directory);
                }
                if (date != null) {
                    return date;
                }
            }
        } catch (ImageProcessingException | IOException e) {
            error("exif-error", source);
        }
        return null;
    }

    private String dateFromExif1(Path source, Directory directory) {
        return directory.getTags().stream()
                .map(Object::toString)
                .map(EXIF_DATE_PATTERN::matcher)
                .filter(Matcher::matches)
                .peek(m -> info("exif 1", source, m.group()))
                .map(m -> m.group(1) + "-" + m.group(2))
                .findFirst()
                .orElse(null);
    }

    private String dateFromExif2(Path source, Directory directory) {
        return directory.getTags().stream()
                .filter(t -> directory.getObject(t.getTagType()).getClass() == Date.class)
                .filter(t -> t.getTagName().toLowerCase().contains("creation"))
                .peek(t -> info("exif 2", source, t))
                .map(t -> directory.getDate(t.getTagType()))
                .map(d -> dateFormat.format(d))
                .findFirst()
                .orElse(null);
    }

    public Path buildMatchingTarget(Path source, String targetRoot) {
        String sourceName = source.getFileName().toString().toLowerCase();
        Matcher matcher = SOURCE_FILE_NAME_PATTERN.matcher(sourceName);
        Path result = null;

        String extension = Files.getFileExtension(sourceName);

        if (matcher.matches()) {
            if (EXTENSIONS.contains(extension)) {
                result = Paths.get(targetRoot, matcher.group(1) + "-" + matcher.group(2), sourceName);
            } else {
                warning("extension", source);
            }
        } else if (EXTENSIONS.contains(extension)) {
            String date = dateFromExif(source);
            if (date != null) {
                result = Paths.get(targetRoot, date, sourceName);
            }
        } else {
            debug("no match", source);
        }

        return result;
    }

    public boolean hasSameContent(Path source, Path target) throws IOException {
        File sourceFile = source.toFile();
        File targetFile = target.toFile();
        if (sourceFile.exists() && targetFile.exists() && sourceFile.length() == targetFile.length()) {
            String sumSource = checkSum(sourceFile);
            String sumTarget = checkSum(targetFile);
            if (sumSource.equals(sumTarget)) {
                info("duplicate", source,target);
                return true;
            }
        }
        return false;
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

    public void info(String status, Object message) {
        logger.info("{} {}", formatStatus(status), message);
    }

    private void info(String status, Object messageA, Object messageB) {
        logger.info("{} {} > {}", formatStatus(status), messageA, messageB);
    }

    public void debug(String status, Object message) {
        logger.debug("{} {}", formatStatus(status), message);
    }

    private void debug(String status, Object messageA, Object messageB) {
        logger.debug("{} {} : {}", formatStatus(status), messageA, messageB);
    }

    private void warning(String status, Object message) {
        logger.warn("{} {}", formatStatus(status), message);
    }

    public void error(String status, Object message) {
        logger.error("{} {}", formatStatus(status), message);
    }

    private String formatStatus(String status) {
        String upper = status.toUpperCase().replace(" ", "-").trim();
        return (upper + "                                ").substring(0, 10);
    }

    public void delete(Path source) {
        info("delete", source);
        statistics.add("delete", source.toFile().length());

        if (!dryRun) {
           // source.toFile().delete();
        }
    }

    public void move(Path source, Path target) throws IOException {
        info("move", source, target);
        statistics.add("move", source.toFile().length());

        if (!dryRun) {
            target.getParent().toFile().mkdirs();
            Files.move(source.toFile(), target.toFile());
        }
    }

    public void moveAsNew(Path source, Path orgTarget) throws IOException {
        Path target = buildUniquePath(orgTarget);
        info("move new", source, target);
        statistics.add("move new", source.toFile().length());

        if (!dryRun) {
            target.getParent().toFile().mkdirs();
            Files.move(source.toFile(), target.toFile());
        }
    }

    public boolean isSameFile(Path source, Path target) throws IOException {
        return source.toFile().exists() && target.toFile().exists() && java.nio.file.Files.isSameFile(source, target);
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Statistics getStatistics() {
        return statistics;
    }
}
