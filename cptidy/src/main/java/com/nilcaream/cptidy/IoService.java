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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
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

    @Option(alternative = "fast")
    private boolean fast = false;

    private Set<String> ignoredFiles = Set.of(".picasa.ini", "Picasa.ini", "desktop.ini", "Thumbs.db", "ZbThumbnail.info");

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
        if (Files.exists(source) && Files.exists(target)) {
            return getAttributes(source).equals(getAttributes(target));
        } else {
            return false;
        }
    }

    public void fixTimestamps(Path source, Path target) throws IOException {
        FileTime sourceFileTime = Files.readAttributes(source, BasicFileAttributes.class).creationTime();
        FileTime targetFileTime = Files.readAttributes(target, BasicFileAttributes.class).creationTime();

        long sourceTime = sourceFileTime.toMillis();
        long targetTime = targetFileTime.toMillis();

        if (sourceTime > targetTime) {
            logger.debugStat("time-fix", source, ":", targetFileTime.toString());
            copyAttributes(target, source);
        } else if (sourceTime < targetTime) {
            logger.debugStat("time-fix", target, ":", sourceFileTime.toString());
            copyAttributes(source, target);
        }
    }

    private void copyAttributes(Path source, Path target) throws IOException {
        if (copy) {
            BasicFileAttributes from = Files.readAttributes(source, BasicFileAttributes.class);
            BasicFileAttributeView to = Files.getFileAttributeView(target, BasicFileAttributeView.class);
            to.setTimes(from.lastModifiedTime(), from.lastAccessTime(), from.creationTime());
        }
    }

    private String getAttributes(Path path) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
        String creation = attr.creationTime().toString();
        return format("%s/%s %d %s", path.getParent().getFileName().toString(), path.getFileName().toString(), Files.size(path), creation);
    }

    public void delete(Path path) throws IOException {
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    private void assertExists(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException(path + " does not exists for deletion");
        }
    }

    public void deleteOne(Path fileA, Path fileB) throws IOException {
        assertExists(fileA);
        assertExists(fileB);
        if (io.isSameFile(fileA, fileB)) {
            throw new IllegalArgumentException("Should provide different paths");
        }

        Path path = selectToDelete(fileA, fileB);
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    public void retainOne(Collection<Path> paths) throws IOException {
        if (paths.stream().distinct().count() < 2) {
            throw new IllegalArgumentException("Should provide more than 1 path");
        }
        paths.forEach(this::assertExists);

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

    private final Pattern COPY_SUFFIX = Pattern.compile("(.+?)(-[0-9])*");

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
        } else if (files.size() == 1 && isIgnoredFile(files.get(0))) {
            logger.infoStat("delete ignored", files.get(0));

            if (delete) {
                io.delete(files.get(0));
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean isIgnoredFile(Path path) throws IOException {
        return Files.isRegularFile(path) && (Files.size(path) == 0 || ignoredFiles.contains(path.getFileName().toString()));
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

    public void setCopy(boolean copy) {
        this.copy = copy;
    }

    public boolean isFast() {
        return fast;
    }

    public void setFast(boolean fast) {
        this.fast = fast;
    }
}
