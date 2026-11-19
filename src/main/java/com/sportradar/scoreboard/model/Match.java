package com.sportradar.scoreboard.model;

import java.util.Objects;

/**
 * Immutable snapshot of an active match on the scoreboard.
 */
public record Match(
        MatchId id,
        String homeTeam,
        String awayTeam,
        int homeScore,
        int awayScore
) {
    public Match {
        Objects.requireNonNull(id, "Match id must not be null");
        if (homeTeam == null || homeTeam.isBlank()) {
            throw new IllegalArgumentException("Home team name must not be null or blank");
        }
        if (awayTeam == null || awayTeam.isBlank()) {
            throw new IllegalArgumentException("Away team name must not be null or blank");
        }
        if (homeScore < 0 || awayScore < 0) {
            throw new IllegalArgumentException("Scores must not be negative");
        }
    }

    public int totalScore() {
        return homeScore + awayScore;
    }
}