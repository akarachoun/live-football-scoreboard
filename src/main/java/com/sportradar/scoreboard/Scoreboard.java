package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.NoScoreUpdateToUndoException;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;

import java.util.List;

/**
 * Live Football World Cup Scoreboard management API.
 */
public interface Scoreboard {
    /**
     * Creates a new thread-safe in-memory {@link Scoreboard} instance.
     *
     * @return a new {@link InMemoryScoreboard} instance
     */
    static Scoreboard inMemory() {
        return new InMemoryScoreboard();
    }

    /**
     * Starts a new match with an initial score of 0 - 0.
     *
     * @param homeTeam name of the home team
     * @param awayTeam name of the away team
     * @return unique identifier of the started match
     */
    MatchId startMatch(String homeTeam, String awayTeam);

    /**
     * Updates the absolute score of an active match.
     *
     * @param matchId   identifier of the active match
     * @param homeScore new home team score (must be >= 0)
     * @param awayScore new away team score (must be >= 0)
     */
    void updateScore(MatchId matchId, int homeScore, int awayScore);

    /**
     * Reverts the score of an active match to its state prior to the last score update.
     *
     * @param matchId identifier of the active match
     * @throws MatchNotFoundException       if the match is not active
     * @throws NoScoreUpdateToUndoException if no score updates can be undone
     */
    void undoLastScoreUpdate(MatchId matchId);

    /**
     * Finishes an active match and removes it from the scoreboard.
     *
     * @param matchId identifier of the active match
     */
    void finishMatch(MatchId matchId);

    /**
     * Returns a snapshot of all matches currently in progress, ordered by:
     * 1. Total score (descending)
     * 2. Most recently started match first (if total scores are tied)
     *
     * @return unmodifiable ordered list of active matches
     */
    List<Match> getSummary();
}