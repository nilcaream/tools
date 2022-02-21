package com.nilcaream.cptidy;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class NameResolver {

    // 1 - prefix, 2 - yyyyMMdd, 3 - yyyy, 4 - MM, 5 - dd, 6 - suffix
    private static final Pattern NAME_EXTENSION = Pattern.compile("(.*)((20[0123][0-9])([01][0-9])([0123][0-9]))([^0-9].+)");
    private static final Set<String> EXTENSIONS = Set.of(".jpg", ".jpeg", ".mp4", ".mpeg", ".avi", ".mov", ".mts", ".gif");

    @Inject
    private Logger logger;

    @Inject
    private ExifService exifService;

    private ExplicitDates explicitDates = new ExplicitDates();

    // naming convention for file name test.txt:
    // nameExtension: test.txt
    // name: test
    // extension: .txt

    public void addDate(String patternText, String date) {
        explicitDates.add(patternText, date);
    }

    public Result resolve(Path input) throws IOException {
        StatusHolder status = new StatusHolder();

        String inputNameExtension = input.getFileName().toString();
        String nameExtension = prepare(status, inputNameExtension);
        String extension = getExtension(nameExtension);

        Result result = null;
        Matcher matcher = NAME_EXTENSION.matcher(inputNameExtension);

        DateString explicitDate = explicitDates.getDate(input);

        if (explicitDate != null) {
            result = new Result(explicitDate.asShort(), overrideDate(status, inputNameExtension, explicitDate.asLong()));
        } else if (matcher.matches() && EXTENSIONS.contains(extension)) {
            DateString date = toDateString(matcher);
            result = new Result(date.asShort(), prefixDate(status, nameExtension, matcher));
        } else if (EXTENSIONS.contains(extension)) {
            DateString date = exifService.getDate(input);
            BasicFileAttributes attr = Files.readAttributes(input, BasicFileAttributes.class);
            String fileDateTime = attr.creationTime().toString().substring(0, 7);
            if (date != null) {
                result = new Result(date.asShort(), exifDate(status, nameExtension, date.asLong()));
            } else if (input.getFileName().toString().contains(fileDateTime)) {
                date = new DateString(attr.creationTime().toString().substring(0, 10));
                result = new Result(date.asShort(), fileDate(status, nameExtension, date.asLong()));
            }
        }

        if (result == null) {
            // if result is not determined then actual status does not matter
            logger.infoStat(Status.NO_MATCH.name(), input);
        } else if (result.file.equals(inputNameExtension)) {
            logger.debugStat(Status.NO_NAME_CHANGE.name(), input);
        } else {
            logger.infoStat(status.status.name(), input, ":", result);
        }

        return result;
    }

    public Path buildUniquePath(Path input) {
        Path root = input.getParent();
        String nameExtension = prepareOnly(input.getFileName().toString());
        String name = getName(nameExtension);
        String extension = getExtension(nameExtension);

        int index = 0;
        Path result = root.resolve(nameExtension);
        while (Files.exists(result)) {
            String infix = name.endsWith("-") ? "" : "-";
            result = root.resolve(prepareOnly(name + infix + index + extension));
            index++;
        }
        return result;
    }

    private DateString toDateString(Matcher matcher) {
        return new DateString(matcher.group(3), matcher.group(4), matcher.group(5));
    }

    private String prepareOnly(String nameExtension) {
        String name = getName(nameExtension).toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replace(" ", "-");
        String extension = getExtension(nameExtension).toLowerCase();
        return name + extension;
    }

    private String prepare(StatusHolder statusHolder, String nameExtension) {
        String result = prepareOnly(nameExtension);
        if (!result.equals(nameExtension)) {
            statusHolder.set(Status.NEW_NAME);
        }
        return result;
    }

    private String fileDate(StatusHolder statusHolder, String nameExtension, String date) {
        String result = overrideDate(statusHolder, nameExtension, date);

        if (!result.equals(nameExtension)) {
            statusHolder.set(Status.FILE_DATE);
        }
        return result;
    }

    private String exifDate(StatusHolder statusHolder, String nameExtension, String date) {
        String result = overrideDate(statusHolder, nameExtension, date);

        if (!result.equals(nameExtension)) {
            statusHolder.set(Status.EXIF_DATE);
        }
        return result;
    }

    private String applyDate(String date, String nameExtension) {
        String input = prepareOnly(nameExtension.replace(date, ""));
        if (input.startsWith("-")) {
            return date + input;
        } else {
            return date + "-" + input;
        }
    }

    private String prefixDate(StatusHolder statusHolder, String nameExtension, Matcher matcher) {
        String result = prepare(statusHolder, applyDate(matcher.group(2), matcher.group(1) + matcher.group(6)));

        if (!result.equals(nameExtension)) {
            statusHolder.set(Status.PREFIX_DATE);
        }
        return result;
    }

    private String overrideDate(StatusHolder statusHolder, String nameExtension, String date) {
        Matcher matcher = NAME_EXTENSION.matcher(nameExtension);
        String result;

        if (matcher.matches()) {
            result = prepare(statusHolder, applyDate(date, matcher.group(1) + matcher.group(6)));
        } else {
            result = prepare(statusHolder, applyDate(date, nameExtension));
        }

        if (!result.equals(nameExtension)) {
            statusHolder.set(Status.OVERRIDE_DATE);
        }
        return result;
    }

    // test.txt -> test
    private String getName(String nameExtension) {
        int index = nameExtension.lastIndexOf(".");
        if (index == -1) {
            return nameExtension;
        } else {
            return nameExtension.substring(0, index);
        }
    }

    // test.txt -> .txt (with dot)
    private String getExtension(String nameExtension) {
        int index = nameExtension.lastIndexOf(".");
        if (index == -1) {
            return "";
        } else {
            return nameExtension.substring(index);
        }
    }

    private enum Status {
        NO_MATCH,
        NEW_NAME,
        PREFIX_DATE,
        OVERRIDE_DATE,
        EXIF_DATE,
        FILE_DATE,
        NO_NAME_CHANGE
    }

    private static class StatusHolder {
        Status status = Status.NO_MATCH;

        void set(Status status) {
            if (status.ordinal() > this.status.ordinal()) {
                this.status = status;
            }
        }
    }

    public static final class Result {
        private final String parent;
        private final String file;

        public Result(String parent, String file) {
            this.parent = parent;
            this.file = file;
        }

        public Path resolve(Path root) {
            return root.resolve(parent).resolve(file);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return parent.equals(result.parent) && file.equals(result.file);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parent, file);
        }

        @Override
        public String toString() {
            return parent + " : " + file;
        }
    }
}
