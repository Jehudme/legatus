# Milestone Planning & Delivery Specification

## Document Metadata
* **Guideline ID:** GL-0008
* **Scope:** Engineering Management & Phased Delivery (`docs/dev/milestones/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Engineering Leads, Release Managers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

In complex distributed platforms comprising multiple binaries, high-speed IPC, and low-level runtime engines, uncoordinated development produces integration gridlock, premature releases, and critical security oversights. Without disciplined milestone planning, features are declared "done" when the code is barely written, leaving tests, documentation, sanitizers, and operational observability incomplete.

This specification formalizes the authoring, priority taxonomy, execution lifecycle, and closure criteria for all engineering milestones within the Legatus ecosystem (`docs/dev/milestones/`). It establishes an unambiguous **Definition of Done (DoD)** and an enforceable **Priority Blocker Taxonomy (P0-P3)**. Every milestone file checked into Git serves as an authoritative, auditable delivery contract governing cluster releases.

---

## 2. Normative Invariants & Core Principles

All milestone plans and contributors **MUST** comply with the following binding invariants:

1. **Version-Controlled Milestone Plans:** All engineering milestones **MUST** be defined as Git-tracked Markdown files located in `docs/dev/milestones/` using three-digit zero-padded prefixes (e.g., `003-worker-mvp-implementation.md`). Tracking milestones exclusively in external, transient tools (Jira, Trello) without Git backing is strictly prohibited.
2. **Deterministic Blocker Classification:** Every work item, bug, or feature within a milestone **MUST** be tagged with an explicit priority level: `P0`, `P1`, `P2`, or `P3`. Unprioritized tasks **SHALL NOT** be merged into milestone plans.
3. **The P0/P1 Zero-Tolerance Rule:** A milestone **MUST NOT** transition to `CLOSED` or be tagged for release if a single `P0` or `P1` task remains unresolved or incomplete. Unresolved `P2` or `P3` tasks **MAY** be formally deferred to a subsequent milestone by explicit maintainer consensus.
4. **Non-Negotiable Definition of Done (DoD):** A task or milestone cannot be marked complete until all five DoD pillars are satisfied:
   * Functional code compiled and merged into `master`.
   * 100% automated test pass across Catch2 v3 and JUnit 5 test suites.
   * Zero memory leaks or undefined behavior warnings under AddressSanitizer (ASan) and UndefinedBehaviorSanitizer (UBSan).
   * Living documentation, CLI flag tables, and wire specs updated in accordance with GL-0010.
   * Architecture Decision Records (ADRs) authored and accepted if architectural choices occurred (GL-0006).
5. **Milestone State Progression:** Milestones **MUST** strictly follow the five-stage finite state machine (`DRAFT` $\to$ `ACTIVE` $\to$ `FEATURE_COMPLETE` $\to$ `VALIDATED` $\to$ `CLOSED`). Regressions discovered during validation **MUST** transition the milestone back to `ACTIVE`.

---

## 3. Structural Specification & Schema

### 3.1 Blocker Priority Taxonomy

| Priority Tier | Classification | Definition & Criteria | Release Impact | SLA / Gate |
| :--- | :--- | :--- | :--- | :--- |
| **`P0`** | **Critical Blocker** | Halts release, breaks cluster safety, causes memory corruption, introduces segfaults, exposes security vulnerabilities, or breaks CI compilation. | **Blocks all releases immediately.** Cannot deploy to any environment. | Must be fixed within 24h; all other work stops. |
| **`P1`** | **High Priority** | Core milestone deliverable, essential protocol capability, data persistence guarantee, or mandatory cross-platform support. | **Blocks milestone completion.** Release cannot cut. | Must be resolved prior to `FEATURE_COMPLETE`. |
| **`P2`** | **Medium Priority** | Ergonomics, non-blocking telemetry polish, performance optimizations, developer tooling improvements. | Non-blocking. Can release if maintainers approve deferral. | Scheduled for current milestone or pushed to next. |
| **`P3`** | **Low Priority** | Minor documentation polish, non-critical refactoring, cosmetic CLI color adjustments. | Non-blocking. Deferrable. | Backlog or opportunistic cleanup. |

### 3.2 Milestone Finite State Machine (FSM)

```
    ┌──────────┐
    │  DRAFT   │
    └────┬─────┘
         │ (Milestone approved by lead architect)
         ▼
    ┌──────────┐
    │  ACTIVE  │ ◄─────────────────────────────────────────┐
    └────┬─────┘                                           │
         │ (All P0 and P1 implementation PRs merged)       │ (Regression / ASan leak
         ▼                                                 │  found in soak tests)
    ┌──────────────────┐                                   │
    │ FEATURE_COMPLETE │ ──────────────────────────────────┘
    └────┬─────────────┘
         │ (DoD verified: tests green, ASan/UBSan clean, docs synced)
         ▼
    ┌───────────┐
    │ VALIDATED │
    └────┬──────┘
         │ (Release tag cut by Release Please, PR closed)
         ▼
    ┌──────────┐
    │  CLOSED  │
    └──────────┘
```

1. **`DRAFT`**: Scope and deliverables are being drafted. Not yet approved for implementation.
2. **`ACTIVE`**: Development in progress. PRs actively being merged to `master`.
3. **`FEATURE_COMPLETE`**: All scoped `P0` and `P1` functionality is merged. Feature freeze in effect; only bug fixes, tests, and documentation permitted.
4. **`VALIDATED`**: Full multi-platform CI matrix passes; 72-hour continuous cluster soak tests show zero leaks; all documentation verified.
5. **`CLOSED`**: Milestone successfully delivered and officially retired.

---

## 4. Production-Ready Template

Engineers authoring milestone files (`docs/dev/milestones/XXX-[name].md`) **MUST** use the following canonical template:

```markdown
# Milestone [XXX]: [Milestone Title]

## Document Metadata
* **Milestone ID:** MS-[XXX]
* **Target Version:** `v[X.Y.Z]`
* **Status:** [DRAFT | ACTIVE | FEATURE_COMPLETE | VALIDATED | CLOSED]
* **Lead Architect:** [@github_username]
* **Target Delivery Date:** [YYYY-MM-DD]
* **Related ADRs:** [ADR-XXXX, ADR-YYYY]

---

## 1. Executive Summary & Objectives
<!-- High-level statement of the milestone's primary mission and delivered capabilities. -->
[Provide milestone summary and objectives here]

## 2. Scope & Subsystems Affected
* `worker/daemon` (`legatusd`): [Scope description]
* `worker/runner` (`legatus-runner`): [Scope description]
* `server` (`legatus-server`): [Scope description]
* `studio` (`legatus-studio`): [Scope description]
* `protocols` (Wire/LCTP): [Scope description]

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [ ] `[P0]` **[Task Title]:** [Detailed task description and PR/Issue link]
- [ ] `[P0]` **[Task Title]:** [Detailed task description and PR/Issue link]

### 3.2 P1: High Priority (Milestone Core)
- [ ] `[P1]` **[Task Title]:** [Detailed task description and PR/Issue link]
- [ ] `[P1]` **[Task Title]:** [Detailed task description and PR/Issue link]

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [ ] `[P2]` **[Task Title]:** [Detailed task description and PR/Issue link]
- [ ] `[P2]` **[Task Title]:** [Detailed task description and PR/Issue link]

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [ ] `[P3]` **[Task Title]:** [Detailed task description and PR/Issue link]

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | 100% pass across all worker test fixtures | `ctest --output-on-failure` | [ ] Pending / Verified |
| **JUnit 5 Suite** | 100% pass across Spring Boot orchestrator | `mvn test` | [ ] Pending / Verified |
| **Sanitizer Gate** | 0 memory leaks, 0 UB errors | ASan / UBSan on Clang 18 | [ ] Pending / Verified |
| **Documentation** | Subsystem specs & guides updated to GL-0010 | `scripts/verify-cli-docs.sh`| [ ] Pending / Verified |
| **ADR Governance** | All architectural decisions recorded to GL-0006 | Review against ADR manifest | [ ] Pending / Verified |

## 5. Risk Assessment & Mitigation Strategies
* **Risk 1:** [Description of architectural or concurrency risk]
  * *Mitigation:* [Concrete engineering mitigation]
* **Risk 2:** [Description of cross-platform compatibility risk]
  * *Mitigation:* [Concrete engineering mitigation]
```

---

## 5. Concrete Production Example

```markdown
# Milestone 003: Worker Daemon & Task Runner MVP Implementation

## Document Metadata
* **Milestone ID:** MS-003
* **Target Version:** `v1.0.0-worker-mvp`
* **Status:** ACTIVE
* **Lead Architect:** @worker-lead
* **Target Delivery Date:** 2026-11-15
* **Related ADRs:** ADR-0005, ADR-0006, ADR-0008

---

## 1. Executive Summary & Objectives
Deliver the production-grade worker daemon (`legatusd`), administrative command-line utility (`legatus`), and sandboxed task runner (`legatus-runner`). The deliverable provides outbound TLS 1.3 WebSocket connectivity to `legatus-server`, local IPC process control via `nng`, and child process isolation via `reproc++` with deterministic instruction budget enforcement.

## 2. Scope & Subsystems Affected
* `worker/daemon`: Outbound WebSocket, LCTP handshake, heartbeat scheduler, local IPC REP0 server.
* `worker/runner`: Ephemeral process launcher, Solix memory arena bounds, stdio drain pipes.
* `worker/cli`: CLI11 parsing for `up`, `down`, `drain`, `status`.
* `docs/specs`: `worker-spec.md`, `protocols/worker-wire.md`.

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **reproc++ Escalation:** Implement two-stage child process termination (`SIGTERM` -> 500ms -> `SIGKILL`) preventing zombie process leaks. (PR #142)
- [ ] `[P0]` **Memory Arena Zeroing:** Enforce `explicit_bzero` on Solix guest memory stack frames upon task completion. (PR #158)
- [ ] `[P0]` **Heartbeat Dead-Man Switch:** Auto-terminate worker task dispatch if `HEARTBEAT_ACK` is missed for 3 consecutive intervals (15,000ms).

### 3.2 P1: High Priority (Milestone Core)
- [x] `[P1]` **LCTP SESSION_INIT:** Construct and transmit hardware capability manifest on socket upgrade. (PR #110)
- [ ] `[P1]` **Local IPC via nng:** Implement `NNG_PROTO_REP0` in `legatusd` and `NNG_PROTO_REQ0` in `legatus` CLI. (PR #134)
- [ ] `[P1]` **Cross-Platform Path Resolution:** Dynamic configuration lookup for Linux, macOS, and Windows without hardcoded `/tmp` paths.

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [ ] `[P2]` **CLI Output Formatting:** Format `legatus status` with clean ANSI terminal colors and ASCII tables.
- [ ] `[P2]` **Rotating File Sinks:** Configure `spdlog` to rotate daemon logs at 50MB with 5 historical backups.

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [ ] `[P3]` **CMake Build Time Tuning:** Optimize precompiled header inclusions for Catch2 test suites.

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | 42 unit & integration test cases green | `ctest --output-on-failure` | [ ] Pending |
| **Sanitizer Gate** | Zero leaks across 10,000 task spawn tests | Clang 18 `-fsanitize=address,undefined` | [ ] Pending |
| **Documentation** | `worker-spec.md` and `05-worker.md` synced | GL-0010 audit checklist | [ ] Pending |
| **ADR Governance** | ADR-0008 approved for `reproc++` adoption | Verified in `docs/dev/decisions/` | [x] Verified |
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Premature Closure with Unresolved Blockers
* **Flawed Practice:** Moving a milestone to `CLOSED` while a `P0` memory corruption issue remains open, labeling it: "Will patch in hotfix."
* **Failure Mode:** Deploying known memory corruptions to production compute nodes causes cascading worker crashes across the cluster, exhausting host memory and triggering Linux OOM killers.
* **Corrected Approach:** A milestone **MUST NOT** close with unresolved P0 or P1 items. Either delay the release or down-scope features through a formal maintainer vote.

### Anti-Pattern 2: The "Ghost" Milestone
* **Flawed Practice:** Maintaining milestone targets and progress solely in an external, private spreadsheet or proprietary ticket tracker.
* **Failure Mode:** Open-source contributors and distributed team members have zero visibility into upcoming breaking changes or delivery deadlines. Architectural alignment diverges.
* **Corrected Approach:** Commit and maintain all milestone documents in `docs/dev/milestones/` directly in the repository.

### Anti-Pattern 3: Incomplete Definition of Done
* **Flawed Practice:** Declaring a milestone complete because "the feature compiles and runs on my local Linux laptop," skipping Windows CI runs, sanitizer gates, and documentation updates.
* **Failure Mode:** Windows compute nodes fail to compile due to missing MSVC headers; background threads leak memory; operators have no documentation explaining how to start the service.
* **Corrected Approach:** Enforce the full 5-pillar DoD matrix before any milestone status transition.

---

## 7. Verification & Compliance Checklist

Milestone leads and release managers **MUST** verify the following checklist before modifying milestone states:

- [ ] **Sequential Numbering:** Milestone file path matches `docs/dev/milestones/XXX-[name].md`.
- [ ] **Explicit Priorities:** Every listed task is explicitly tagged with `[P0]`, `[P1]`, `[P2]`, or `[P3]`.
- [ ] **Zero Open P0/P1s on Close:** No P0 or P1 tasks remain incomplete before transitioning to `CLOSED`.
- [ ] **DoD Verification:** Automated test results, ASan/UBSan clean logs, and documentation links are attached to the milestone sign-off.
- [ ] **Master Index Updated:** `docs/dev/milestones/README.md` reflects the current milestone state and target dates.
