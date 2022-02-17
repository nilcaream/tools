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

import static java.util.Collections.emptyList;

public class App {

    @Option(value = "i", alternative = "input")
    private List<String> sourceDirectories = emptyList();

    @Option(value = "o", alternative = "output")
    private String targetDirectory;

    @Option(value = "v", alternative = "verbose")
    private boolean verbose;

    @Option(alternative = "organize")
    private boolean organize;

    @Option(alternative = "reorganize")
    private boolean reorganize;

    @Option(alternative = "no-duplicates")
    private boolean removeDuplicates;

    @Option(alternative = "synchronize")
    private boolean synchronize;

    @Option(alternative = "no-empty")
    private boolean removeEmpty;

    @Option(alternative = "date")
    private List<String> explicitDates = emptyList();

    @Inject
    private IoService ioService;

    @Inject
    private Logger logger;

    @Inject
    private Marker marker;

    private final List<Statistics> statistics = new ArrayList<>();

    public static void main(String[] args) {
        App app = Atto.builder().build().instance(App.class);
        UtilArgs.bind(args, app);
        UtilArgs.bind(args, app.ioService);

        if (app.verbose) {
            app.logger.setDebug();
        }

        app.printHeader(args);

        app.marker.setPeriod(5000);
        app.explicitDates.stream()
                .map(date -> date.split("=", 2))
                .forEach(s -> app.ioService.addDate(s[0], s[1]));

        if (app.targetDirectory == null) {
            app.logger.error("no input", "Provide input arguments: -i sourceDirectory -o targetDirectory");
            app.logger.error("actions", "(--organize|--synchronize) --reorganize --no-duplicates --no-empty");
            app.logger.error("options", "--copy --move --delete");
        } else {
            try {
                if (app.organize) {
                    app.sourceDirectories.forEach(source -> {
                        app.organize("organize", Paths.get(source), Paths.get(app.targetDirectory));
                    });
                    if (app.reorganize) {
                        app.organize("reorganize", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }
                    if (app.removeDuplicates) {
                        app.removeDuplicates("no-duplicates", Paths.get(app.targetDirectory));
                    }
                    if (app.removeEmpty) {
                        app.removeEmpty("no-empty", Paths.get(app.sourceDirectories.get(0)));
                    }
                } else if (app.synchronize) {
                    if (app.reorganize) {
                        app.organize("reorganize-1", Paths.get(app.sourceDirectories.get(0)), Paths.get(app.sourceDirectories.get(0)));
                        app.organize("reorganize-2", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }

                    app.synchronize("synchronize-1", Paths.get(app.sourceDirectories.get(0)), Paths.get(app.targetDirectory));
                    app.synchronize("synchronize-2", Paths.get(app.targetDirectory), Paths.get(app.sourceDirectories.get(0)));

                    if (app.removeDuplicates) {
                        app.removeDuplicates("no-duplicates-1", Paths.get(app.sourceDirectories.get(0)));
                        app.removeDuplicates("no-duplicates-2", Paths.get(app.targetDirectory));
                    }
                    if (app.removeEmpty) {
                        app.removeEmpty("no-empty-1", Paths.get(app.sourceDirectories.get(0)));
                        app.removeEmpty("no-empty-2", Paths.get(app.targetDirectory));
                    }
                } else {
                    if (app.reorganize) {
                        app.organize("reorganize", Paths.get(app.targetDirectory), Paths.get(app.targetDirectory));
                    }
                    if (app.removeDuplicates) {
                        app.removeDuplicates("no-duplicates", Paths.get(app.targetDirectory));
                    }
                    if (app.removeEmpty) {
                        app.removeEmpty("no-empty", Paths.get(app.targetDirectory));
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

    private void printHeader(String[] args) {
        logger.label("");
        logger.info("Arguments", String.join(" ", args));
        logger.info("Source", sourceDirectories);
        logger.info("Target", targetDirectory);
        logger.info("Options", opt("verbose", verbose), opt("copy", ioService.isCopy()), opt("move", ioService.isMove()), opt("delete", ioService.isDelete()));
        logger.info("Actions", opt("organize", organize), opt("reorganize", reorganize), opt("no-duplicates", removeDuplicates), opt("synchronize", synchronize), opt("no-empty", removeEmpty));
        if (!explicitDates.isEmpty()) {
            logger.info("Dates", String.join(" ", explicitDates));
        }
        logger.label("");
    }

    private String opt(String key, boolean value) {
        return value ? key : "";
    }

    private void organize(String id, Path sourceRoot, Path targetRoot) {
        logger.info(id, sourceRoot, ">", targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    marker.mark(source);
                    Path target = ioService.buildMatchingTarget(source, targetRoot);
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

        statistics.add(logger.getStatistics());
        logger.info(id, "total time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
    }

    private void removeDuplicates(String id, Path targetRoot) {
        logger.info(id, targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(targetRoot)) {
            walk.filter(Files::isDirectory).forEach(directory -> {
                try {
                    marker.mark(directory);
                    int[] count = new int[]{0};
                    Map<Long, List<Path>> sizeToPaths = new HashMap<>();
                    Files.list(directory)
                            .filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                            .forEach(path -> {
                                count[0]++;
                                long size = ioService.size(path);
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
                                            if (!ioService.isSameFile(file, copy) && ioService.haveSameContent(file, copy)) {
                                                logger.infoStat("duplicate", file, "=", copy);
                                                ioService.deleteOne(file, copy);
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

        statistics.add(logger.getStatistics());
        logger.info(id, "total time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
    }

    private void removeEmpty(String id, Path targetRoot) {
        logger.info(id, targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(targetRoot)) {
            walk.filter(Files::isDirectory).forEach(directory -> {
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

        statistics.add(logger.getStatistics());
        logger.info(id, "total time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
    }

    private void synchronize(String id, Path sourceRoot, Path targetRoot) {
        logger.info(id, sourceRoot, "=", targetRoot);
        logger.resetStatistics(id);
        marker.reset();

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    marker.mark(source);
                    Path target = ioService.buildCopyTarget(source, sourceRoot, targetRoot);
                    if (ioService.haveSameContent(source, target)) {
                        // file is already in present in target location
                        logger.infoStat("ok location", source);
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

        statistics.add(logger.getStatistics());
        logger.info(id, "total time", marker.getElapsed() / 1000, "seconds");
        logger.label("");
    }

}
