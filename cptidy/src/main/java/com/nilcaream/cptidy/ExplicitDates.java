package com.nilcaream.cptidy;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ExplicitDates {

    private final Map<String, Pattern> patternTextToPattern = new HashMap<>();

    private final Map<String, DateString> patternTextToDate = new HashMap<>();

    public void add(String patternText, String date) {
        Pattern pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
        patternTextToPattern.put(patternText, pattern);
        patternTextToDate.put(patternText, new DateString(date));
    }

    public DateString getDate(String fileName) {
        return patternTextToPattern.entrySet().stream()
                .filter(e -> e.getValue().matcher(fileName).matches())
                .findFirst()
                .map(Map.Entry::getKey)
                .map(patternTextToDate::get)
                .orElse(null);
    }
}
