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

    @Option(value = "r", alternative = "organize")
    private boolean organize;

    @Option(value = "R", alternative = "reorganize")
    private boolean reorganize;

    @Option(value = "D", alternative = "deduplicate")
    private boolean deduplicate;

    @Option(value = "s", alternative = "synchronize")
    private boolean synchronize;

    @Inject
    private IoService ioService;

    @Inject
    private Logger logger;

    private List<Statistics> statistics = new ArrayList<>();

    public static void main(String[] args) {
        App app = Atto.builder().build().instance(App.class);
        UtilArgs.bind(args, app);
        UtilArgs.bind(args, app.ioService);

        if (app.verbose) {
            app.logger.setDebug();
        }

        app.printHeader();

        if (app.targetDirectory == null) {
            app.logger.error("no input", "Provide input arguments: -i sourceDirectory -o targetDirectory");
            app.logger.error("actions", "(--organize|--synchronize) --reorganize --deduplicate");
            app.logger.error("options", "--copy --move --delete");
        } else {
            try {
                if (app.organize) {
                    app.sourceDirectories.forEach(source -> {
                        app.logger.info("organize", source, ">", app.targetDirectory);
                        app.organize("organize", Paths.get(source), Paths.get(app.targetDirectory));
                    });
                    if (app.reorganize) {
                        app.logger.info("reorganize", app.targetDirectory);
                        app.organize("reorganize", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }
                    if (app.deduplicate) {
                        app.logger.info("deduplicate", app.targetDirectory);
                        app.deduplicate("deduplicate", Paths.get(app.targetDirectory));
                    }
                } else if (app.synchronize) {
                    if (app.reorganize) {
                        app.logger.info("reorganize-1", app.sourceDirectories.get(0));
                        app.organize("reorganize-1", Paths.get(app.sourceDirectories.get(0)), Paths.get(app.sourceDirectories.get(0)));
                        app.logger.info("reorganize-2", app.targetDirectory);
                        app.organize("reorganize-2", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }

                    app.logger.info("synchronize-1", app.sourceDirectories.get(0), "=", app.targetDirectory);
                    app.synchronize("synchronize-1", Paths.get(app.sourceDirectories.get(0)), Paths.get(app.targetDirectory));

                    app.logger.info("synchronize-2", app.targetDirectory, "=", app.sourceDirectories.get(0));
                    app.synchronize("synchronize-2", Paths.get(app.targetDirectory), Paths.get(app.sourceDirectories.get(0)));

                    if (app.deduplicate) {
                        app.logger.info("deduplicate-1", app.sourceDirectories.get(0));
                        app.deduplicate("deduplicate-1", Paths.get(app.sourceDirectories.get(0)));
                        app.logger.info("deduplicate-2", app.targetDirectory);
                        app.deduplicate("deduplicate-2", Paths.get(app.targetDirectory));
                    }
                } else {
                    if (app.reorganize) {
                        app.logger.info("reorganize", app.targetDirectory);
                        app.organize("reorganize", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }
                    if (app.deduplicate) {
                        app.logger.info("deduplicate", app.targetDirectory);
                        app.deduplicate("deduplicate", Paths.get(app.targetDirectory));
                    }
                }
            } finally {
                app.statistics.stream().filter(Statistics::hasData).forEach(stats -> {
                    app.logger.label(stats.getId());
                    stats.getData().forEach((k, v) -> app.logger.info(k, v.toString()));
                });
                if (!app.logger.getErrors().isEmpty()) {
                    app.logger.label("errors");
                    app.logger.getErrors().forEach(e -> app.logger.warn("error", e));
                }
            }
        }
    }

    private void printHeader() {
        logger.info("Source", sourceDirectories);
        logger.info("Target", targetDirectory);
        logger.info("Options", "copy=" + ioService.isCopy(), "move=" + ioService.isMove(), "delete=" + ioService.isDelete());
        logger.info("Actions", "organize=" + organize, "reorganize=" + reorganize, "deduplicate=" + deduplicate, "synchronize=" + synchronize);
        logger.debug("Verbose", verbose);
        logger.label("");
    }

    private void organize(String id, Path sourceRoot, Path targetRoot) {
        ioService.resetStatistics(id);

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = ioService.buildMatchingTarget(source, targetRoot);
                    if (target == null) {
                        // file is not matching target pattern
                        ioService.reportNoMatch(source);
                    } else if (ioService.isSameFile(source, target)) {
                        // file is already in target location
                        ioService.reportOkLocation(source);
                    } else if (ioService.hasSameContent(source, target)) {
                        // duplicate detected
                        ioService.delete(source);
                    } else if (Files.exists(target)) {
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

        statistics.add(ioService.getStatistics());
        logger.label("");
    }

    private void deduplicate(String id, Path targetRoot) {
        ioService.resetStatistics(id);

        try (Stream<Path> walk = Files.walk(targetRoot)) {
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
                                                ioService.delete(selectOne(file, copy));
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

        statistics.add(ioService.getStatistics());
        logger.label("");
    }

    private Path selectOne(Path fileA, Path fileB) {
        String nameA = fileA.getFileName().toString();
        String nameB = fileB.getFileName().toString();
        if (nameA.length() > nameB.length()) {
            return fileA;
        } else if (nameA.length() < nameB.length()) {
            return fileB;
        } else if (nameA.compareTo(nameB) > 0) {
            return fileA;
        } else {
            return fileB;
        }
    }

    private void synchronize(String id, Path sourceRoot, Path targetRoot) {
        ioService.resetStatistics(id);

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = ioService.buildCopyTarget(source, sourceRoot, targetRoot);
                    if (ioService.hasSameContent(source, target)) {
                        // file is already in present in target location
                        ioService.reportOkLocation(source);
                    } else if (Files.exists(target)) {
                        // target exists but is a different file
                        ioService.copyAsNew(source, target);
                    } else {
                        // file does not exist in target location
                        ioService.copy(source, target);
                    }
                } catch (IOException e) {
                    logger.error("error", e, "File processing error");
                }
            });
        } catch (IOException e) {
            logger.error("error", e, "Directory processing error");
        }

        statistics.add(ioService.getStatistics());
        logger.label("");
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
