package com.nilcaream.cptidy;

import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.Option;
import com.nilcaream.utilargs.UtilArgs;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;

public class App {

    @Option(alternative = "source")
    private List<String> sourceDirectories = emptyList();

    @Option(alternative = "target")
    private String targetDirectory;

    @Option(value = "v", alternative = "verbose")
    private boolean verbose;

    @Option(alternative = "analyze")
    private boolean analyze;

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

    @Option(alternative = "test")
    private boolean test;

    @Option(alternative = "date")
    private List<String> explicitDates = emptyList();

    @Inject
    private IoService ioService;

    @Inject
    private Logger logger;

    @Inject
    private Marker marker;

    @Inject
    private Actions actions;

    @Inject
    private NameResolver nameResolver;

    private final List<Statistics> statistics = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        App app = Atto.builder().build().instance(App.class);
        UtilArgs.bind(args, app);
        UtilArgs.bind(args, app.ioService);

        app.initialize(args);
        app.execute();
    }

    private void initialize(String[] args) throws IOException {
        if (verbose) {
            logger.setDebug();
        }

        logger.label("");
        logger.info("Arguments", String.join(" ", args));
        logger.info("Source", sourceDirectories);
        logger.info("Target", targetDirectory);
        logger.info("Options", opt("verbose", verbose), opt("copy", ioService.isCopy()), opt("move", ioService.isMove()), opt("delete", ioService.isDelete()), opt("fast", ioService.isFast()));
        logger.info("Actions", opt("analyze", analyze), opt("organize", organize), opt("reorganize", reorganize), opt("no-duplicates", removeDuplicates), opt("synchronize", synchronize), opt("no-empty", removeEmpty));

        marker.setPeriod(5000);

        List<String> explicitDatesResolved = new ArrayList<>();

        for (String date : explicitDates) {
            if (date.contains("=")) {
                String[] split = date.split("=", 2);
                nameResolver.addDate(split[0].trim(), split[1].trim());
                explicitDatesResolved.add(split[0].trim() + "=" + split[1].trim());
            } else {
                Files.readAllLines(Paths.get(date)).stream()
                        .filter(x -> x.contains("="))
                        .map(x -> x.split("=", 2))
                        .forEach(split -> {
                            nameResolver.addDate(split[0].trim(), split[1].trim());
                            explicitDatesResolved.add(split[0].trim() + "=" + split[1].trim());
                        });
            }
        }

        explicitDates = explicitDatesResolved;
        if (!explicitDates.isEmpty()) {
            logger.info("Dates", String.join(" ", explicitDates));
        }
        logger.label("");
    }

    private boolean hasSource() {
        return sourceDirectories != null && !sourceDirectories.isEmpty() && sourceDirectories.stream().noneMatch(String::isBlank);
    }

    private boolean hasTarget() {
        return targetDirectory != null && !targetDirectory.isBlank();
    }

    private void requireAny() {
        if (!hasSource() && !hasTarget()) {
            fail();
        }
    }

    private void require(boolean source, boolean target) {
        if (source && !hasSource()) {
            fail();
        }
        if (target && !hasTarget()) {
            fail();
        }
    }

    private void fail() {
        logger.error("no input", "Provide input arguments: --source sourceDirectory --target targetDirectory");
        logger.error("actions", "(--organize|--synchronize) --reorganize --no-duplicates --no-empty");
        logger.error("options", "--copy --move --delete --fast");
        System.exit(1);
    }

    private Path asPath(String text) {
        try {
            return Paths.get(text).toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void execute() throws IOException {
        if (test) {
            actions.test(asPath(ofNullable(targetDirectory).filter(t -> !t.isBlank()).orElse(".")));
        } else {
            long time = currentTimeMillis();
            try {
                if (analyze) {
                    require(true, false);

                    sourceDirectories.stream().map(this::asPath).forEach(source -> {
                        statistics.add(actions.analyze("analyze", source));
                    });
                } else if (organize) {
                    require(true, true);

                    if (removeDuplicates) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.findCopies("no-copies", source, asPath(targetDirectory)));
                        });
                    }

                    sourceDirectories.stream().map(this::asPath).forEach(source -> {
                        statistics.add(actions.organize("organize", source, asPath(targetDirectory)));
                    });

                    if (removeEmpty) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.removeEmpty("no-empty", source));
                        });
                    }
                } else if (removeDuplicates && hasTarget()) {
                    require(true, true);

                    sourceDirectories.stream().map(this::asPath).forEach(source -> {
                        statistics.add(actions.findCopies("no-copies", source, asPath(targetDirectory)));
                    });

                    if (removeEmpty) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.removeEmpty("no-empty", source));
                        });
                    }
                } else if (removeDuplicates) { // only source
                    require(true, false);

                    sourceDirectories.stream().map(this::asPath).forEach(source -> {
                        statistics.add(actions.removeDuplicates("no-self-copies", source));
                    });

                    if (removeEmpty) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.removeEmpty("no-empty", source));
                        });
                    }
                } else if (reorganize) {
                    require(true, false);

                    sourceDirectories.stream().map(this::asPath).forEach(source -> {
                        statistics.add(actions.organize("reorganize", source, source));
                    });

                    if (removeEmpty) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.removeEmpty("no-empty", source));
                        });
                    }
                } else if (removeEmpty) {
                    requireAny();

                    if (hasSource()) {
                        sourceDirectories.stream().map(this::asPath).forEach(source -> {
                            statistics.add(actions.removeEmpty("no-empty", source));
                        });
                    }
                    if (hasTarget()) {
                        statistics.add(actions.removeEmpty("no-empty", asPath(targetDirectory)));
                    }
                } else if (synchronize) {
                    require(true, true);

                    statistics.add(actions.synchronize("synchronize", asPath(sourceDirectories.get(0)), asPath(targetDirectory)));
                } else {
                    fail();
                }
            } finally {
                statistics.stream().filter(Statistics::hasData).forEach(stats -> {
                    logger.label(stats.getId());
                    stats.getData().forEach((k, v) -> logger.info(k, v.toString()));
                });
                if (!logger.getErrors().isEmpty()) {
                    logger.label("errors");
                    logger.getErrors().forEach(e -> logger.warn("error", e));
                }
                logger.label("");
                logger.info("", "total time", (currentTimeMillis() - time) / 1000, "seconds");
            }
        }
    }

    private String opt(String key, boolean value) {
        return value ? key : "";
    }
}
