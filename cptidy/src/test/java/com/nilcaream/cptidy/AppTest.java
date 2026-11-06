package com.nilcaream.cptidy;

import com.nilcaream.utilargs.UtilArgs;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void shouldAcceptEveryDeclaredOption() {
        // given
        String[] args = {"--organize", "--no-duplicates", "--no-empty", "--source", "a", "b", "--target", "c", "--delete", "--move", "--copy", "--time",
                "--buffer", "1024", "--configuration", "c.json", "--log", "logs", "-v", "--", "--operand"};

        // then
        assertThat(App.unknownOptions(args, App.class, IoService.class)).isEmpty();
    }

    @Test
    void shouldRejectRemovedAndMistypedOptions() {
        // given
        String[] args = {"--organize", "--fast", "--delte", "--source", "a", "-x"};

        // then
        assertThat(App.unknownOptions(args, App.class, IoService.class)).containsExactly("delte", "fast", "x");
    }

    @Test
    void shouldWriteLogToStateDirectoryUnlessGiven() {
        assertThat(App.logDirectory(new String[]{"--analyze", "--source", "a"}, "/home/x")).isEqualTo(Paths.get("/home/x/.local/state/cptidy"));
        assertThat(App.logDirectory(new String[]{"--analyze", "--log", "/var/tmp/logs", "--source", "a"}, "/home/x")).isEqualTo(Paths.get("/var/tmp/logs"));
    }

    @Test
    void shouldAcceptActionsThatRunTogether() {
        assertThat(conflict("--organize", "--no-duplicates", "--no-empty")).isNull();
        assertThat(conflict("--reorganize", "--no-empty")).isNull();
        assertThat(conflict("--no-duplicates", "--no-empty")).isNull();
        assertThat(conflict("--synchronize", "--source", "a")).isNull();
        assertThat(conflict("--analyze")).isNull();
    }

    @Test
    void shouldRejectActionsThatWouldRunOnlyInPart() {
        assertThat(conflict("--analyze", "--organize")).contains("choose one");
        assertThat(conflict("--reorganize", "--no-duplicates")).contains("--no-duplicates runs alone");
        assertThat(conflict("--synchronize", "--no-empty")).contains("--no-empty runs alone");
        assertThat(conflict("--count-zeros", "--no-duplicates")).contains("--no-duplicates runs alone");
        assertThat(conflict("--synchronize", "--source", "a", "--source", "b")).isEqualTo("--synchronize takes one --source");
    }

    private static String conflict(String... args) {
        App app = new App();
        UtilArgs.bind(args, app);
        return app.conflictingActions();
    }
}
