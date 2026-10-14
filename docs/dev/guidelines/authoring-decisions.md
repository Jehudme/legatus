# Architecture Decision Records (ADR) Specification

## Document Metadata
* **Guideline ID:** GL-0006
* **Scope:** Architecture & Systems Design (`docs/dev/decisions/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

Architectural entropy is the primary threat to long-term distributed systems maintainability. In systems combining low-level C++ runtime components with Java orchestration backends, critical architectural decisions are frequently lost in transient pull request discussions, chat channels, or code comments. When original authors depart or architectural context fades, future engineers inadvertently re-introduce failed patterns or violate hidden system constraints.

This specification formalizes the authoring, review, and lifecycle of **Architecture Decision Records (ADRs)** within the Legatus ecosystem, standardizing on the **Markdown Architectural Decision Records (MADR)** schema enriched with Michael Nygard's structural principles. ADRs serve as the immutable, authoritative historical log of significant architectural choices, their contextual trade-offs, considered alternatives, and operational consequences.

---

## 2. Normative Invariants & Core Principles

All architectural decisions **MUST** adhere to the following binding invariants:

1. **Mandatory ADR Threshold:** An ADR **MUST** be authored for any change that:
   * Introduces, replaces, or removes a third-party dependency or runtime library (e.g., `reproc++`, `nng`, `IXWebSocket`).
   * Modifies cluster network topology, wire protocol encoding, or authentication mechanisms.
   * Changes concurrency models (e.g., migrating Java platform threads to Loom virtual threads).
   * Dictates persistent storage schemas, database engines, or serialization standards.
   * Defines cross-platform IPC or OS abstraction layers across Linux, Windows, and macOS.
2. **Absolute Immutability Invariant:** Once merged into `master`, an ADR **SHALL NOT** be altered to change its historical decision or intent. Historical ADRs represent reality at the time of commit. If technical requirements or hardware invariants shift, an engineer **MUST** author a new ADR containing a `Supersedes: ADR-XXXX` reference. Only grammatical corrections or retroactive supersession references **MAY** be applied to merged records.
3. **No Retrospective Rubber-Stamping:** An ADR **MUST** be authored, reviewed, and approved **prior** to merging the functional code implementation. Writing an ADR post-hoc to justify merged code is strictly prohibited.
4. **Mandatory Three-Alternative Threshold:** The "Considered Options" section **MUST** evaluate at least **three realistic, viable architectural alternatives**. Constructing biased "strawman" options designed solely to make the chosen option look favorable is a fatal review defect.
5. **Mandatory Explicit Negative Consequences:** Every chosen option carries trade-offs. An ADR **MUST** document explicit negative consequences, operational complexities, or memory/CPU penalties introduced by the choice. Claims of "zero drawbacks" are invalid.

---

## 3. Structural Specification & Schema

### 3.1 Filename and Storage Conventions

All ADR documents **MUST** be stored in `docs/dev/decisions/` and follow the strict naming convention:
```text
docs/dev/decisions/XXXX-lowercase-title-separated-by-hyphens.md
```
* `XXXX` is a zero-padded, four-digit sequential integer (e.g., `0008-adopt-reproc-for-task-isolation.md`).
* The root index `docs/dev/decisions/README.md` **MUST** be updated synchronously to list the new ADR.

### 3.2 Canonical MADR Layout

Every ADR **MUST** encompass the following structural sections in order:

```text
# ADR-XXXX: <Short Decision Title>

## Metadata
* **Status:** [PROPOSED | ACCEPTED | SUPERSEDED | REJECTED | DEPRECATED]
* **Deciders:** [List of GitHub usernames / contributors]
* **Date:** [YYYY-MM-DD when decision was accepted]
* **Technical Story:** [Issue # / PR #]
* **Supersedes:** [ADR-YYYY | None]
* **Superseded By:** [ADR-ZZZZ | None]

## 1. Context and Problem Statement
[Detailed background, architectural environment, constraints, and the problem requiring resolution.]

## 2. Decision Drivers (Forces)
[Numbered list of competing forces, performance goals, platform constraints, and invariants.]

## 3. Considered Options
* Option 1: [Candidate A]
* Option 2: [Candidate B]
* Option 3: [Candidate C]

## 4. Evaluation of Considered Options
[Deep technical comparison of each candidate against the decision drivers, with explicit pros/cons.]

## 5. Decision Outcome
[Chosen option with authoritative justification.]

## 6. Consequences & Operational Impact
* Positive Consequences
* Negative Consequences (Trade-offs & Technical Debt)
* Neutral Consequences

## 7. Compliance, Verification & Implementation Plan
[Concrete steps, verification tests, and criteria to enforce compliance.]
```

---

## 4. Production-Ready Template

Engineers proposing a new architecture decision **MUST** copy and populate the following template:

```markdown
# ADR-[XXXX]: [Title of Decision]

## Metadata
* **Status:** PROPOSED
* **Deciders:** [@author_github, @reviewer_github]
* **Date:** [YYYY-MM-DD]
* **Technical Story:** [Issue #123 / PR #456]
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement
<!-- 
Describe the context and problem being faced. Provide links to relevant specs 
or codebase locations. What failure mode or scaling bottleneck necessitates this decision?
-->
[Describe context and problem statement here]

## 2. Decision Drivers (Forces)
<!-- 
What technical forces, latency thresholds, memory bounds, or platform portability 
requirements influence this decision? 
-->
1. **Force 1:** [e.g., Cross-platform process management across Linux, macOS, and Windows]
2. **Force 2:** [e.g., Deterministic zero-leak cleanup during abnormal process exit]
3. **Force 3:** [e.g., Thread-safe non-blocking standard I/O redirection]

## 3. Considered Options
* **Option 1:** [Name of Candidate 1]
* **Option 2:** [Name of Candidate 2]
* **Option 3:** [Name of Candidate 3]

## 4. Evaluation of Considered Options

### Option 1: [Name of Candidate 1]
* **Description:** [How it works, dependencies involved]
* **Good:** [Positive aspects, performance, alignment with drivers]
* **Bad:** [Negative aspects, risks, external dependencies, maintenance burden]

### Option 2: [Name of Candidate 2]
* **Description:** [How it works, dependencies involved]
* **Good:** [Positive aspects]
* **Bad:** [Negative aspects]

### Option 3: [Name of Candidate 3]
* **Description:** [How it works, dependencies involved]
* **Good:** [Positive aspects]
* **Bad:** [Negative aspects]

## 5. Decision Outcome
Chosen Option: **Option [N]: [Name of Candidate]**, because [uncompromising justification referencing decision drivers].

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* [Advantage 1]
* [Advantage 2]

### 6.2 Negative Consequences (Trade-offs)
* [Trade-off 1: Increased binary size / memory overhead / build complexity]
* [Trade-off 2: Specific platform limitation that must be managed]

### 6.3 Neutral Consequences
* [Architectural realignment or developer workflow adjustment]

## 7. Compliance, Verification & Implementation Plan
* **Implementation Target:** [`worker/daemon/task_runner.cpp`]
* **Test Verification:** Catch2 test suite `tests/unit/test_task_isolation.cpp` verifying two-stage process escalation.
* **Sanitizer Gate:** Zero memory leaks reported under ASan during runner process termination cycles.
```

---

## 5. Concrete Production Example

```markdown
# ADR-0008: Adopt reproc++ for Worker Child Process Isolation

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @sys-architect, @worker-lead, @runtime-steward
* **Date:** 2026-10-14
* **Technical Story:** Issue #412
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement
The Legatus compute daemon (`legatusd`) executes untrusted or resource-metered Solix bytecode by spawning isolated ephemeral child processes (`legatus-runner`). The daemon must guarantee that if a runner hangs, enters an infinite loop, or exhausts its instruction budget, `legatusd` can deterministically:
1. Intercept standard input, output, and error streams without pipe deadlocks.
2. Terminate the child process using a two-stage timeout escalation (`SIGTERM` -> wait 500ms -> `SIGKILL` on POSIX; `GenerateConsoleCtrlEvent` -> `TerminateProcess` on Windows).
3. Reclaim all operating system process table entries and file descriptors without leaking zombie processes.

## 2. Decision Drivers (Forces)
1. **Cross-Platform Uniformity:** Must compile and behave identically on Linux (glibc/musl), macOS (Darwin), and Windows (MSVC) without conditional `#ifdef` sprawl.
2. **Deterministic RAII Teardown:** Process and pipe handles must close deterministically under C++20 exception propagation.
3. **Zero Pipe Deadlocks:** Reading `stdout`/`stderr` concurrently must not block if the child writes large outputs before exiting.
4. **Minimal External Dependencies:** The dependency must be small, audited, vendor-friendly, and compatible with modern CMake (`FetchContent` and `vcpkg`).

## 3. Considered Options
* **Option 1:** Raw OS primitives (`fork`/`execve`/`waitpid` + POSIX pipes on Linux/macOS; `CreateProcess`/`CreatePipe` on Windows).
* **Option 2:** `boost::process` (v1 or v2).
* **Option 3:** `reproc++` (Modern C++ process library).

## 4. Evaluation of Considered Options

### Option 1: Raw OS Primitives
* **Description:** Hand-craft platform-specific process forks, pipe multiplexing with `poll()` / `select()`, and Windows Job Objects.
* **Good:** Zero external dependencies; absolute control over OS-specific flags (`CLOEXEC`, Windows inheritance).
* **Bad:** Massive cross-platform boilerplate (~1,500 lines of error-prone OS code); high risk of subtle zombie leaks and pipe deadlocks; maintenance nightmare.

### Option 2: `boost::process`
* **Description:** Leverage Boost's established process abstraction library.
* **Good:** Rich feature set; well-known interface; extensive community documentation.
* **Bad:** Introduces massive Boost dependency tree; substantially bloats build times; historical issues with hanging child pipes under Windows MSVC runtime; API differs sharply between Boost v1 and v2.

### Option 3: `reproc++`
* **Description:** A lightweight, single-focus C++11/C++20 process library designed specifically for robust subprocess lifecycle management and non-blocking stream redirection.
* **Good:** Header and small C implementation; native cross-platform support (Linux, macOS, Windows); built-in two-stage `stop` escalation (`terminate` -> wait -> `kill`); RAII wrapper types (`reproc::process`); clean CMake target.
* **Bad:** Smaller community compared to Boost; lacks asynchronous event loop integration (requires explicit polling or dedicated thread reading).

## 5. Decision Outcome
Chosen Option: **Option 3: `reproc++`**, because it fulfills all four decision drivers with minimal binary footprint, completely encapsulates Windows/POSIX process differences, and eliminates hand-rolled pipe multiplexing.

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Process escalation logic in `legatusd` reduced from 900 lines of platform-specific code to under 120 lines of clean C++20.
* Zero zombie process leaks observed under continuous burst stress tests (100,000 tasks).
* Standard output and error buffers are unmarshaled without thread deadlocks using `reproc::drain`.

### 6.2 Negative Consequences (Trade-offs)
* Synchronous draining requires a dedicated worker supervisor worker thread during task execution.
* Adds a third-party dependency managed via `vcpkg` and CMake `FetchContent`.

### 6.3 Neutral Consequences
* Developers working on `worker/runner` must familiarize themselves with `reproc::options` and `reproc::stop_actions`.

## 7. Compliance, Verification & Implementation Plan
* **Implementation:** Integrated into `worker/daemon/src/process_supervisor.cpp`.
* **Test Verification:** Unit test `tests/unit/test_process_supervisor.cpp` executes a mock hung child and verifies `reproc::stop` triggers SIGKILL within 550ms.
* **Leak Audit:** Valgrind and AddressSanitizer verify zero leaked file descriptors across 5,000 process spawn/kill iterations.
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: The "Done Deal" Post-Hoc ADR
* **Flawed Practice:** An engineer merges 4,000 lines of code swapping the database from H2 to PostgreSQL, and opens an ADR PR two weeks later titled "Adopt Postgres" requesting rubber-stamp approval.
* **Failure Mode:** Peer review is bypassed. Architectural assumptions (memory limits on embedded edge nodes) are invalidated, resulting in edge node crashes when running in memory-constrained environments.
* **Corrected Approach:** Propose the ADR *before* implementation. Team evaluates memory footprint, embedded use cases, and reaches consensus or identifies alternatives (e.g., SQLite or DuckDB).

### Anti-Pattern 2: Biased Strawman Alternatives
* **Flawed Practice:** Listing considered options as:
  1. *Option 1:* Our proposed approach (Amazing, fast, flawless).
  2. *Option 2:* Do nothing (Causes system collapse).
  3. *Option 3:* Write our own operating system from scratch (Unrealistic).
* **Failure Mode:** Destroys engineering credibility. The ADR fails to evaluate real, viable trade-offs (e.g., `libuv` vs `nng` vs raw sockets).
* **Corrected Approach:** Evaluate genuine industry-standard alternatives with honest trade-off analysis.

### Anti-Pattern 3: Omitting Negative Consequences
* **Flawed Practice:** Listing exclusively positive benefits in the outcome section, claiming "Zero downsides or risks."
* **Failure Mode:** Downstream teams discover severe production limitations only after deployment (e.g., thread starvation, licensing conflicts, binary bloat).
* **Corrected Approach:** Document every trade-off, including build dependencies, operational overhead, and memory penalties.

---

## 7. Verification & Compliance Checklist

Reviewers and documentation stewards **MUST** verify the following audit criteria before merging any ADR:

- [ ] **Sequential Numbering:** Filename follows `docs/dev/decisions/XXXX-<title>.md` matching the next available integer.
- [ ] **Directory Index Sync:** `docs/dev/decisions/README.md` is updated with the new record, title, and status.
- [ ] **Immutability Checked:** If modifying an existing ADR, verify the change is strictly a status update (`SUPERSEDED`) or typo fix, not an intent rewrite.
- [ ] **Three Realistic Options:** At least three viable candidates are evaluated in detail with pros and cons.
- [ ] **Explicit Trade-offs:** The Negative Consequences section contains concrete operational or architectural costs.
- [ ] **Pre-Implementation Timing:** The ADR is reviewed and accepted prior to merging corresponding implementation PRs.
- [ ] **Verification Plan:** Concrete test targets and sanitizer gates are specified for downstream compliance.
