package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.InvalidScoreException;
import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe in-memory implementation of {@link Scoreboard}.
 */
public class InMemoryScoreboard implements Scoreboard {

    private static final Comparator<MatchEntry> SUMMARY_COMPARATOR = Comparator
            .comparingInt((MatchEntry entry) -> entry.currentMatch().totalScore())
            .thenComparingLong(MatchEntry::sequenceNumber)
            .reversed();

    private final Map<MatchId, MatchEntry> activeMatches = new HashMap<>();
    private final Set<String> activeTeams = new HashSet<>();
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public MatchId startMatch(String homeTeam, String awayTeam) {
        String normalizedHome = validateAndTrimTeamName(homeTeam, "Home team");
        String normalizedAway = validateAndTrimTeamName(awayTeam, "Away team");

        String homeKey = toCanonicalKey(normalizedHome);
        String awayKey = toCanonicalKey(normalizedAway);

        if (homeKey.equals(awayKey)) {
            throw new IllegalArgumentException("Home team and away team must be different");
        }

        lock.writeLock().lock();
        try {
            if (activeTeams.contains(homeKey)) {
                throw new TeamAlreadyPlayingException(normalizedHome);
            }
            if (activeTeams.contains(awayKey)) {
                throw new TeamAlreadyPlayingException(normalizedAway);
            }

            MatchId matchId = MatchId.generate();
            Match initialMatch = new Match(matchId, normalizedHome, normalizedAway, 0, 0);
            long sequence = sequenceGenerator.incrementAndGet();

            activeMatches.put(matchId, new MatchEntry(initialMatch, sequence));
            activeTeams.add(homeKey);
            activeTeams.add(awayKey);

            return matchId;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateScore(MatchId matchId, int homeScore, int awayScore) {
        Objects.requireNonNull(matchId, "MatchId must not be null");
        if (homeScore < 0 || awayScore < 0) {
            throw new InvalidScoreException("Scores must not be negative: " + homeScore + " - " + awayScore);
        }

        lock.writeLock().lock();
        try {
            MatchEntry existingEntry = activeMatches.get(matchId);
            if (existingEntry == null) {
                throw new MatchNotFoundException(matchId);
            }

            Match current = existingEntry.currentMatch();
            Match updated = new Match(
                    current.id(),
                    current.homeTeam(),
                    current.awayTeam(),
                    homeScore,
                    awayScore
            );

            activeMatches.put(matchId, new MatchEntry(updated, existingEntry.sequenceNumber()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void finishMatch(MatchId matchId) {
        Objects.requireNonNull(matchId, "MatchId must not be null");

        lock.writeLock().lock();
        try {
            MatchEntry removed = activeMatches.remove(matchId);
            if (removed == null) {
                throw new MatchNotFoundException(matchId);
            }

            Match match = removed.currentMatch();
            activeTeams.remove(toCanonicalKey(match.homeTeam()));
            activeTeams.remove(toCanonicalKey(match.awayTeam()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Match> getSummary() {
        lock.readLock().lock();
        try {
            return activeMatches.values().stream()
                    .sorted(SUMMARY_COMPARATOR)
                    .map(MatchEntry::currentMatch)
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private String validateAndTrimTeamName(String teamName, String fieldLabel) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " must not be null or blank");
        }
        return teamName.trim();
    }

    private String toCanonicalKey(String teamName) {
        return teamName.toLowerCase(Locale.ROOT);
    }

    private record MatchEntry(Match currentMatch, long sequenceNumber) {
    }
}