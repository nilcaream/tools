package com.nilcaream.cptidy;

import com.nilcaream.utilargs.Option;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

@Singleton
public class IoService {

    @Inject
    private Logger logger;

    @Inject
    private NameResolver nameResolver;

    @Inject
    private FileCompare fileCompare;

    @Inject
    private Io io;

    @Option(alternative = "delete")
    private boolean delete = false;

    @Option(alternative = "move")
    private boolean move = false;

    @Option(alternative = "copy")
    private boolean copy = false;

    @Option(alternative = "time")
    private boolean time = false;

    @Option(alternative = "fast")
    private boolean fast = false;

    private Set<String> ignoredFiles = new HashSet<>();

    // 1 - yyyy, 2 - MM, 3 - dd
    private static final Pattern NAME_EXTENSION = Pattern.compile("([12][0-9]{3})([01][0-9])([0123][0-9])-.+");
    private static final Instant DEFAULT_TIMESTAMP = Instant.parse("2000-01-01T12:00:00.00Z");
    private static final Pattern COPY_SUFFIX = Pattern.compile("(.+?)(-[0-9])*");

    public Path buildMatchingTarget(Path source, Path targetRoot) throws IOException {
        Path result = ofNullable(nameResolver.resolve(source)).map(r -> r.resolve(targetRoot)).orElse(null);
        logger.stat("total", source);
        return result;
    }

    public boolean haveSameContent(Path source, Path target) throws IOException {
        if (fast) {
            return fileCompare.fast(source, target);
        } else {
            return fileCompare.byteByByte(source, target);
        }
    }

    public boolean haveSameAttributes(Path source, Path target) throws IOException {
        assertExists(source, target);
        assertDifferent(source, target);
        return getAttributes(source).equals(getAttributes(target));
    }

    private FileTime getCorrectTimestamp(Path path) throws IOException {
        FileTime fileTime = getCreateTime(path);
        String name = path.getFileName().toString();
        String date = fileTime.toString().substring(0, 10).replace("-", ""); // yyyy-MM-ddTHH:mm:ss -> yyyyMMdd
        return name.contains(date) ? fileTime : null;
    }

