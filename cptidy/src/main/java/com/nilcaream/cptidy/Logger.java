package com.nilcaream.cptidy;

import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class Logger {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    private final List<String> errors = new ArrayList<>();

    public void setDebug() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);
    }

    public void disable() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
    }

    public void debug(String status, Object... messages) {
        logger.debug("{} {}", formatStatus(status), asString(messages));
    }

    public void label(String status) {
        logger.info("{} ----------------------------------------------------------------", formatStatus(status));
    }

    public void info(String status, Object... messages) {
        logger.info("{} {}", formatStatus(status), asString(messages));
    }

    public void warn(String status, Object... messages) {
        logger.warn("{} {}", formatStatus(status), asString(messages));
    }

    public void error(String status, Object... messages) {
        logger.error("{} {}", formatStatus(status), asString(messages));
        storeError(status, asString(messages));
    }

    public void error(String status, Throwable e, Object... messages) {
        logger.error(formatStatus(status) + " " + asString(messages), e);
        storeError(status, e, asString(messages));
    }

    public List<String> getErrors() {
        return errors;
    }

    private void storeError(Object... messages) {
        errors.add(asString(messages));
        if (errors.size() > 32) {
            throw new IllegalStateException("Exceeded max error count");
        }
    }

    private String asString(Object... messages) {
        if (messages == null || messages.length == 0) {
            return "";
        } else {
            return Arrays.stream(messages).map(String::valueOf).map(String::trim).filter(e -> !e.isEmpty()).collect(Collectors.joining(" "));
        }
    }

    private String formatStatus(String status) {
        String upper = status.toUpperCase().replace(" ", "-").trim();
        return (upper + "                                ").substring(0, 16);
    }
}
