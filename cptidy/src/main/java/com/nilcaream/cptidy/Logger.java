package com.nilcaream.cptidy;

import ch.qos.logback.classic.Level;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Arrays;
import java.util.stream.Collectors;

@Singleton
public class Logger {

    private final org.slf4j.Logger logger = LoggerFactory.getLogger(getClass());

    public void setDebug() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);
    }

    public void disable() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.OFF);
    }

    public void debug(String status, Object... messages) {
        logger.debug("{} {}", formatStatus(status), asString(messages));
    }

    public void info(String status, Object... messages) {
        logger.info("{} {}", formatStatus(status), asString(messages));
    }

    public void warn(String status, Object... messages) {
        logger.warn("{} {}", formatStatus(status), asString(messages));
    }

    public void error(String status, Object... messages) {
        logger.error("{} {}", formatStatus(status), asString(messages));
    }

    public void error(String status, Throwable e, Object... messages) {
        logger.error(formatStatus(status) + " " + asString(messages), e);
    }

    private String asString(Object... messages) {
        if (messages == null || messages.length == 0) {
            return "";
        } else {
            return Arrays.stream(messages).map(String::valueOf).collect(Collectors.joining(" "));
        }
    }

    private String formatStatus(String status) {
        String upper = status.toUpperCase().replace(" ", "-").trim();
        return (upper + "                                ").substring(0, 12);
    }
}
