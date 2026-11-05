package com.nilcaream.cptidy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppTest {

    @Test
    void shouldAcceptEveryDeclaredOption() {
        // given
        String[] args = {"--organize", "--no-duplicates", "--no-empty", "--source", "a", "b", "--target", "c", "--delete", "--move", "--copy", "--time",
                "--buffer", "1024", "--configuration", "c.json", "-v", "--", "--operand"};

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
}
