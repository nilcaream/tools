package com.nilcaream.cptidy;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
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
                    ".*exif.*date/time original.* (20[012][0-9]):([01][0-9]):([0123][0-9]) .*",
                    ".*exif.*date/time.* (20[012][0-9]):([01][0-9]):([0123][0-9]) .*")
            .map(t -> Pattern.compile(t, Pattern.CASE_INSENSITIVE)).collect(Collectors.toList());

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM");

    public String getDate(Path source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(java.nio.file.Files.newInputStream(source));
            return getDate(source, metadata);
        } catch (ImageProcessingException | IOException e) {
            logger.error("exif-error", source);
        }
        return null;
    }

    private String getDate(Path source, Metadata metadata) {
        Set<String> tags = StreamSupport.stream(metadata.getDirectories().spliterator(), false)
                .map(Directory::getTags)
                .flatMap(Collection::stream)
                .map(Tag::toString)
                .collect(Collectors.toSet());

        for (Pattern datePattern : DATE_PATTERNS) {
            String date = tags.stream()
                    .map(datePattern::matcher)
                    .filter(Matcher::matches)
                    .peek(m -> logger.debug("exif-str", source, ":", m.group()))
                    .map(m -> m.group(1) + "-" + m.group(2))
                    .findFirst()
                    .orElse(null);
            if (date != null) {
                logger.info("exif", source, ":", date);
                return date;
            }
        }

        for (Directory directory : metadata.getDirectories()) {
            String date = directory.getTags().stream()
                    .filter(t -> directory.getObject(t.getTagType()).getClass() == Date.class)
                    .filter(t -> t.getTagName().toLowerCase().contains("creation"))
                    .peek(t -> logger.debug("exif-cls", source, ":", t))
                    .map(t -> directory.getDate(t.getTagType()))
                    .map(DATE_FORMAT::format)
                    .findFirst()
                    .orElse(null);
            if (date != null) {
                logger.info("exif", source, ":", date);
                return date;
            }
        }

        logger.warn("exif-none", source);
        return null;
    }
}
