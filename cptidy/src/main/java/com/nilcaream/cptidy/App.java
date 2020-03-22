package com.nilcaream.cptidy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.nilcaream.utilargs.UtilArgs;
import com.nilcaream.utilargs.model.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    @Option(name = 'i')
    private String sourceDirectories;

    @Option(name = 'o')
    private String targetDirectory;

    private Logger logger = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) {
        App app = new App();
        new UtilArgs(args, app);
        app.go();
    }


	private void go() {
        Arrays.stream(sourceDirectories.split(",")).forEach(source -> scan(source,targetDirectory));
    }
    
    private void scan(String source, String target) {
        try (Stream<Path> walk = Files.walk(Paths.get(source))) {
            walk.filter(Files::isRegularFile)
            .forEach(f -> logger.info(f.toString()));
                    //.map(x -> x.toString());

        } catch (IOException e) {
            e.printStackTrace();
        }        
    }

    
}
