package com.sportradar.scoreboard;

import com.sportradar.scoreboard.exception.InvalidScoreException;
import com.sportradar.scoreboard.exception.MatchNotFoundException;
import com.sportradar.scoreboard.exception.NoScoreUpdateToUndoException;
import com.sportradar.scoreboard.exception.TeamAlreadyPlayingException;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryScoreboardTest {

    private Scoreboard scoreboard;

    @BeforeEach
    void setUp() {
        scoreboard = Scoreboard.inMemory();
    }

    @Nested
    @DisplayName("Start Match")
    class StartMatchTests {

        @Test
        void shouldStartMatchWithZeroScore() {
            MatchId id = scoreboard.startMatch("Mexico", "Canada");

            assertThat(id).isNotNull();
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Mexico", "Canada", 0, 0));
        }

        @Test
        void shouldTrimTeamNamesOnStart() {
            MatchId id = scoreboard.startMatch("  Spain  ", "  Brazil ");

            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Spain", "Brazil", 0, 0));
        }

        @Test
        void shouldRejectNullOrBlankTeamNames() {
            assertThatThrownBy(() -> scoreboard.startMatch(null, "Canada"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> scoreboard.startMatch("Mexico", "   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectSameHomeAndAwayTeamIgnoringCase() {
            assertThatThrownBy(() -> scoreboard.startMatch("Germany", "germany"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldRejectMatchIfTeamIsAlreadyPlaying() {
            scoreboard.startMatch("Mexico", "Canada");

            assertThatThrownBy(() -> scoreboard.startMatch("mexico", "Brazil"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
            assertThatThrownBy(() -> scoreboard.startMatch("Argentina", "CANADA"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);
        }
    }

    @Nested
    @DisplayName("Update Score")
    class UpdateScoreTests {

        @Test
        void shouldUpdateScoreForActiveMatch() {
            MatchId id = scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore(id, 3, 2);

            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Spain", "Brazil", 3, 2));
        }

        @Test
        void shouldRejectNegativeScores() {
            MatchId id = scoreboard.startMatch("Spain", "Brazil");

            assertThatThrownBy(() -> scoreboard.updateScore(id, -1, 0))
                    .isInstanceOf(InvalidScoreException.class);
            assertThatThrownBy(() -> scoreboard.updateScore(id, 0, -2))
                    .isInstanceOf(InvalidScoreException.class);
        }

        @Test
        void shouldThrowWhenUpdatingNonExistentMatch() {
            MatchId unknownId = MatchId.generate();

            assertThatThrownBy(() -> scoreboard.updateScore(unknownId, 1, 0))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Finish Match")
    class FinishMatchTests {

        @Test
        void shouldRemoveMatchFromScoreboardAndAllowTeamsToPlayAgain() {
            MatchId id = scoreboard.startMatch("Uruguay", "Italy");
            scoreboard.finishMatch(id);

            assertThat(scoreboard.getSummary()).isEmpty();

            MatchId rematchId = scoreboard.startMatch("Uruguay", "Italy");
            assertThat(scoreboard.getSummary()).hasSize(1);
            assertThat(rematchId).isNotEqualTo(id);
        }

        @Test
        void shouldThrowWhenFinishingUnknownOrAlreadyFinishedMatch() {
            MatchId id = scoreboard.startMatch("Uruguay", "Italy");
            scoreboard.finishMatch(id);

            assertThatThrownBy(() -> scoreboard.finishMatch(id))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Summary Ordering")
    class SummaryOrderingTests {

        @Test
        void shouldReturnEmptyListWhenNoMatchesInProgress() {
            assertThat(scoreboard.getSummary()).isEmpty();
        }

        @Test
        void shouldOrderMatchesByTotalScoreDescendingAndMostRecentlyStartedFirst() {
            MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
            scoreboard.updateScore(mexicoCanada, 0, 5);

            MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");
            scoreboard.updateScore(spainBrazil, 10, 2);

            MatchId germanyFrance = scoreboard.startMatch("Germany", "France");
            scoreboard.updateScore(germanyFrance, 2, 2);

            MatchId uruguayItaly = scoreboard.startMatch("Uruguay", "Italy");
            scoreboard.updateScore(uruguayItaly, 6, 6);

            MatchId argentinaAustralia = scoreboard.startMatch("Argentina", "Australia");
            scoreboard.updateScore(argentinaAustralia, 3, 1);

            List<Match> summary = scoreboard.getSummary();

            assertThat(summary).containsExactly(
                    new Match(uruguayItaly, "Uruguay", "Italy", 6, 6),
                    new Match(spainBrazil, "Spain", "Brazil", 10, 2),
                    new Match(mexicoCanada, "Mexico", "Canada", 0, 5),
                    new Match(argentinaAustralia, "Argentina", "Australia", 3, 1),
                    new Match(germanyFrance, "Germany", "France", 2, 2)
            );
        }
    }

    @Test
    @DisplayName("Concurrency: Should handle parallel score updates safely")
    void shouldHandleConcurrentUpdatesSafely() throws InterruptedException {
        MatchId id = scoreboard.startMatch("Brazil", "Argentina");
        int threads = 10;
        CountDownLatch latch = new CountDownLatch(threads);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 1; i <= threads; i++) {
                final int score = i;
                executor.submit(() -> {
                    try {
                        scoreboard.updateScore(id, score, score);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        }

        List<Match> summary = scoreboard.getSummary();
        assertThat(summary).hasSize(1);
        assertThat(summary.getFirst().homeScore()).isBetween(1, threads);
    }

    @Nested
    @DisplayName("Undo Last Score Update")
    class UndoScoreUpdateTests {

        @Test
        void shouldRevertToPreviousScoreAndPreserveStartSequence() {
            MatchId match1 = scoreboard.startMatch("Mexico", "Canada");
            MatchId match2 = scoreboard.startMatch("Spain", "Brazil");

            scoreboard.updateScore(match1, 1, 0);
            scoreboard.updateScore(match1, 2, 0);
            scoreboard.updateScore(match2, 1, 0);

            // VAR cancels Mexico's second goal -> both matches are 1-0, match2 started later so it stays first
            scoreboard.undoLastScoreUpdate(match1);

            assertThat(scoreboard.getSummary()).containsExactly(
                    new Match(match2, "Spain", "Brazil", 1, 0),
                    new Match(match1, "Mexico", "Canada", 1, 0)
            );
        }

        @Test
        void shouldSupportMultipleConsecutiveUndosBackToInitialZeroZero() {
            MatchId id = scoreboard.startMatch("Germany", "France");
            scoreboard.updateScore(id, 1, 0);
            scoreboard.updateScore(id, 1, 1);

            scoreboard.undoLastScoreUpdate(id);
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Germany", "France", 1, 0));

            scoreboard.undoLastScoreUpdate(id);
            assertThat(scoreboard.getSummary())
                    .containsExactly(new Match(id, "Germany", "France", 0, 0));

            assertThatThrownBy(() -> scoreboard.undoLastScoreUpdate(id))
                    .isInstanceOf(NoScoreUpdateToUndoException.class);
        }

        @Test
        void shouldThrowWhenUndoingMatchWithNoUpdates() {
            MatchId id = scoreboard.startMatch("Argentina", "Australia");

            assertThatThrownBy(() -> scoreboard.undoLastScoreUpdate(id))
                    .isInstanceOf(NoScoreUpdateToUndoException.class);
        }

        @Test
        void shouldThrowWhenUndoingUnknownMatch() {
            assertThatThrownBy(() -> scoreboard.undoLastScoreUpdate(MatchId.generate()))
                    .isInstanceOf(MatchNotFoundException.class);
        }
    }
}