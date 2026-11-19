package com.sportradar.scoreboard.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly-typed unique identifier for a match on the scoreboard.
 */
public record MatchId(UUID value) {

    public MatchId {
        Objects.requireNonNull(value, "MatchId value must not be null");
    }

    public static MatchId generate() {
        return new MatchId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}