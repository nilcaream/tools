package com.nilcaream.cptidy;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateString {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    private final String year;
    private final String month;
    private final String day;

    // yyyy-MM-dd
    public DateString(String date) {
        String[] parts = date.split("-");
        this.year = parts[0];
        this.month = parts[1];
        this.day = parts[2];
    }

    public DateString(Date date) {
        String[] parts = DATE_FORMAT.format(date).split("-");
        this.year = parts[0];
        this.month = parts[1];
        this.day = parts[2];
    }

    public DateString(String year, String month, String day) {
        this.year = year;
        this.month = month;
        this.day = day;
    }

    public String asShort() {
        return year + "-" + month;
    }

    public String asLong() {
        return year + month + day;
    }

    @Override
    public String toString() {
        return year + "-" + month + "-" + day;
    }
}
