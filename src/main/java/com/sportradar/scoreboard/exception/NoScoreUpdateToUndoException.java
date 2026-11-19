package com.sportradar.scoreboard.exception;

import com.sportradar.scoreboard.model.MatchId;

public class NoScoreUpdateToUndoException extends ScoreboardException {
    public NoScoreUpdateToUndoException(MatchId matchId) {
        super("No score update available to undo for match: " + matchId);
    }
}