package com.nilcaream.cptidy;

import ch.qos.logback.classic.Level;
import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.Option;
import com.nilcaream.utilargs.UtilArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;

public class App {

    @Option(value = "i", alternative = "input")
    private List<String> sourceDirectories;

    @Option(value = "o", alternative = "output")
    private String targetDirectory;

    @Option(value = "d", alternative = "dryRun")
    private boolean dryRun;

    @Option(value = "v", alternative = "verbose")
    private boolean verbose;

    @Inject
    private IoService ioService;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) {
        App app = Atto.builder().build().instance(App.class);
        UtilArgs.bind(args, app);
        app.go();
    }

    private void go() {
        if (verbose) {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);
        }

        ioService.info("Source", sourceDirectories);
        ioService.info("Target", targetDirectory);
        ioService.info("Dry run", dryRun);
        ioService.debug("Verbose", verbose);
        ioService.info("", "----------------------------------------------------------------");

        if (sourceDirectories == null || sourceDirectories.isEmpty() || targetDirectory == null) {
            ioService.error("no input", "Provide input arguments: -i sourceDirectory -o targetDirectory");
        } else {
            ioService.setDryRun(dryRun);
            sourceDirectories.forEach(source -> {
                ioService.info("scan", source + " > " + targetDirectory);
                scan(source, targetDirectory);
            });
            if (!dryRun) {
                ioService.info("lowercase", targetDirectory);
                scan(targetDirectory, targetDirectory);
            }
        }

        ioService.info("", "----------------------------------------------------------------");
        ioService.getStatistics().getData().forEach((k, v) -> ioService.info(k, format("%d files, %d MB", v.getCount(), v.getBytes() / (1024 * 1024))));
    }

    private void scan(String sourceRoot, String targetRoot) {
        try (Stream<Path> walk = Files.walk(Paths.get(sourceRoot))) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = ioService.buildMatchingTarget(source, targetRoot);
                    if (target == null) {
                        // file is not matching target pattern
                        ioService.debug("no match", source);
                    } else if (ioService.isSameFile(source, target)) {
                        // file is already in target location
                        ioService.debug("ok location", source);
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
                    logger.error("File processing error", e);
                }
            });
        } catch (IOException e) {
            logger.error("Directory processing error", e);
        }
    }
}
