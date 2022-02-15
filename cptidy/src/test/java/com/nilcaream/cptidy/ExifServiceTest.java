package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExifServiceTest {

    @InjectMocks
    private ExifService underTest = new ExifService();

    @Spy
    private Logger logger = new Logger();

    @Test
    void shouldExtractOriginalDate() {
        // given
        Path source = Paths.get("src", "test", "resources", "exif-original.jpg");

        // when
        String actual = underTest.getDate(source);

        // then
        assertThat(actual).isEqualTo("2008-05");
    }

    @Test
    void shouldNotFailOnNoExif() {
        // given
        Path source = Paths.get("src", "test", "resources", "no-exif.jpg");

        // when
        String actual = underTest.getDate(source);

        // then
        assertThat(actual).isNull();
    }

    @Test
    void shouldExtractDateTime() {
        // given
        Path source = Paths.get("src", "test", "resources", "exif-no-original.jpg");

        // when
        String actual = underTest.getDate(source);

        // then
        assertThat(actual).isEqualTo("2022-02");
    }
}