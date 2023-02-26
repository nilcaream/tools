package com.nilcaream.cptidy;

import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.Option;
import com.nilcaream.utilargs.UtilArgs;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

    @Option(alternative = "count-zeros")
    private boolean countZeros;

    @Option(alternative = "configuration")
    private String configurationFile;

    @Option(alternative = "buffer")
    private int bufferSize;

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

    @Inject
    private FileCompare fileCompare;

    private final List<Statistics> statistics = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
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
        logger.info("Sources", hasSource() ? sourceDirectories.stream().map(this::asPath).map(Path::toString).collect(Collectors.joining(" ")) : "");
        logger.info("Target", hasTarget() ? asPath(targetDirectory) : "");
        logger.info("Options", opt("verbose", verbose), opt("copy", ioService.isCopy()), opt("move", ioService.isMove()), opt("delete", ioService.isDelete()), opt("fast", ioService.isFast()), opt("time", ioService.isTime()));
        logger.info("Actions", opt("analyze", analyze), opt("organize", organize), opt("reorganize", reorganize), opt("no-duplicates", removeDuplicates), opt("synchronize", synchronize), opt("no-empty", removeEmpty));

        marker.setPeriod(5000);

        Configuration configuration = new Configuration();
        if (configurationFile != null) {
            configuration.load(asPath(configurationFile));
        }

        configuration.getIgnored().forEach(ignored -> logger.info("ignored", ignored));
        ioService.setIgnoredFiles(configuration.getIgnored());

        configuration.getExplicitDates().forEach((pattern, value) -> {
            nameResolver.addDate(pattern, value);
            logger.info("explicit date", pattern, "=", value);
        });

        if (bufferSize > 0) {
            fileCompare.updateBufferSize(bufferSize);
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
        logger.error("actions", "--analyze --organize --synchronize --reorganize --no-duplicates --no-empty");
        logger.error("options", "--copy --move --delete --fast");
        //noinspection ConstantConditions
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/manual.txt")))) {
            logger.label("");
            reader.lines().forEach(line -> logger.info("manual", line));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
                if (countZeros) {
                    require(true, false);

                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.countEmptyBlocks("count-zeros", source, fileCompare.getInternalBufferSize())));
                } else if (analyze) {
                    require(true, false);

                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.analyze("analyze", source)));
                } else if (organize) {
                    require(true, true);

                    if (removeDuplicates) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.findCopies("no-copies", source, asPath(targetDirectory))));
                    }

                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.organize("organize", source, asPath(targetDirectory))));

                    if (removeEmpty) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.removeEmpty("no-empty", source)));
                    }
                } else if (removeDuplicates && hasSource() && hasTarget()) {
                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.findCopies("no-copies", source, asPath(targetDirectory))));

                    if (removeEmpty) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.removeEmpty("no-empty", source)));
                    }
                } else if (removeDuplicates && hasSource()) { // random files in source directory
                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.removeDuplicatesGlobally("no-source-copies", source)));

                    if (removeEmpty) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.removeEmpty("no-empty", source)));
                    }
                } else if (removeDuplicates && hasTarget()) { // directory-ordered files in target directory
                    statistics.add(actions.removeDuplicatesPerDirectory("no-target-copies", asPath(targetDirectory)));

                    if (removeEmpty) {
                        statistics.add(actions.removeEmpty("no-empty", asPath(targetDirectory)));
                    }
                } else if (reorganize) {
                    require(true, false);

                    sourceDirectories.stream()
                            .map(this::asPath)
                            .forEach(source -> statistics.add(actions.organize("reorganize", source, source)));

                    if (removeEmpty) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.removeEmpty("no-empty", source)));
                    }
                } else if (removeEmpty) {
                    requireAny();

                    if (hasSource()) {
                        sourceDirectories.stream()
                                .map(this::asPath)
                                .forEach(source -> statistics.add(actions.removeEmpty("no-empty", source)));
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
                if (!logger.getWarns().isEmpty()) {
                    logger.label("warnings");
                    logger.getWarns().forEach(e -> logger.info("warning", e));
                }
                if (!logger.getErrors().isEmpty()) {
                    logger.label("errors");
                    logger.getErrors().forEach(e -> logger.info("error", e));
                }
                logger.label("");
                logger.info("", "buffer size in bytes", fileCompare.getInternalBufferSize());
                logger.info("", "total time", (currentTimeMillis() - time) / 1000, "seconds");
            }
        }
    }

    private String opt(String key, boolean value) {
        return value ? key : "";
    }
}
