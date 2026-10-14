# Conventional Commits & Release Please Specification

## Document Metadata
* **Guideline ID:** GL-0001
* **Scope:** Universal / Cluster-Wide (`legatusd`, `server`, `studio`, `solix`, `ci`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Release Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

The Legatus platform operates as a multi-component distributed system where disparate binaries (`legatusd`, `legatus`, `legatus-runner`, `server`, `studio`) communicate across strict network and IPC boundaries. The integrity of automated release management, software distribution, and semantic versioning directly depends upon deterministic, machine-parseable Git commit histories.

This specification mandates adherence to the **Conventional Commits v1.0.0** standard, coupled with **Google's Release Please** automated release pipeline. By formalizing commit grammar, the engineering team guarantees:
1. Automated semantic version bumps (Major, Minor, Patch) according to **SemVer 2.0.0**.
2. Automated, immutable generation and maintenance of `CHANGELOG.md` across component subtrees.
3. Automated drafting and publication of tagged GitHub Releases and binary release artifacts.
4. Complete elimination of ambiguous, unsearchable, or monolithic commits that obfuscate regressions.

Any commit that violates this grammar halts the automated integration pipeline and **MUST NOT** be merged into the protected trunk branch (`master`).

---

## 2. Normative Invariants & Core Principles

All contributors **MUST** comply with the following normative rules:

1. **Strict Conventional Commit Formatting:** Every commit subject line **MUST** match the structure `<type>(<scope>): <description>`.
2. **Deterministic Version Bumping:**
   * Commits of type `fix` or `perf` **MUST** trigger a **Patch** version bump ($x.y.Z \to x.y.Z+1$).
   * Commits of type `feat` **MUST** trigger a **Minor** version bump ($x.Y.z \to x.Y+1.0$).
   * Any commit introducing a breaking contract change **MUST** trigger a **Major** version bump ($X.y.z \to X+1.0.0$).
3. **Mandatory Breaking Change Syntax:** A breaking change **MUST** be signaled by appending an exclamation mark (`!`) immediately prior to the colon in the header (e.g., `feat(worker)!: ...`) **AND** including a `BREAKING CHANGE:` footer block in the commit body. Omitting either token when altering wire schemas or API contracts is strictly prohibited.
4. **Canonical Lowercase Scopes:** Scopes **MUST** be selected exclusively from the registered manifest of canonical lowercase scopes. Custom or ad-hoc scopes **MUST NOT** be used without an approved update to this guideline.
5. **Imperative Mood & Punctuation Constraints:** The commit description **MUST** be written in the imperative, present tense (e.g., "add", "fix", "migrate", not "added", "fixes", "migrating"). The description **MUST NOT** begin with a capital letter and **MUST NOT** end with a trailing period.
6. **Atomic Concern Principle:** Each commit **MUST** encapsulate a single atomic change. Combining unrelated modifications (e.g., fixing a daemon IPC bug while refactoring Java server database queries) within one commit **MUST NOT** occur.
7. **Body Line Length:** Commit body lines **MUST** wrap at **72 characters** to guarantee readability across Git terminal pagers (`git log`) and release interfaces.

---

## 3. Structural Specification & Schema

### 3.1 Backus-Naur Form (BNF) Grammar

```bnf
<commit-message> ::= <header> <newline> <newline> <body> [<newline> <newline> <footer>]
<header>         ::= <type> ["(" <scope> ")"] ["!"] ":" <space> <description>
<type>           ::= "feat" | "fix" | "perf" | "docs" | "refactor" | "style" | "test" | "chore" | "build" | "ci"
<scope>          ::= "worker" | "server" | "studio" | "wire" | "cli" | "runner" | "crypto" | "deps" | "docs"
<description>    ::= <lowercase-letter> <any-character-except-period>*
<body>           ::= <wrapped-text-72-cols>
<footer>         ::= <footer-element> (<newline> <footer-element>)*
<footer-element> ::= <breaking-change> | <issue-ref>
<breaking-change>::= "BREAKING CHANGE:" <space> <explanation>
<issue-ref>      ::= ("Refs:" | "Fixes:" | "Closes:") <space> "#" <issue-number>
```

### 3.2 Commit Type Semantics & Release Please Triggers

| Commit Type | SemVer Impact | Release Please Action | Purpose and Guidance |
| :--- | :--- | :--- | :--- |
| `feat` | **Minor** ($0.X.0$) | Appends entry to `### Features` in `CHANGELOG.md` | Introduces new customer-facing or protocol capabilities. |
| `fix` | **Patch** ($0.0.X$) | Appends entry to `### Bug Fixes` in `CHANGELOG.md` | Resolves a software defect or runtime anomaly. |
| `perf` | **Patch** ($0.0.X$) | Appends entry to `### Performance` in `CHANGELOG.md` | Improves memory consumption, CPU cycles, or latency. |
| `docs` | None ($0.0.0$) | Excluded from release notes by default | Documentation updates, guideline modifications, spec edits. |
| `refactor` | None ($0.0.0$) | Excluded from release notes by default | Code restructuring without functional or contract changes. |
| `test` | None ($0.0.0$) | Excluded from release notes by default | Addition, refactoring, or correction of unit/integration tests. |
| `style` | None ($0.0.0$) | Excluded from release notes by default | Code formatting, whitespace, clang-format, Checkstyle fixes. |
| `build` | None ($0.0.0$) | Excluded from release notes by default | CMake scripts, Maven POM dependencies, compiler flags. |
| `ci` | None ($0.0.0$) | Excluded from release notes by default | GitHub Actions workflows, container builds, CI lint rules. |
| `chore` | None ($0.0.0$) | Excluded from release notes by default | Repository maintenance, release-please syncs, metadata. |

### 3.3 Registered Component Scopes

| Scope Identifier | Target Component / Binary | Directory Boundaries |
| :--- | :--- | :--- |
| `(worker)` | `legatusd` compute supervisor daemon | `worker/daemon/`, `worker/include/` |
| `(runner)` | `legatus-runner` sandboxed execution engine | `worker/runner/`, `worker/solix_adapter/` |
| `(cli)` | `legatus` administrative operator CLI | `worker/cli/` |
| `(server)` | `legatus-server` Spring Boot orchestration broker | `server/src/`, `server/pom.xml` |
| `(studio)` | `legatus-studio` JavaFX administrative cockpit | `studio/src/`, `studio/pom.xml` |
| `(wire)` | Layer 7 protocols, LCTP, network frames | `docs/specs/protocols/`, `shared/wire/` |
| `(crypto)` | Cryptographic routines, Argon2id, Ed25519, TLS | `worker/crypto/`, `server/security/` |
| `(deps)` | External package and dependency updates | `vcpkg.json`, `pom.xml`, submodules |
| `(docs)` | Guidelines, subsystem specs, ADRs, user guides | `docs/` |

---

## 4. Production-Ready Template

Engineers **MUST** structure non-trivial commits using the following production template:

```gitcommit
<type>(<scope>): <imperative description starting lowercase without period>

[Optional body: Detailed technical context explaining the problem, the root
cause, the selected architecture, and the implementation rationale. Lines
MUST wrap at 72 characters.]

[Optional additional paragraphs separated by double newlines, providing
benchmarks, memory measurements, or protocol state machine details.]

[Optional Breaking Change Footer - MANDATORY if type contains '!']:
BREAKING CHANGE: <Detailed description of the breaking change, migration
path, affected wire frames, and deprecated APIs.>

[Optional Issue References]:
Fixes: #<issue-number>
Refs: #<issue-number>
```

---

## 5. Concrete Production Example

### Example 1: Breaking Protocol Change (Major Version Bump)

```gitcommit
feat(wire)!: transition heartbeat frame to millisecond monotonic epoch

Replace system clock wall-time seconds with uint64 monotonic milliseconds
in LCTP HEARTBEAT frames to prevent drift during NTP step-adjustments on
bare-metal compute nodes.

BREAKING CHANGE: The 'timestamp' field in 'HEARTBEAT' and 'HEARTBEAT_ACK'
frames is now formatted as an unsigned 64-bit integer representing
monotonic milliseconds since connection establishment. Brokers running
v1.x wire parsers expecting ISO-8601 strings will fail deserialization.

Fixes: #312
Refs: #298
```

### Example 2: Critical Memory Leak Fix in Worker Daemon (Patch Bump)

```gitcommit
fix(worker): release reproc child process pipes upon timeout escalation

Ensure reproc::process stream handles are explicitly closed during SIGKILL
escalation in worker task supervisor. Previously, timed-out tasks that
resisted SIGTERM left open file descriptors in legatusd, exhausting the
process table under high-density burst workloads.

Closes: #481
```

### Example 3: Performance Improvement in Guest Runner (Patch Bump)

```gitcommit
perf(runner): eliminate heap allocation during Solix opcode dispatch

Pre-allocate the instruction stack frame inside the runner memory arena
using std::span over fixed virtual pages. Yields a 14% latency reduction
for micro-tasks executing fewer than 10,000 instructions.

Refs: #502
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Counter-Example 1: Ambiguous & Colloquial Descriptions
```text
# FLAWED:
git commit -m "fix bug"
git commit -m "WIP on worker stuff"
git commit -m "updated database logic"
```
* **Failure Mode:** Completely unparseable by `release-please`. The generated changelog contains empty or useless entries. Git archaeology (`git bisect`) becomes impossible when diagnosing cluster crashes.
* **Corrected Form:**
```text
# CORRECT:
git commit -m "fix(worker): catch std::bad_alloc during task frame unmarshaling"
git commit -m "feat(server): add optimistic locking to task allocation entity"
```

### Counter-Example 2: Capitalization & Trailing Punctuation
```text
# FLAWED:
git commit -m "feat(runner): Implemented Solix memory arena bounds checking."
```
* **Failure Mode:** Violates strict regex linting in CI (`commitlint`). The capitalization and trailing dot break changelog formatting consistency.
* **Corrected Form:**
```text
# CORRECT:
git commit -m "feat(runner): implement Solix memory arena bounds checking"
```

### Counter-Example 3: Silent Breaking Changes
```text
# FLAWED:
git commit -m "refactor(wire): rename worker_id to node_uuid in SESSION_INIT"
```
* **Failure Mode:** `release-please` categorizes `refactor` as a non-releasing commit. A new minor or patch version is released without bumping the Major version. When deployed, existing `legatusd` daemons fail to register with `server`, causing a cluster-wide service blackout.
* **Corrected Form:**
```text
# CORRECT:
feat(wire)!: rename worker_id to node_uuid in SESSION_INIT

BREAKING CHANGE: Payload field 'worker_id' in SESSION_INIT has been renamed
to 'node_uuid'. Compute nodes running legacy daemons cannot register.
```

### Counter-Example 4: Monolithic Multi-Component Commits
```text
# FLAWED:
git commit -m "feat: add argon2id auth, update studio ui and fix runner segfault"
```
* **Failure Mode:** Binds unrelated subsystems across C++, Java, and JavaFX together. Reverting a regression in the runner segfault fix necessitates reverting the security authentication mechanism and UI adjustments.
* **Corrected Form:** Split into three discrete, atomic commits:
  1. `feat(crypto): implement Argon2id password hashing for node tokens`
  2. `feat(studio): add node token configuration dialog to settings view`
  3. `fix(runner): guard null pointer dereference during Solix stack teardown`

---

## 7. Verification & Compliance Checklist

All engineers and automated PR gates **MUST** enforce the following criteria prior to merge:

- [ ] **Commitlint Gate:** Commit passes automated `commitlint` evaluation against `.commitlintrc.json`.
- [ ] **Type Legitimacy:** The commit type is one of: `feat`, `fix`, `perf`, `docs`, `refactor`, `style`, `test`, `chore`, `build`, `ci`.
- [ ] **Scope Legitimacy:** The scope is lowercase and registered in Section 3.3 (`worker`, `server`, `studio`, `wire`, `cli`, `runner`, `crypto`, `deps`, `docs`).
- [ ] **Imperative Mood:** Subject starts lowercase, uses imperative mood ("add", not "added"), and omits trailing punctuation.
- [ ] **Breaking Change Conformance:** If breaking, both `!` in header and `BREAKING CHANGE:` in footer are present.
- [ ] **Line Wrapping:** All lines in the commit body and footer wrap at 72 characters or fewer.
- [ ] **Atomic Integrity:** Commit does not intermingle distinct concerns across multiple subsystems.
