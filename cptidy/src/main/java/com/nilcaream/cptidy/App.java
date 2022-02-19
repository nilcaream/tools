package com.nilcaream.cptidy;

import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.Option;
import com.nilcaream.utilargs.UtilArgs;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;

public class App {

    @Option(value = "i", alternative = "input")
    private List<String> sourceDirectories = emptyList();

    @Option(value = "o", alternative = "output")
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
        logger.info("Options", opt("verbose", verbose), opt("copy", ioService.isCopy()), opt("move", ioService.isMove()), opt("delete", ioService.isDelete()));
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

    private void execute() {
        if (targetDirectory == null) {
            logger.error("no input", "Provide input arguments: -i sourceDirectory -o targetDirectory");
            logger.error("actions", "(--organize|--synchronize) --reorganize --no-duplicates --no-empty");
            logger.error("options", "--copy --move --delete");
        } else {
            long time = currentTimeMillis();
            try {
                String sourceDirectory = sourceDirectories.isEmpty() ? null : sourceDirectories.get(0);
                if (sourceDirectory == null || sourceDirectory.isBlank()) {
                    sourceDirectory = null;
                }

                if (analyze) {
                    statistics.add(actions.analyze("analyze", Paths.get(targetDirectory)));
                } else if (organize) {
                    sourceDirectories.forEach(source -> {
                        statistics.add(actions.organize("organize", Paths.get(source), Paths.get(targetDirectory)));
                    });
                    if (reorganize) {
                        statistics.add(actions.organize("reorganize", Paths.get(targetDirectory), Paths.get(targetDirectory)));
                    }
                    if (removeDuplicates) {
                        statistics.add(actions.removeDuplicates("no-duplicates", Paths.get(targetDirectory)));
                    }
                    if (removeEmpty) {
                        assertNotNull(sourceDirectory, "Source directory is needed for no-empty action");
                        statistics.add(actions.removeEmpty("no-empty", Paths.get(sourceDirectory)));
                    }
                } else if (synchronize) {
                    if (reorganize) {
                        assertNotNull(sourceDirectory, "Source directory is needed for reorganize action");
                        statistics.add(actions.organize("reorganize-1", Paths.get(sourceDirectory), Paths.get(sourceDirectory)));
                        statistics.add(actions.organize("reorganize-2", Paths.get(targetDirectory), Paths.get(targetDirectory)));
                    }

                    assertNotNull(sourceDirectory, "Source directory is needed for synchronize action");
                    statistics.add(actions.synchronize("synchronize-1", Paths.get(sourceDirectory), Paths.get(targetDirectory)));
                    statistics.add(actions.synchronize("synchronize-2", Paths.get(targetDirectory), Paths.get(sourceDirectory)));

                    if (removeDuplicates) {
                        statistics.add(actions.removeDuplicates("no-duplicates-1", Paths.get(sourceDirectory)));
                        statistics.add(actions.removeDuplicates("no-duplicates-2", Paths.get(targetDirectory)));
                    }
                    if (removeEmpty) {
                        statistics.add(actions.removeEmpty("no-empty-1", Paths.get(sourceDirectory)));
                        statistics.add(actions.removeEmpty("no-empty-2", Paths.get(targetDirectory)));
                    }
                } else {
                    if (reorganize) {
                        statistics.add(actions.organize("reorganize", Paths.get(targetDirectory), Paths.get(targetDirectory)));
                    }
                    if (removeDuplicates) {
                        if (sourceDirectory == null) {
                            statistics.add(actions.removeDuplicates("no-duplicates", Paths.get(targetDirectory)));
                        } else {
                            statistics.add(actions.findCopies("no-copies", Paths.get(sourceDirectory), Paths.get(targetDirectory)));
                        }
                    }
                    if (removeEmpty) {
                        statistics.add(actions.removeEmpty("no-empty", Paths.get(targetDirectory)));
                    }
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

    private void assertNotNull(Object object, String message) {
        if (object == null) {
            logger.error("null", message);
            throw new IllegalStateException(message);
        }
    }

    private String opt(String key, boolean value) {
        return value ? key : "";
    }
}
