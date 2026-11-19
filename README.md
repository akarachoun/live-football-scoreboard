# Live Football World Cup Scoreboard

A lightweight, thread-safe, zero-dependency Java 21 library for managing a Live Football World Cup Scoreboard in memory.

## Features

1. **Start a new match** — Initializes a match with a `0 - 0` score and returns a strongly-typed `MatchId`.
2. **Update the score** — Updates the absolute score of an active match.
3. **Finish a match** — Removes an active match from the scoreboard and frees the participating teams.
4. **Get a summary of matches in progress** — Returns an immutable snapshot of active matches ordered by:
    - Total score (descending)
    - Most recently started match first (when total scores are tied)
5. **Undo last score update (Additional Feature)** — Reverts an active match's score to its state prior to the last update (e.g., VAR goal disallowance or operator input correction).

---

## Requirements & Build

- **Java:** 21+
- **Maven:** 3.9+

### Run Tests

```bash
mvn test
```

### Build JAR

```bash
mvn clean package
```

---

## Quick Start

```java
import com.sportradar.scoreboard.Scoreboard;
import com.sportradar.scoreboard.model.Match;
import com.sportradar.scoreboard.model.MatchId;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        Scoreboard scoreboard = Scoreboard.inMemory();

        MatchId mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        scoreboard.updateScore(mexicoCanada, 0, 5);

        MatchId spainBrazil = scoreboard.startMatch("Spain", "Brazil");
        scoreboard.updateScore(spainBrazil, 10, 2);

        // VAR disallows a goal and later re-applies the valid score
        scoreboard.undoLastScoreUpdate(spainBrazil);
        scoreboard.updateScore(spainBrazil, 10, 2);

        List<Match> summary = scoreboard.getSummary();
        summary.forEach(match ->
                System.out.printf("%s %d - %s %d%n",
                        match.homeTeam(), match.homeScore(),
                        match.awayTeam(), match.awayScore())
        );

        scoreboard.finishMatch(mexicoCanada);
        scoreboard.finishMatch(spainBrazil);
    }
}
```

---

## Additional Feature: `undoLastScoreUpdate`

### Why This Feature Was Chosen
In live football data feeds, score updates are occasionally rolled back due to **VAR (Video Assistant Referee) interventions** or **operator data-entry mistakes**. Adding `undoLastScoreUpdate(MatchId)` directly enhances the core live-scoring workflow without overlapping with `getSummary()`.

### Behavior & Edge Cases
- Every match starts with an initial score state of `(0, 0)` pushed onto its internal score history stack (`Deque<Score>`).
- Each `updateScore` call pushes the new score onto the stack.
- Calling `undoLastScoreUpdate(MatchId)` pops the latest score and restores the previous one while **preserving the match's original start sequence** for tie-breaking.
- Multiple consecutive undos are supported all the way back to the initial `0 - 0` state.
- Calling `undoLastScoreUpdate` when no updates exist to revert (e.g., right after `startMatch` or after undoing back to `0 - 0`) throws `NoScoreUpdateToUndoException`.

---

## Assumptions

1. **Match Identification (`MatchId`)**: Matches are identified via a strongly-typed `MatchId` (wrapping a `UUID`) returned by `startMatch()`. `MatchId` is also exposed inside the `Match` record returned by `getSummary()`.
2. **Team Uniqueness Within Active Matches**: A single national team cannot play two simultaneous matches on the same scoreboard (`TeamAlreadyPlayingException`). Team names are compared case-insensitively after trimming leading/trailing whitespace (`" Mexico "` and `"mexico"` refer to the same team), while preserving the original trimmed formatting for display.
3. **Rematches Across Tournament Stages**: Once a match is finished via `finishMatch()`, the teams are released and may play again in future rounds (e.g., Group Stage vs. Knockout Stage). The scoreboard tracks *live* state only, not historical tournament fixtures.
4. **Absolute Score Updates & Decrements**: `updateScore()` accepts absolute non-negative scores (`>= 0`). Decreasing a score directly via `updateScore()` is permitted alongside `undoLastScoreUpdate()` to support manual corrections.
5. **No Persistence of Finished Matches**: `finishMatch()` completely removes the match from memory to prevent unbounded memory growth during long-running operations.

---

## Design Decisions & Trade-offs

| Decision | Reasoning | Trade-offs |
| :--- | :--- | :--- |
| **Zero Runtime Dependencies (Pure Java 21)** | Avoids dependency conflicts (e.g., Spring version clashes) for library consumers and keeps the JAR minimal and fast. Testing dependencies (`JUnit 5`, `AssertJ`) are strictly scoped to `test`. | Requires writing standard validation and concurrency primitives manually instead of relying on framework annotations. |
| **Strongly-Typed `MatchId` Value Object** | Prevents accidental misuse of raw `UUID`s or typos in team names during `updateScore`/`finishMatch`, enables \(O(1)\) lookups, and prepares the API for future persistence or multi-tournament extensions. | Callers must retain the `MatchId` returned at match start (mitigated by including `MatchId` in `Match` snapshots). |
| **Java `record` for Public Models (`Match`, `MatchId`)** | Guarantees immutability and thread-safety for snapshots returned by `getSummary()`, prevents external mutation of internal scoreboard state, and eliminates boilerplate without Lombok. | Adding new fields to `Match` in future versions would require overloaded constructors to preserve binary compatibility. |
| **Monotonic `AtomicLong` Sequence for Tie-Breaking** | Guarantees deterministic *"most recently started first"* ordering even when multiple matches start within the same nanosecond (e.g., during automated tests or batch ingestion), avoiding `Instant.now()` clock collisions. | Ordering reflects creation order in the scoreboard instance rather than an external kick-off timestamp. |
| **`ReentrantReadWriteLock` Concurrency Control** | Ensures atomic invariant checks across `activeMatches` and `activeTeams` during writes while allowing concurrent non-blocking reads for `getSummary()`. | Slightly higher complexity than a single `synchronized` block, chosen for better read scalability. |
| **Domain-Specific `RuntimeException` Hierarchy** | `ScoreboardException` and its subclasses (`MatchNotFoundException`, `TeamAlreadyPlayingException`, `InvalidScoreException`, `NoScoreUpdateToUndoException`) make contract violations explicit and easy to handle selectively. | Callers must handle or propagate unchecked exceptions on invalid state transitions. |
| **Cohesive Single-Class Core (`InMemoryScoreboard`)** | Keeps atomic state transitions across `activeMatches`, `activeTeams`, and score histories under a single `ReentrantReadWriteLock` without unnecessary abstraction layers (YAGNI/KISS). | As domain rules grow (e.g., match events, timers, persistent storage), logic can be decomposed along clear seams into a rich `LiveMatch` aggregate, a `MatchRepository` port, and an injectable `Comparator<Match>`. |
