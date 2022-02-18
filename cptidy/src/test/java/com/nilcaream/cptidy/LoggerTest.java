package com.nilcaream.cptidy;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LoggerTest {

    private Logger underTest = new Logger();

    private Io io = new Io();
    private Path root = Jimfs.newFileSystem(Configuration.unix()).getPath("unix").toAbsolutePath();

    @Test
    void shouldStoreStats() throws IOException {
        // given
        Path path = io.write(root.resolve("file"), "test");

        // when
        underTest.stat("stat", path);
        underTest.debugStat("debug", path);
        underTest.infoStat("info", path);
        Statistics actual = underTest.getStatistics();

        // then
        assertThat(actual.hasData()).isTrue();
        assertThat(actual.getData()).hasSize(3);
        assertThat(actual.getData().get("STAT").getBytes()).isEqualTo(4);
        assertThat(actual.getData().get("STAT").getCount()).isEqualTo(1);
        assertThat(actual.getData().get("DEBUG").getBytes()).isEqualTo(4);
        assertThat(actual.getData().get("DEBUG").getCount()).isEqualTo(1);
        assertThat(actual.getData().get("INFO").getBytes()).isEqualTo(4);
        assertThat(actual.getData().get("INFO").getCount()).isEqualTo(1);
    }
}