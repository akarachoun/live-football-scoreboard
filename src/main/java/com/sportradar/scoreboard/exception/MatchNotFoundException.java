package com.sportradar.scoreboard.exception;

import com.sportradar.scoreboard.model.MatchId;

public class MatchNotFoundException extends ScoreboardException {
    public MatchNotFoundException(MatchId matchId) {
        super("Active match not found with id: " + matchId);
    }
}