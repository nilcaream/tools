package com.nilcaream.cptidy;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Singleton
public class ExifService {

    @Inject
    private Logger logger;

    private static final List<Pattern> DATE_PATTERNS = Stream.of(
                    ".*exif.*date/time original.* (20[0123][0-9]):([01][0-9]):([0123][0-9]) .*",
                    ".*exif.*date/time.* (20[0123][0-9]):([01][0-9]):([0123][0-9]) .*")
            .map(t -> Pattern.compile(t, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());

    public DateString getDate(Path source) {
        try (InputStream inputStream = Files.newInputStream(source)) {
            return getDate(source, ImageMetadataReader.readMetadata(inputStream));
        } catch (ImageProcessingException | IOException e) {
            logger.warn("exif-error", source);
        }
        return null;
    }

    private DateString getDate(Path source, Metadata metadata) {
        Set<String> tags = StreamSupport.stream(metadata.getDirectories().spliterator(), false)
                .map(Directory::getTags)
                .flatMap(Collection::stream)
                .map(Tag::toString)
                .collect(Collectors.toSet());

        for (Pattern datePattern : DATE_PATTERNS) {
            DateString date = tags.stream()
                    .map(datePattern::matcher)
                    .filter(Matcher::matches)
                    .peek(m -> logger.debug("exif-string", source, ":", m.group()))
                    .map(m -> new DateString(m.group(1), m.group(2), m.group(3)))
                    .findFirst()
                    .orElse(null);
            if (date != null) {
                logger.debug("exif", source, ":", date);
                return date;
            }
        }

        for (Directory directory : metadata.getDirectories()) {
            DateString date = directory.getTags().stream()
                    .filter(t -> directory.getObject(t.getTagType()).getClass() == Date.class)
                    .filter(t -> t.getTagName().toLowerCase().contains("creation"))
                    .peek(t -> logger.debug("exif-date", source, ":", t))
                    .map(t -> directory.getDate(t.getTagType()))
                    .map(DateString::new)
                    .findFirst()
                    .orElse(null);
            if (date != null) {
                logger.debug("exif", source, ":", date);
                return date;
            }
        }

        logger.debug("exif-none", source);
        return null;
    }
}
