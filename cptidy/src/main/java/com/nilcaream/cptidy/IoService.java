package com.nilcaream.cptidy;

import com.nilcaream.utilargs.Option;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class IoService {

    private static final Pattern SOURCE_FILE_NAME_PATTERN = Pattern.compile(".*(20[0-9]{2})([01][0-9])([0-9]{2})([^0-9]).+\\.(.{3,4})");
    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "mp4", "mpeg", "avi", "mov");

    @Inject
    private Logger logger;

    @Inject
    private ExifService exifService;

    @Inject
    private Io io;

    @Option(value = "d", alternative = "delete")
    private boolean delete = false;

    @Option(value = "m", alternative = "move")
    private boolean move = false;

    @Option(value = "c", alternative = "copy")
    private boolean copy = false;

    private Statistics statistics = new Statistics("statistics");

    public Path buildMatchingTarget(Path source, Path targetRoot) throws IOException {
        String sourceName = source.getFileName().toString().toLowerCase().replaceAll("[^.a-z0-9_-]+", "-");
        Matcher matcher = SOURCE_FILE_NAME_PATTERN.matcher(sourceName);
        Path result = null;

        String extension = io.getFileExtension(sourceName);

        if (matcher.matches()) {
            if (EXTENSIONS.contains(extension)) {
                result = targetRoot.resolve(matcher.group(1) + "-" + matcher.group(2)).resolve(sourceName);
            } else {
                logger.warn("extension", source);
            }
        } else if (EXTENSIONS.contains(extension)) {
            String date = exifService.getDate(source);
            if (date != null) {
                result = targetRoot.resolve(date).resolve(sourceName);
            }
        }

        if (EXTENSIONS.contains(extension)) {
            statistics.add("ok-extension", io.size(source));
        }
        if (result != null) {
            statistics.add("has-date", io.size(source));
        }

        statistics.add("total", io.size(source));
        return result;
    }

    public boolean haveSameContent(Path source, Path target) throws IOException {
        if (Files.exists(source) && Files.exists(target) && io.size(source) == io.size(target)) {
            return io.haveSameContent(source, target);
            // return io.hash(source).equals(io.hash(target));
        } else {
            return false;
        }
    }

    private Path buildUniquePath(Path path) {
        String fileName = path.getFileName().toString();
        String name = io.getFileName(fileName);
        String extension = io.getFileExtension(fileName);
        Path root = path.getParent();

        int index = 0;
        Path result = path;
        while (Files.exists(result)) {
            result = root.resolve(name + "-" + index + "." + extension);
            index++;
        }
        return result;
    }

    public void delete(Path path) throws IOException {
        logger.info("delete", path);
        statistics.add("delete", io.size(path));

        if (delete) {
            io.delete(path);
        }
    }

    public void move(Path source, Path target) throws IOException {
        if (source.toAbsolutePath().getParent().equals(target.toAbsolutePath().getParent())) {
            logger.info("rename", source, ">", target);
            statistics.add("rename", io.size(source));
        } else {
            logger.info("move", source, ">", target);
            statistics.add("move", io.size(source));
        }

        if (move) {
            io.move(source, target);
        }
    }

    public Path moveAsNew(Path source, Path orgTarget) throws IOException {
        Path target = buildUniquePath(orgTarget);
        if (source.toAbsolutePath().getParent().equals(target.toAbsolutePath().getParent())) {
            logger.info("rename new", source, ">", target);
            statistics.add("rename new", io.size(source));
        } else {
            logger.info("move new", source, ">", target);
            statistics.add("move new", io.size(source));
        }

        if (move) {
            io.move(source, target);
        }
        return target;
    }

    public Path copyAsNew(Path source, Path orgTarget) throws IOException {
        Path target = buildUniquePath(orgTarget);
        logger.info("copy new", source, ">", target);
        statistics.add("copy new", io.size(source));

        if (copy) {
            io.copy(source, target);
        }
        return target;
    }

    public void copy(Path source, Path target) throws IOException {
        logger.info("copy", source, ">", target);
        statistics.add("copy", io.size(source));

        if (copy) {
            io.copy(source, target);
        }
    }

    public void deleteEmpty(Path root, Path orgPath) throws IOException {
        Path path = orgPath.normalize().toAbsolutePath();
        while (Files.exists(path) && Files.isDirectory(path) && Files.list(path).findAny().isEmpty() && path.startsWith(root) && !path.equals(root)) {
            logger.info("delete empty", path);
            statistics.add("delete empty", 0);

            if (delete) {
                io.delete(path);
            }
            path = path.getParent().toAbsolutePath();
        }
    }

    public boolean isSameFile(Path source, Path target) throws IOException {
        return io.isSameFile(source, target);
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public Statistics resetStatistics(String id) {
        Statistics previous = statistics;
        statistics = new Statistics(id);
        return previous;
    }

    public void reportNoMatch(Path path) throws IOException {
        logger.info("no match", path);
        statistics.add("no match", io.size(path));
    }

    public void reportOkLocation(Path path) throws IOException {
        logger.debug("ok location", path);
        statistics.add("ok location", io.size(path));
    }

    public Path buildCopyTarget(Path source, Path sourceRoot, Path targetRoot) {
        return targetRoot.resolve(sourceRoot.relativize(source));
    }

    // --------------------------------

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isMove() {
        return move;
    }

    public void setMove(boolean move) {
        this.move = move;
    }

    public boolean isCopy() {
        return copy;
    }

    public void setCopy(boolean copy) {
        this.copy = copy;
    }
}
