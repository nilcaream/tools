package com.nilcaream.cptidy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import com.nilcaream.atto.Atto;
import com.nilcaream.utilargs.UtilArgs;
import com.nilcaream.utilargs.model.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    @Option(name = 'i')
    private String sourceDirectories;

    @Option(name = 'o')
    private String targetDirectory;

    @Inject
    private IoService ioService;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) {
        App app = Atto.builder().build().instance(App.class);
        new UtilArgs(args, app);
        app.go();
    }

    private void go() {
        Arrays.stream(sourceDirectories.split(",")).forEach(source -> scan(source, targetDirectory));
    }

    private void scan(String sourceRoot, String targetRoot) {
        try (Stream<Path> walk = Files.walk(Paths.get(sourceRoot))) {
            walk.filter(Files::isRegularFile).forEach(source -> {
                try {
                    Path target = ioService.buildTarget(source, targetRoot);
                    if (target != null && !Files.isSameFile(source, target)) {
                        target = ioService.checkDuplicate(source, target);
                        if (target == null) {
                            ioService.delete(source);
                        } else {
                            ioService.move(source, target);
                        }
                    }
                } catch (IOException e) {
                    logger.error("Fail", e);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
