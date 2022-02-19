package com.nilcaream.cptidy;

import com.nilcaream.utilargs.Option;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Optional.ofNullable;

@Singleton
public class IoService {

    @Inject
    private Logger logger;

    @Inject
    private NameResolver nameResolver;

    @Inject
    private Io io;

    @Option(value = "d", alternative = "delete")
    private boolean delete = false;

    @Option(value = "m", alternative = "move")
    private boolean move = false;

    @Option(value = "c", alternative = "copy")
    private boolean copy = false;

    public Path buildMatchingTarget(Path source, Path targetRoot) throws IOException {
        Path result = ofNullable(nameResolver.resolve(source)).map(r -> r.resolve(targetRoot)).orElse(null);
        logger.stat("total", source);
        return result;
    }

    public boolean haveSameContent(Path source, Path target) throws IOException {
        return io.haveSameContent(source, target);
    }

    public void delete(Path path) throws IOException {
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    public void deleteOne(Path fileA, Path fileB) throws IOException {
        Path path = selectOne(fileA, fileB);
        logger.infoStat("delete", path);

        if (delete) {
            io.delete(path);
        }
    }

    private Path selectOne(Path fileA, Path fileB) {
        Path result;
        String nameA = fileA.getFileName().toString();
        String nameB = fileB.getFileName().toString();

        if (nameA.length() == nameB.length()) {
            if (nameA.compareTo(nameB) > 0) {
                result = fileA;
            } else {
                result = fileB;
            }
        } else {
            Path shorter = nameA.length() < nameB.length() ? fileA : fileB;
            Path longer = shorter == fileA ? fileB : fileA;
            String shorterName = getName(shorter == fileA ? nameA : nameB);
            String longerName = getName(longer == fileA ? nameA : nameB);

            if (longerName.startsWith(shorterName)) {
                result = longer;
            } else {
                result = shorter;
            }
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
        logger.infoStat("copy", source, ">", target);

        if (copy) {
            io.copy(source, target);
        }
        return target;
    }

    public void deleteEmpty(Path root, Path orgPath) throws IOException {
        Path path = orgPath.normalize().toAbsolutePath();
        while (Files.exists(path) && Files.isDirectory(path) && path.startsWith(root) && !path.equals(root)) {
            long count = Files.list(path).count();
            if (count == 0) {
                logger.infoStat("delete empty", path);

                if (delete) {
                    io.delete(path);
                }
                path = path.getParent().toAbsolutePath();
            } else {
                logger.infoStat("not empty", path, ":", count, "elements");
                break;
            }
        }
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
}
