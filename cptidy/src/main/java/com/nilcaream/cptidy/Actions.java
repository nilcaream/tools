package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class Actions {

    @Inject
    private IoService ioService;

    @Inject
    private Logger logger;

    @Inject
    private Marker marker;

    public Statistics organize(String id, Path sourceRoot, Path targetRoot) {
        logger.info(id, sourceRoot, "->", targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        // listed before any file moves, so that reorganize never meets a file it has already moved
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            sources = walk.filter(this::isFile).sorted().collect(Collectors.toList());
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        for (Path source : sources) {
            try {
                marker.mark(source);
                Path target = ioService.buildMatchingTarget(source, targetRoot);
                //noinspection StatementWithEmptyBody
                if (target == null) {
                    // file is not matching target pattern or has no exif date
                } else if (ioService.isSameFile(source, target)) {
                    // file is already in target location
                    logger.infoStat("ok location", source);
                } else if (ioService.haveSameContent(source, target)) {
                    // duplicate detected
                    logger.infoStat("duplicate", source, "=", target);
                    ioService.deleteCopy(source, target);
                } else {
                    // just move to target
                    ioService.move(source, target);
                }
            } catch (IOException | RuntimeException e) {
                logger.error("error", e, "File processing error");
            }
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    // A symlink is skipped, never followed: it is not a file of the tree being tidied.
    private boolean isFile(Path path) {
        if (Files.isSymbolicLink(path)) {
            logger.info("skip link", path);
            return false;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private boolean isDirectory(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    public Statistics removeDuplicatesPerDirectory(String id, Path root) {
        logger.info(id, root);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(this::isDirectory).forEach(directory -> {
                try {
                    marker.mark(directory);
                    int[] count = new int[]{0};
                    Map<Long, List<Path>> sizeToPaths = new HashMap<>();
                    try (Stream<Path> list = Files.list(directory)) {
                        list
                                .filter(this::isFile)
                                .forEach(path -> {
                                    count[0]++;
                                    long size = ioService.size(path);
                                    List<Path> paths = sizeToPaths.computeIfAbsent(size, k -> new ArrayList<>());
                                    paths.add(path);
                                });
                    }
                    logger.info("directory", directory, ":", count[0], "files");

                    sizeToPaths.entrySet().stream()
                            .filter(e -> e.getKey() > 1024)
                            .filter(e -> e.getValue().size() > 1)
                            .map(Map.Entry::getValue)
                            .forEach(paths -> {
                                try {
                                    for (List<Path> same : ioService.groupBySameContent(paths)) {
                                        logger.infoStat("duplicate", same.get(0), "=", same.subList(1, same.size()));
                                        ioService.retainOne(same);
                                    }
                                } catch (IOException | RuntimeException e) {
                                    logger.error("error", e, paths);
                                }
                            });
                } catch (IOException | RuntimeException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics countEmptyBlocks(String id, Path root, int bufferSize) {
        logger.info(id, root);
        logger.resetStatistics(id);
        marker.reset();

        byte[] buffer = new byte[bufferSize];

        try (Stream<Path> walk = Files.walk(root)) {
            walk
                    .filter(this::isFile)
                    .peek(marker::mark)
                    .forEach(file -> {
                        try {
                            long bytes = ioService.countZeroBytes(file, buffer);
                            if (bytes > 0) {
                                long totalSize = ioService.size(file);
                                long percentage = 100 * bytes / totalSize;
                                if (percentage > 0) {
                                    logger.infoStat("empty blocks", file, ":", bytes, "/", totalSize, "bytes", percentage, "%");
                                }
                            }
                        } catch (IOException | RuntimeException e) {
                            logger.error("error", e, "File processing error");
                        }
                    });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics removeEmpty(String id, Path targetRoot) {
        logger.info(id, targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(targetRoot)) {
            walk
                    .filter(this::isDirectory)
                    .peek(d -> marker.mark(d))
                    .forEach(directory -> {
                        try {
                            marker.mark(directory);
                            ioService.deleteEmpty(targetRoot, directory);
                        } catch (IOException | RuntimeException e) {
                            logger.error("error", e, "Directory processing error");
                        }
                    });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics synchronize(String id, Path sourceRoot, Path targetRoot) {
        logger.info(id, sourceRoot, "=", targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(this::isFile).forEach(source -> {
                try {
                    marker.mark(source);
                    logger.stat("total", source);

                    Path target = ioService.buildCopyTarget(source, sourceRoot, targetRoot);
                    if (ioService.isSameFile(source, target)) {
                        // source file is exactly the same file as target
                        logger.error("same file", source);
                    } else if (Files.exists(target) && ioService.haveSameSizeAndTime(source, target)) {
                        // unchanged: content is not read
                        logger.stat("same attributes", source);
                    } else if (ioService.haveSameContent(source, target)) {
                        // same size, different modification time, same content
                        logger.infoStat("same content", source, "=", target);
                        ioService.fixTimestamps(source, target);
                    } else {
                        // does not exist in target location or target is a different file
                        ioService.copy(source, target);
                    }
                } catch (IOException | RuntimeException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics analyze(String id, Path path) {
        logger.info(id, path);
        logger.resetStatistics(id);
        marker.reset();

        try {
            Path target = Paths.get("");
            List<Path> directories;
            try (Stream<Path> walk = Files.walk(path)) {
                directories = walk.filter(this::isDirectory).sorted().collect(Collectors.toList());
            }
            logger.info("count", "Found", directories.size(), "directories");
            for (Path directory : directories) {
                List<Path> files;
                try (Stream<Path> list = Files.list(directory)) {
                    files = list
                            .filter(this::isFile)
                            .peek(marker::mark)
                            .sorted().collect(Collectors.toList());
                }
                for (Path file : files) {
                    marker.mark(file);
                    BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
                    Path match = ioService.buildMatchingTarget(file, target);
                    String fileDateTime = attr.creationTime().toString().replaceAll("[TZ]", " ").trim();
                    if (match == null) {
                        logger.infoStat("failure", file, ":", "unknown", "|", fileDateTime);
                    } else {
                        String fileDate = fileDateTime.substring(0, 7);
                        if (match.getParent().getFileName().toString().equals(fileDate)) {
                            logger.infoStat("all match", file, ":", match, "|", fileDateTime);
                        } else {
                            logger.infoStat("partial match", file, ":", match, "|", fileDateTime);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "File processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics findCopies(String id, Path source, Path target) {
        logger.info(id, source, ":", target);
        logger.resetStatistics(id);
        marker.reset();

        Map<Long, List<Path>> sizeToPaths = new HashMap<>();

        logger.info("target", target);
        try (Stream<Path> walk = Files.walk(target)) {
            walk.filter(this::isDirectory).forEach(directory -> {
                try {
                    marker.mark(directory);
                    int[] count = new int[]{0};
                    try (Stream<Path> list = Files.list(directory)) {
                        list
                                .filter(this::isFile)
                                .forEach(path -> {
                                    count[0]++;
                                    marker.mark(path);
                                    long size = ioService.size(path);
                                    if (size > 1024) {
                                        List<Path> paths = sizeToPaths.computeIfAbsent(size, k -> new ArrayList<>());
                                        paths.add(path);
                                    }
                                });
                    }
                    logger.info("directory", directory, ":", count[0], "files");
                } catch (IOException | RuntimeException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info("source", source);
        Map<Long, List<Path>> sizeToSourcePaths = new TreeMap<>();
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(this::isFile).forEach(sourceFile -> {
                try {
                    logger.stat("total", sourceFile);
                    marker.mark(sourceFile);
                    long size = ioService.size(sourceFile);
                    if (sizeToPaths.containsKey(size)) {
                        sizeToSourcePaths.computeIfAbsent(size, k -> new ArrayList<>()).add(sourceFile);
                    } else {
                        logger.infoStat("unique", sourceFile);
                    }
                } catch (IOException | RuntimeException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        // each file of a size found on both sides is read at most twice, however many files share that size
        sizeToSourcePaths.forEach((size, sourceFiles) -> {
            try {
                Set<Path> sourceSet = new HashSet<>(sourceFiles);
                Set<Path> unique = new TreeSet<>(sourceFiles);
                List<Path> all = Stream.concat(sourceFiles.stream(), sizeToPaths.get(size).stream()).distinct().collect(Collectors.toList());
                for (List<Path> same : ioService.groupBySameContent(all)) {
                    Path targetFile = same.stream().filter(p -> !sourceSet.contains(p)).findFirst().orElse(null);
                    if (targetFile != null) {
                        for (Path sourceFile : same) {
                            if (sourceSet.contains(sourceFile)) {
                                logger.infoStat("duplicate", sourceFile, "=", targetFile);
                                ioService.deleteCopy(sourceFile, targetFile);
                                unique.remove(sourceFile);
                            }
                        }
                    }
                }
                for (Path sourceFile : unique) {
                    logger.infoStat("unique", sourceFile);
                }
            } catch (IOException | RuntimeException e) {
                logger.error("error", e, "File processing error");
            }
        });

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics removeDuplicatesGlobally(String id, Path root) {
        logger.info(id, root);
        logger.resetStatistics(id);
        marker.reset();

        Map<Long, List<Path>> sizeToPaths = new HashMap<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(this::isFile).forEach(file -> {
                try {
                    marker.mark(file);
                    List<Path> paths = sizeToPaths.computeIfAbsent(Files.size(file), k -> new ArrayList<>());
                    paths.add(file);
                } catch (IOException | RuntimeException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException | RuntimeException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info("scan completed", "found", sizeToPaths.size(), "unique file sizes");

        sizeToPaths.entrySet().stream()
                .filter(e -> e.getKey() > 1024)
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getValue)
                .forEach(paths -> {
                    try {
                        List<Path> different = new ArrayList<>(paths);
                        for (List<Path> same : ioService.groupBySameContent(paths)) {
                            ioService.retainOne(same);
                            different.removeAll(same);
                        }
                        for (Path path : different) {
                            logger.infoStat("different", path);
                        }
                    } catch (IOException | RuntimeException e) {
                        logger.error("error", e, "Delete error");
                    }
                });

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }
}
