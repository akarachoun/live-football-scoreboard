package com.sportradar.scoreboard;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScoreboardTest {

    @Test
    void shouldVerifyTestEnvironmentWorks() {
        Scoreboard scoreboard = new Scoreboard() {};
        assertThat(scoreboard).isNotNull();
    }
}
