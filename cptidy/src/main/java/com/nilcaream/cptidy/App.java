package com.nilcaream.cptidy;

import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.Option;
import com.nilcaream.utilargs.UtilArgs;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class App {

    @Option(value = "i", alternative = "input")
    private List<String> sourceDirectories;

    @Option(value = "o", alternative = "output")
    private String targetDirectory;

    @Option(value = "v", alternative = "verbose")
    private boolean verbose;

    @Inject
    private IoService ioService;

    @Inject
    private Logger logger;

    public static void main(String[] args) {
        App app = Atto.builder().build().instance(App.class);
        UtilArgs.bind(args, app);
        UtilArgs.bind(args, app.ioService);
        app.go();
    }

    private void go() {
        if (verbose) {
            logger.setDebug();
        }

        logger.info("Source", sourceDirectories);
        logger.info("Target", targetDirectory);
        logger.info("Options", "move=" + ioService.isMove(), "delete=" + ioService.isDelete());
        logger.debug("Verbose", verbose);
        logger.info("", "----------------------------------------------------------------");

        Statistics scanStatistics = new Statistics();
        Statistics lowerCaseStatistics = new Statistics();
        Statistics duplicatesStatistics = new Statistics();

        if (targetDirectory == null) {
            logger.error("no input", "Provide input arguments: -i sourceDirectory -o targetDirectory");
        } else {
            if (sourceDirectories != null) {
                ioService.resetStatistics();
                sourceDirectories.forEach(source -> {
                    logger.info("scan-main", source + " > " + targetDirectory);
                    scan(source, targetDirectory);
                });
                scanStatistics = ioService.getStatistics();
            }

            ioService.resetStatistics();
            logger.info("scan-case", targetDirectory);
            scan(targetDirectory, targetDirectory);
            lowerCaseStatistics = ioService.getStatistics();

            ioService.resetStatistics();
            logger.info("scan-copy", targetDirectory);
            removeDuplicates(targetDirectory);
            duplicatesStatistics = ioService.getStatistics();
        }

        if (scanStatistics.hasData()) {
            logger.info("scan-main", "----------------------------------------------------------------");
            scanStatistics.getData().forEach((k, v) -> logger.info(k, v.toString()));
        }
        if (lowerCaseStatistics.hasData()) {
            logger.info("scan-case", "----------------------------------------------------------------");
            lowerCaseStatistics.getData().forEach((k, v) -> logger.info(k, v.toString()));
        }
        if (duplicatesStatistics.hasData()) {
            logger.info("scan-copy", "----------------------------------------------------------------");
            duplicatesStatistics.getData().forEach((k, v) -> logger.info(k, v.toString()));
        }
    }

    private void scan(String sourceRoot, String targetRoot) {
        Path targetRootPath = Paths.get(targetRoot);
        try (Stream<Path> walk = Files.walk(Paths.get(sourceRoot))) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = ioService.buildMatchingTarget(source, targetRootPath);
                    if (target == null) {
                        // file is not matching target pattern
                        ioService.reportNoMatch(source);
                    } else if (ioService.isSameFile(source, target)) {
                        // file is already in target location
                        ioService.reportOkLocation(source);
                    } else if (ioService.hasSameContent(source, target)) {
                        // duplicate detected
                        ioService.delete(source);
                    } else if (target.toFile().exists()) {
                        // target exists but is a different file
                        ioService.moveAsNew(source, target);
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
    }

    private void removeDuplicates(String targetRoot) {
        try (Stream<Path> walk = Files.walk(Paths.get(targetRoot))) {
            walk.filter(Files::isDirectory).forEach(directory -> {
                try {
                    int[] count = new int[]{0};
                    Map<Long, List<Path>> sizeToPaths = new HashMap<>();
                    Files.list(directory)
                            .filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                            .forEach(path -> {
                                count[0]++;
                                long size = size(path);
                                List<Path> paths = sizeToPaths.computeIfAbsent(size, k -> new ArrayList<>());
                                paths.add(path);
                            });
                    logger.info("directory", directory, ":", count[0], "files");

                    sizeToPaths.entrySet().stream()
                            .filter(e -> e.getKey() > 1024)
                            .filter(e -> e.getValue().size() > 1)
                            .map(Map.Entry::getValue)
                            .forEach(paths -> {
                                for (Path file : paths) {
                                    for (Path copy : paths) {
                                        try {
                                            if (!ioService.isSameFile(file, copy) && ioService.hasSameContent(file, copy)) {
                                                ioService.delete(copy);
                                            }
                                        } catch (IOException e) {
                                            logger.error("error", file, ":", copy);
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
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            logger.error("size-error", path);
            return -1;
        }
    }
}
