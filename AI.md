# AI Usage & Engineering Log

This document outlines how AI tools were used during the design, implementation, verification, and documentation of the **Live Football World Cup Scoreboard** library.

## Tools Used

- **AI Assistant (LLM)**: Used as a pair-programming partner for requirement analysis, edge-case brainstorming, boilerplate generation, and documentation structuring.
- **IntelliJ IDEA Static Analysis & Dependency Vulnerability Scanner**: Used locally to verify Java 21 idioms, null-safety warnings, code hygiene, and third-party dependency security.

---

## Summary of How AI Was Used

1. **Requirement Analysis & Open Questions**: Explored ambiguities in the specification (match identification strategy, tie-breaking determinism, simultaneous team participation, and score decrement/VAR handling).
2. **Architecture & API Design**: Evaluated trade-offs between a zero-dependency Java 21 library vs. bringing in frameworks (Spring/Lombok), selecting Java `record` types, a strongly-typed `MatchId` value object, and a cohesive single-class core (`InMemoryScoreboard`).
3. **Feature Ideation**: Evaluated several candidate additions for the 5th requirement (team search, score filtering, event listeners, and score undo) and selected `undoLastScoreUpdate` as the most cohesive, non-overlapping domain feature.
4. **Code & Test Generation**: Generated the Maven structure, implementation classes, Javadocs, and JUnit 5 + AssertJ test suites across structured Git commits.
5. **Documentation**: Drafted and refined `README.md` and this `AI.md` log.

---

## Human Oversight, Corrections & Trade-offs

AI suggestions were critically reviewed and adjusted in several key areas:

- **Security Vulnerability Remediation (`CVE-2026-24400`)**: The AI initially suggested `assertj-core:3.26.3` for unit tests. Local dependency scanning flagged **CVE-2026-24400** (CVSS 7.3) affecting versions up to `3.27.6`. The dependency was upgraded to the patched **`3.27.7`** release before the initial commit.
- **Explicit `MatchId` vs. Team-Pair Lookup**: Rather than identifying matches solely by `(homeTeam, awayTeam)` pairs, a dedicated `MatchId` value object was introduced to provide \(O(1)\) lookups, prevent string-matching fragility, and establish a clean foundation for future persistence or tournament extensions.
- **Orthogonal 5th Feature Selection**: Rejected an initial AI suggestion to add `findMatchByTeam` because it overlapped with `getSummary()`. Selected `undoLastScoreUpdate` instead to support real-world VAR goal disallowances and operator error recovery.
- **Static Analysis & Code Hygiene Refinements**:
    - Removed an unused `MatchId.of(String)` factory method flagged by IDE inspection to keep the public API lean (YAGNI).
    - Replaced `@Nullable` `Deque.peek()` with `Deque.element()` in `undoLastScoreUpdate()` to eliminate a potential NPE inspection warning and explicitly assert the non-empty history invariant.
    - Updated concurrency tests to use Java 21 `ExecutorService` with `try-with-resources` (`AutoCloseable`) and `List.getFirst()`.
- **Repository & Package Hygiene**: Kept the package structure flat (`com.sportradar.scoreboard` without an `impl` subpackage) and omitted a `main` entry point to keep the artifact strictly focused as a reusable library.

---

## Key Prompt History

Below is a chronological summary of the prompts used to guide the design and implementation:

1. **Initial Setup & Requirement Breakdown**
   > *"You are asked to implement a simple library that manages a Live Football World Cup Scoreboard... [full task requirements]. Let's analyze hidden questions, edge cases, and plan the architecture."*
2. **Evaluating Third-Party Frameworks**
   > *"Question about 3rd-party libraries — even though it's not required, I considered using Spring to speed up development and improve extensibility and testing. What are the pros and cons for a library?"*
    - *Outcome*: Decided against Spring/Lombok in runtime dependencies to avoid dependency conflicts for library consumers; kept `JUnit 5` and `AssertJ` strictly in `<scope>test</scope>`.
3. **Selecting an Orthogonal Additional Feature**
   > *"Let's use Java 21. For the 5th feature, I want something that has minimal overlap with existing operations (e.g., searching active matches by team overlaps somewhat with getting the summary)."*
    - *Outcome*: Chose `undoLastScoreUpdate(MatchId)` to support VAR rollbacks and score correction workflows.
4. **Designing Match Identification & Entry Point**
   > *"Let's agree on match identification: I would introduce an explicit ID as a foundation for future extensibility... Should we provide a main entry point or `impl` subpackages?"*
    - *Outcome*: Introduced `MatchId` (`record` wrapping `UUID`), kept a flat package structure without `impl`, and provided `Scoreboard.inMemory()` with Javadoc instead of a `main` method.
5. **Evaluating Java `record` Usage & Class Cohesion**
   > *"Do you consider Java `record`s a plus or a minus in this design? Is it okay that all core logic lives in a single class?"*
    - *Outcome*: Confirmed `record` for public immutable snapshots (`Match`, `MatchId`) and documented the trade-offs of a cohesive single-class core (`InMemoryScoreboard`) along with future decomposition seams.
6. **Reviewing Open Questions & Domain Rules**
   > *"Let's review how we addressed 'The requirements below intentionally include open questions and design choices', including whether `startMatch` should forbid teams that have already played earlier."*
    - *Outcome*: Verified that finished matches release teams so rematches in later tournament stages remain valid, while simultaneous active matches for the same team are prevented.
