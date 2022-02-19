package com.nilcaream.cptidy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ExplicitDates {

    private final Map<String, Pattern> patternTextToPattern = new HashMap<>();

    private final Map<String, String> patternTextToDate = new HashMap<>();

    public void add(String patternText, String date) {
        Pattern pattern = Pattern.compile(patternText, Pattern.CASE_INSENSITIVE);
        patternTextToPattern.put(patternText, pattern);
        patternTextToDate.put(patternText, date);
    }

    public DateString getDate(Path input) throws IOException {
        String fileName = input.getFileName().toString();
        String match = patternTextToPattern.entrySet().stream()
                .filter(e -> e.getValue().matcher(fileName).matches())
                .findFirst()
                .map(Map.Entry::getKey)
                .map(patternTextToDate::get)
                .orElse(null);

        if (match == null) {
            return null;
        } else {
            return resolve(match, input);
        }
    }

    private DateString resolve(String match, Path input) throws IOException {
        if (match.equals("FILE_DATE")) {
            BasicFileAttributes attr = Files.readAttributes(input, BasicFileAttributes.class);
            return new DateString(attr.creationTime().toString().substring(0, 10));
        } else {
            return new DateString(match);
        }
    }
}