    private Instant getDateFromName(String name) {
        Matcher matcher = NAME_EXTENSION.matcher(name);
        Instant result = null;
        if (matcher.matches()) {
            try {
                result = Instant.parse(matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3) + "T12:00:00.00Z");
            } catch (DateTimeParseException e) {
                logger.warn("invalid-date", name);
            }
        }
        return result;
    }

    private void setTimestampFromName(Path path) throws IOException {
        Instant instant = ofNullable(getDateFromName(path.getFileName().toString())).orElse(DEFAULT_TIMESTAMP);
        setTimestamp(path, FileTime.from(instant));
    }

    public void fixTimestamps(Path source, Path target) throws IOException {
        FileTime sourceFileTime = getCorrectTimestamp(source);
        FileTime targetFileTime = getCorrectTimestamp(target);

        if (sourceFileTime != null) {
            setTimestamp(target, sourceFileTime);
        } else if (targetFileTime != null) {
            setTimestamp(source, targetFileTime);
        } else {
            setTimestampFromName(source);
            setTimestampFromName(target);
        }
    }

    private FileTime getCreateTime(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class).creationTime();
    }

    private void setTimestamp(Path path, FileTime fileTime) throws IOException {
        String from = asString(getCreateTime(path));
        String to = asString(fileTime);
        if (fileTime.toInstant().equals(DEFAULT_TIMESTAMP)) {
            logger.infoStat("time-default", path, ":", from, "->", to);
        } else {
            logger.infoStat("time-fix", path, ":", from, "->", to);
        }
        if (time) {
            try {
                Files.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(fileTime, null, fileTime);
            } catch (IOException ignored) {
                logger.infoStat("time-no-fix", path, "Could not set file times");
            }
        }
    }

    private String asString(FileTime fileTime) {
        return fileTime.toString().replaceAll("\\.\\d+", "").replaceAll("[TZ]", " ").trim();
    }

    private String getAttributes(Path path) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        String creation = asString(attr.creationTime());
        return format("%s/%s %d %s", path.getParent().getFileName().toString(), path.getFileName().toString(), Files.size(path), creation);
    }

    public void delete(Path path) throws IOException {
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    private void assertExists(Path... paths) {
        for (Path path : paths) {
            if (!Files.exists(path)) {
                throw new IllegalStateException(path + " does not exists for deletion");
            }
        }
    }

    private void assertDifferent(Path... paths) throws IOException {
        if (paths == null || paths.length < 2) {
            throw new IllegalArgumentException("Should provide at least 2 paths ");
        }
        for (int a = 0; a < paths.length; a++) {
            for (int b = a + 1; b < paths.length; b++) {
                Path pathA = paths[a];
                Path pathB = paths[b];
                if (io.isSameFile(pathA, pathB)) {
                    throw new IllegalArgumentException("Should provide different paths " + pathA + ", " + pathB);
                }

            }
        }
    }

    public void deleteOne(Path fileA, Path fileB) throws IOException {
        assertExists(fileA, fileB);
        assertDifferent(fileA, fileB);

        Path path = selectToDelete(fileA, fileB);
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    public void retainOne(Collection<Path> paths) throws IOException {
        if (paths.size() < 2) {
            throw new IllegalArgumentException("Should provide more than 1 path");
        }
        assertExists(paths.toArray(new Path[]{}));
        assertDifferent(paths.toArray(new Path[]{}));

        Map<Path, Integer> pathToScore = paths.stream().collect(Collectors.toMap(p -> p, p -> 0));

        for (Path pathA : paths) {
            for (Path pathB : paths) {
                if (!pathA.equals(pathB)) {
                    if (selectToDelete(pathA, pathB).equals(pathA)) {
                        pathToScore.put(pathA, pathToScore.get(pathA) + 1);
                    } else {
                        pathToScore.put(pathB, pathToScore.get(pathB) + 1);
                    }
                }
            }
        }

        Path best = pathToScore.entrySet().stream()
                .min(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow();

        for (Path path : paths) {
            if (path == best) {
                logger.infoStat("retain", path, ": score", pathToScore.get(path));
            } else {
                logger.infoStat("delete", path, ": score", pathToScore.get(path));

                if (delete) {
                    io.delete(path);
                }
            }
        }
    }

    private String removeCopySuffix(String text) {
        Matcher matcher = COPY_SUFFIX.matcher(text);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return text;
        }
    }

    private Path selectToDelete(Path fileA, Path fileB) {
        Path result;

        String orgNameA = getName(fileA.getFileName().toString());
        String orgNameB = getName(fileB.getFileName().toString());
        String nameA = removeCopySuffix(orgNameA);
        String nameB = removeCopySuffix(orgNameB);

        if (nameA.length() > nameB.length()) {
            result = fileB; // after shortening B is shorter
        } else if (nameA.length() < nameB.length()) {
            result = fileA; // after shortening A is shorter
        } else if (orgNameA.length() > orgNameB.length()) { // same short length; select A as it had only copy suffixes
            result = fileA;
        } else if (orgNameA.length() < orgNameB.length()) { // same short length; select B as it had only copy suffixes
            result = fileB;
        } else if (nameA.compareTo(nameB) < 0) { // same length before and after shortening so select e.g. 20101231 instead of 20101200
            result = fileA;
        } else {
            result = fileB;
        }

        return result;
    }

    // TODO - duplicate from NameResolver. Consider refactoring.
    // test.txt -> test
    private String getName(String nameExtension) {
        int index = nameExtension.lastIndexOf(".");
        if (index == -1) {
            return nameExtension;
        } else {
            return nameExtension.substring(0, index);
        }
    }

    public void move(Path source, Path orgTarget) throws IOException {
        if (io.isSameFile(source, orgTarget)) {
            throw new IOException("Both paths are equal for move " + source + " > " + orgTarget);
        }

        Path target = nameResolver.buildUniquePath(orgTarget);

        boolean sameParent = source.getParent().equals(target.getParent());
        boolean sameName = source.getFileName().equals(target.getFileName());

        if (sameParent && sameName) {
            throw new IllegalStateException("Parents and names cannot be same at this point " + source + " > " + target);
        } else if (sameParent) {
            logger.infoStat("rename", source, ">", target);
        } else {
            logger.infoStat("move", source, ">", target);
        }

        if (move) {
            io.move(source, target);
        }
    }

    public Path copy(Path source, Path orgTarget) throws IOException {
        if (io.isSameFile(source, orgTarget)) {
            throw new IOException("Both paths are equal for copy " + source + " > " + orgTarget);
        }

        Path target = nameResolver.buildUniquePath(orgTarget);

        if (target.equals(orgTarget)) {
            logger.infoStat("copy", source, ">", target);
        } else {
            logger.infoStat("copy new", source, ">", target);
        }

        if (copy) {
            io.copy(source, target);
        }
        return target;
    }

    public void deleteEmpty(Path root, Path orgPath) throws IOException {
        Path path = orgPath.normalize().toAbsolutePath();
        while (Files.exists(path) && Files.isDirectory(path) && path.startsWith(root) && !path.equals(root)) {
            List<Path> files = Files.list(path).collect(Collectors.toList());
            if (deleteIgnoredFiles(files)) {
                logger.infoStat("delete empty", path);

                if (delete) {
                    io.delete(path);
                }
                path = path.getParent().toAbsolutePath();
            } else {
                logger.infoStat("not empty", path, ":", files.size(), "elements");
                break;
            }
        }
    }

    private boolean deleteIgnoredFiles(List<Path> files) throws IOException {
        if (files.isEmpty()) {
            return true;
        } else if (files.stream().allMatch(this::isIgnoredFile)) {
            for (Path file : files) {
                logger.infoStat("delete ignored", file);

                if (delete) {
                    io.delete(file);
                }
            }
            return delete;
        }
        return false;
    }

    private boolean isIgnoredFile(Path path) {
        return Files.isRegularFile(path) && (size(path) == 0 || ignoredFiles.contains(path.getFileName().toString()));
    }

    public long size(Path path) {
        try {
            return io.size(path);
        } catch (IOException e) {
            logger.error("size-error", e, path);
            return -1;
        }
    }

    public boolean isSameFile(Path source, Path target) throws IOException {
        return io.isSameFile(source, target);
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

    public boolean isTime() {
        return time;
    }

    public void setTime(boolean time) {
        this.time = time;
    }

    public void setCopy(boolean copy) {
        this.copy = copy;
    }

    public boolean isFast() {
        return fast;
    }

    public void setFast(boolean fast) {
        this.fast = fast;
    }

    public void setIgnoredFiles(Set<String> ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }

    public List<Path> haveSameContent(List<Path> paths) throws IOException {
        List<List<Path>> results = new ArrayList<>();

        for (int a = 0; a < paths.size(); a++) {
            Path pathA = paths.get(a);
            List<Path> duplicates = new ArrayList<>();
            duplicates.add(pathA);
            for (int b = a + 1; b < paths.size(); b++) {
                Path pathB = paths.get(b);
                if (haveSameContent(pathA, pathB)) {
                    duplicates.add(pathB);
                }
            }
            results.add(duplicates);
        }

        return results.stream().filter(v -> v.size() > 1).max(Comparator.comparing(List::size)).orElse(emptyList());
    }
}
