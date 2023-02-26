package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.lang.System.currentTimeMillis;

@Singleton
public class Marker {

    @Inject
    private Logger logger;

    private long start;
    private long lastMark;
    private long period = 1000;
    private int counter = 0;

    public void setPeriod(long period) {
        this.period = period;
    }

    public void reset() {
        start = currentTimeMillis();
        lastMark = start;
        counter = 0;
    }

    public void mark(Object object) {
        counter++;
        if (currentTimeMillis() - lastMark > period) {
            logger.info("mark", object, ":", counter);
            lastMark = currentTimeMillis();
        }
    }

    public long getElapsed() {
        return currentTimeMillis() - start;
    }
}
