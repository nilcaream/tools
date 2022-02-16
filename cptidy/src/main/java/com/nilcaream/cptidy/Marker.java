package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;

import static java.lang.System.currentTimeMillis;

@Singleton
public class Marker {

    @Inject
    private Logger logger;

    private long now = currentTimeMillis();
    private long time = currentTimeMillis();
    private long period = 1000;

    public void setPeriod(long period) {
        this.period = period;
    }

    public void reset() {
        time = currentTimeMillis();
    }

    public void mark(Object object) {
        now = currentTimeMillis();
        if (now - time > period) {
            logger.info("mark", object);
            time = now;
        }
    }
}
