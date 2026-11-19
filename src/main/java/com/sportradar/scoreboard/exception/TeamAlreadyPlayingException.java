package com.sportradar.scoreboard.exception;

public class TeamAlreadyPlayingException extends ScoreboardException {
    public TeamAlreadyPlayingException(String teamName) {
        super("Team is already participating in an active match: " + teamName);
    }
}