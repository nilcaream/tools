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
import java.util.List;
import java.util.Map;
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

    @Inject
    private IoTest ioTest;

    public Statistics organize(String id, Path sourceRoot, Path targetRoot) {
        logger.info(id, sourceRoot, "->", targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
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
                        ioService.delete(source);
                    } else {
                        // just move to target
                        ioService.move(source, target);
                    }
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public Statistics removeDuplicatesPerDirectory(String id, Path root) {
        logger.info(id, root);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isDirectory).forEach(directory -> {
                try {
                    marker.mark(directory);
                    int[] count = new int[]{0};
                    Map<Long, List<Path>> sizeToPaths = new HashMap<>();
                    try (Stream<Path> list = Files.list(directory)) {
                        list
                                .filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
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
                                for (int a = 0; a < paths.size(); a++) {
                                    for (int b = a + 1; b < paths.size(); b++) {
                                        Path file = paths.get(a);
                                        Path copy = paths.get(b);
                                        try {
                                            if (!ioService.isSameFile(file, copy) && ioService.haveSameContent(file, copy)) {
                                                logger.infoStat("duplicate", file, "=", copy);
                                                ioService.deleteOne(file, copy);
                                            }
                                        } catch (IOException e) {
                                            logger.error("error", e, file, ":", copy);
                                        }
                                    }
                                }
                            });
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
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
                    .filter(Files::isRegularFile)
                    .peek(marker::mark)
                    .forEach(file -> {
                        try {
                            int count = ioService.countZeroBlocks(file, buffer);
                            if (count > 0) {
                                int bytes = count * bufferSize;
                                long totalSize = ioService.size(file);
                                int percentage = (int) (100 * bytes / totalSize);
                                if (percentage > 0) {
                                    logger.infoStat("empty blocks", file, ":", bytes, "/", totalSize, "bytes", percentage, "%");
                                }
                            }
                        } catch (IOException e) {
                            logger.error("error", e, "File processing error");
                        }
                    });
        } catch (IOException e) {
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
                    .filter(Files::isDirectory)
                    .peek(d -> marker.mark(d))
                    .forEach(directory -> {
                        try {
                            marker.mark(directory);
                            ioService.deleteEmpty(targetRoot, directory);
                        } catch (IOException e) {
                            logger.error("error", e, "Directory processing error");
                        }
                    });
        } catch (IOException e) {
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
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    marker.mark(source);
                    logger.stat("total", source);

                    Path target = ioService.buildCopyTarget(source, sourceRoot, targetRoot);
                    if (ioService.isSameFile(source, target)) {
                        // source file is exactly the same file as target
                        logger.error("same file", source);
                    } else if (Files.exists(target) && ioService.haveSameAttributes(source, target)) {
                        // same parent name, file name, size and create date time
                        logger.stat("same attributes", source);
                    } else if (ioService.haveSameContent(source, target)) {
                        // same content
                        logger.infoStat("same content", source, "=", target);
                        ioService.fixTimestamps(source, target);
                    } else {
                        // does not exist in target location or target is a different file
                        ioService.copy(source, target);
                    }
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
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
                directories = walk.filter(Files::isDirectory).sorted().collect(Collectors.toList());
            }
            logger.info("count", "Found", directories.size(), "directories");
            for (Path directory : directories) {
                List<Path> files;
                try (Stream<Path> list = Files.list(directory)) {
                    files = list
                            .filter(Files::isRegularFile)
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
        } catch (IOException e) {
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
            walk.filter(Files::isDirectory).forEach(directory -> {
                try {
                    marker.mark(directory);
                    int[] count = new int[]{0};
                    try (Stream<Path> list = Files.list(directory)) {
                        list
                                .filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
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
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info("source", source);
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile).forEach(sourceFile -> {
                try {
                    logger.stat("total", sourceFile);

                    marker.mark(sourceFile);
                    long size = ioService.size(sourceFile);
                    List<Path> targetFiles = sizeToPaths.get(size);
                    boolean unique = true;
                    if (targetFiles != null) {
                        for (Path targetFile : targetFiles) {
                            if (!ioService.isSameFile(sourceFile, targetFile) && ioService.haveSameContent(sourceFile, targetFile)) {
                                logger.infoStat("duplicate", sourceFile, "=", targetFile);
                                ioService.delete(sourceFile);
                                unique = false;
                                break;
                            }
                        }
                    }
                    if (unique) {
                        logger.infoStat("unique", sourceFile);
                    }
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }

    public void test(Path root) throws IOException {
        ioTest.test2(root, 128 * 1024 * 1024, 16 * 1024 * 1024);
    }

    public Statistics removeDuplicatesGlobally(String id, Path root) {
        logger.info(id, root);
        logger.resetStatistics(id);
        marker.reset();

        Map<Long, List<Path>> sizeToPaths = new HashMap<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    marker.mark(file);
                    List<Path> paths = sizeToPaths.computeIfAbsent(Files.size(file), k -> new ArrayList<>());
                    paths.add(file);
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
            logger.error("error", e, "Directory processing error");
        }

        logger.info("scan completed", "found", sizeToPaths.size(), "unique file sizes");

        sizeToPaths.entrySet().stream()
                .filter(e -> e.getKey() > 1024)
                .filter(e -> e.getValue().size() > 1)
                .map(Map.Entry::getValue)
                .forEach(paths -> {
                    try {
                        List<Path> same = ioService.haveSameContent(paths);
                        List<Path> different = new ArrayList<>(paths);
                        different.removeAll(same);

                        if (!same.isEmpty()) {
                            ioService.retainOne(same);
                        }
                        for (Path path : different) {
                            logger.infoStat("different", path);
                        }
                    } catch (IOException e) {
                        logger.error("error", e, "Delete error");
                    }
                });

        logger.info(id, "time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
        return logger.getStatistics();
    }
}
