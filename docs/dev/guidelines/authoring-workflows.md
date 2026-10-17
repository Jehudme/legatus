# Engineering Lifecycle & Gate Checks Specification

## Document Metadata
* **Guideline ID:** GL-0003
* **Scope:** Software Development Lifecycle & CI Gates (`.github/workflows/`, repo-wide)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Maintainers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

In a distributed runtime coordinating native C++20 worker processes with a Java 21 orchestration cluster across Linux, macOS, and Windows, informal or haphazard development workflows guarantee regressions. Code merged without architectural documentation, untested across operating systems, or lacking local sanitizer validation degrades cluster reliability and increases technical debt.

This specification establishes the mandatory **Eight-Stage Engineering Lifecycle** and formal gate checks governing all code and documentation contributions to Legatus. It defines the exact sequential progression from branch inception to non-fast-forward merge, codifies self-review requirements, and establishes automated CI status checks. Adherence to this lifecycle ensures that all modifications entering `master` are architecturally sound, thoroughly tested, zero-leak validated, and synchronized with living documentation.

---

## 2. Normative Invariants & Core Principles

All contributors **MUST** strictly adhere to the following lifecycle invariants:

2. **The Nine-Stage Sequence:** Engineering work **MUST** proceed through the nine discrete stages in exact chronological order:
   $$\text{Branch} \to \text{Spec Update} \to \text{Implementation} \to \text{Test Matrix} \to \text{Local Verification} \to \text{Doc Sync} \to \text{CI Matrix} \to \text{Merge} \to \text{Release Publish}$$
3. **Specification-First Principle:** Prior to writing production code, the engineer **MUST** document the architectural changes, wire frames, or schema alterations in `docs/specs/` or an ADR (`docs/dev/decisions/`). Implementing code without prior or concurrent specification updates is prohibited.
4. **Mandatory Multi-Platform Green CI:** A pull request **SHALL NOT** be merged unless the complete GitHub Actions CI matrix passes cleanly across all tier-1 operating systems:
   * **Linux:** Ubuntu 22.04 LTS (GCC 13, Clang 18 with ASan/UBSan)
   * **macOS:** macOS 14 Sonoma (AppleClang 15)
   * **Windows:** Windows Server 2022 (MSVC v143 / Visual Studio 2022)
5. **Non-Fast-Forward Merge Invariant:** All pull requests merged into `master` **MUST** use non-fast-forward merge commits (`git merge --no-ff`) to preserve branch topology and maintain atomic milestone histories. Rebasing and squashing at merge time that destroys discrete commit boundaries is strictly prohibited unless explicitly authorized for trivial chore branches.
6. **Mandatory Two-Reviewer Approval:** PRs modifying core daemons (`legatusd`), process runners (`legatus-runner`), or wire protocols (`worker-wire.md`) **MUST** receive approval from at least two core maintainers.
7. **No CI Bypassing:** Administrators **MUST NOT** use bypass permissions to force-merge PRs with failing checks or uncleared review comments.
8. **Automated Monorepo Release Invariant:** Releases **SHALL** be cut strictly via Google `release-please` by merging the automated Release PR into `master`. Manual tagging or manual editing of component changelogs without Conventional Commits is prohibited.

---

## 3. Structural Specification & Schema

### 3.1 The Nine-Stage Engineering Lifecycle

```
[1. BRANCH]            Create feat/<scope>-<desc> from master
     │
     ▼
[2. SPEC FIRST]        Update docs/specs/ or create ADR in docs/dev/decisions/
     │
     ▼
[3. IMPLEMENT]         Write polyglot code adhering to GL-0011 standards
     │
     ▼
[4. TEST MATRIX]       Author test scenarios covering positive and negative cases
     │
     ▼
[5. LOCAL VERIFY]      Execute ctest / mvn test under ASan and UBSan
     │
     ▼
[6. DOC SYNC]          Update CLI help, user guides (GL-0010 zero-drift)
     │
     ▼
[7. CI MATRIX]         Validate multi-platform matrix (Linux, macOS, Windows)
     │
     ▼
[8. MERGE (--no-ff)]   Merge into master preserving commit graph, delete branch
     │
     ▼
[9. RELEASE PUBLISH]   Merge automated Release PR; tag cut & release assets built
```

### 3.2 Automated CI Matrix Gates

| Gate Name | Platform / Environment | Tooling & Parameters | Blocking? |
| :--- | :--- | :--- | :--- |
| **Commitlint** | Linux (Node.js) | Conventional Commits v1.0.0 rules | **YES (P0)** |
| **C++20 Build & Test** | Ubuntu 22.04 (GCC 13) | `cmake -B build -S . && cmake --build build && ctest` | **YES (P0)** |
| **Sanitizers (ASan/UBSan)**| Ubuntu 22.04 (Clang 18) | `-fsanitize=address,undefined -fno-omit-frame-pointer` | **YES (P0)** |
| **Java 21 Build & Test** | Ubuntu 22.04 (Temurin 21) | `mvn clean verify -Pprod` | **YES (P0)** |
| **macOS Native Build** | macOS 14 (AppleClang) | `cmake --build build && ctest` | **YES (P0)** |
| **Windows Native Build** | Win Server 2022 (MSVC) | `cmake -G "Visual Studio 17 2022" && ctest` | **YES (P0)** |
| **Documentation Check** | Linux | `scripts/verify-cli-docs.sh` & Markdownlint | **YES (P1)** |

---

## 4. Production-Ready Template

Engineers opening pull requests **MUST** populate the following Pull Request description template:

```markdown
<!-- PR Title MUST follow Conventional Commits: <type>(<scope>): <description> -->

## Description & Technical Context
<!-- 
Provide a clear, high-level summary of the architectural problem and the solution.
Reference relevant issue numbers and related ADRs.
-->
Resolves: #<issue-number>
Related ADR: ADR-<XXXX>

## Stage Verification Checklist
- [ ] **Stage 2 (Spec):** Specifications updated in `docs/specs/` or ADR accepted.
- [ ] **Stage 3 (Implementation):** Follows GL-0011 polyglot conventions (C++20/Java 21/Solix).
- [ ] **Stage 4 (Test Matrix):** Dual test coverage (positive and negative cases) added.
- [ ] **Stage 5 (Local Verify):** Passed `ctest` with zero memory leaks under ASan/UBSan.
- [ ] **Stage 6 (Doc Sync):** Updated CLI help text, flags, and `docs/guides/`.

## Subsystem Impact Matrix
* [ ] `worker/daemon` (`legatusd`)
* [ ] `worker/runner` (`legatus-runner`)
* [ ] `worker/cli` (`legatus`)
* [ ] `server` (`legatus-server`)
* [ ] `studio` (`legatus-studio`)
* [ ] `protocols` (Wire/LCTP)

## Breaking Change Notice
<!-- If breaking, detail the migration path and wire impacts. -->
* **Breaking Change:** [YES / NO]
* **SemVer Impact:** [Patch / Minor / Major]
* **Migration Notes:** [Describe backward compatibility or state migration]

## Local Verification Output
```bash
# Paste summary output from ctest or ASan run
ctest --test-dir build --output-on-failure
```
```

---

## 5. Concrete Production Example

### End-to-End Walkthrough: Implementing Task Abort in `legatusd`

#### Step 1: Branch Creation
```bash
git checkout master
git pull --rebase origin master
git checkout -b feat/worker-task-abort
```

#### Step 2: Spec First Update
The developer edits `docs/specs/protocols/worker-wire.md` to add the `ABORT_TASK` frame definition and updates the task supervisor state diagram in `docs/specs/worker-spec.md`.

#### Step 3: Implementation
The developer updates `worker/daemon/src/task_supervisor.cpp`, wrapping the child runner process in a cancelation listener that invokes `reproc::stop` with the two-stage escalation.

#### Step 4: Test Matrix Authoring
The developer adds `tests/unit/test_task_abort.cpp` containing:
1. *Positive:* Active child receives SIGTERM, exits cleanly within 200ms, transmits `TASK_RESULT` with status `ABORTED`.
2. *Negative:* Misbehaving child ignores SIGTERM; supervisor escalates to SIGKILL at 500ms and cleans up process handles without leaks.

#### Step 5: Local Verification with Sanitizers
```bash
cmake -B build-asan -S . -DCMAKE_CXX_COMPILER=clang++ -DCMAKE_CXX_FLAGS="-fsanitize=address,undefined"
cmake --build build-asan -j$(nproc)
ctest --test-dir build-asan --output-on-failure
```
*Result: 100% tests passed, 0 memory leaks reported.*

#### Step 6: Documentation Synchronization
The developer updates `docs/guides/05-worker.md` detailing the new `legatus abort <task-id>` CLI command.

#### Step 7: Push and CI Green Check
```bash
git add .
git commit -m "feat(worker): implement task abort handling with two-stage kill escalation"
git push origin feat/worker-task-abort
```
*GitHub Actions executes the multi-platform CI matrix across Linux, macOS, and Windows. All checks pass.*

#### Step 8: Non-Fast-Forward Merge
```bash
# After peer review approval:
git checkout master
git merge --no-ff feat/worker-task-abort -m "Merge branch 'feat/worker-task-abort' into master"
git push origin master
git branch -d feat/worker-task-abort
```

#### Step 9: Automated Release Staging & Publishing
Upon pushing the merged commit to `master`:
1. GitHub Actions workflow `.github/workflows/pipeline-publish.yml` executes.
2. The `release-please` job scans the commit history since the previous tag.
3. Because the commit was prefixed with `feat(worker):`, `release-please` updates the open Release PR (`chore(master): release worker X.Y.0`), bumping `worker` in `.release-please-manifest.json` and appending the changelog entry to `worker/CHANGELOG.md`.
4. When maintainers merge the Release PR, Git tags are minted, GitHub Releases are published, and release artifacts (installers, JARs) are built and attached.

---

## 6. Automated Monorepo Release Standard Operating Procedure (SOP)

Per **ADR-0003**, Legatus utilizes Google `release-please` to maintain independent, deterministic SemVer releases for each monorepo package:
* `worker`: C++20 daemon, runner, and CLI (`worker/CHANGELOG.md`)
* `server`: Java 21 / Spring Boot orchestration server (`server/CHANGELOG.md`)
* `studio`: JavaFX desktop administrative cockpit (`studio/CHANGELOG.md`)

### 6.1 The 4-Step Release Playbook

Engineers and release maintainers **MUST** execute the following workflow to publish new versions:

```
[1. Commit Code] ──────► [2. Bot Maintains PR] ──────► [3. Review & Merge] ──────► [4. Automatic Publish]
 conventional commits     chore(master): release...     merge commit (--no-ff)       tags & release assets
```

1. **Step 1: Commit with Conventional Commits**
   Ensure all merged commits use canonical types (`feat`, `fix`, `perf`) and scopes (`(worker)`, `(server)`, `(studio)`).
   * `fix(...)`: Triggers a **PATCH** release (e.g., `0.3.0` $\to$ `0.3.1`).
   * `feat(...)`: Triggers a **MINOR** release (e.g., `0.3.0` $\to$ `0.4.0`).
   * `feat!(...)` or `BREAKING CHANGE:`: Triggers a **MAJOR** release (e.g., `0.3.0` $\to$ `1.0.0`).

2. **Step 2: Inspect the Automated Release PR**
   Navigate to the repository's **Pull Requests** tab on GitHub.
   Locate the open PR authored by `github-actions[bot]`, typically titled:
   `chore(master): release server X.Y.Z, worker X.Y.Z`
   * Review the version numbers bumped in `.release-please-manifest.json`.
   * Review the auto-generated release notes in the respective `CHANGELOG.md` files.

3. **Step 3: Merge the Release PR**
   When the milestone is marked `FEATURE_COMPLETE` and `VALIDATED`:
   * Click **Merge pull request** using a standard merge commit (`--no-ff`).
   * **Do NOT squash** the Release PR, as squashing destroys the manifest commit metadata.

4. **Step 4: Automated Tagging and Artifact Compilation**
   The merge commit triggers `.github/workflows/pipeline-publish.yml`:
   * `release-please` cuts the official Git tags (e.g., `server-v1.0.0`, `worker-v1.0.0`, `studio-v1.0.0`).
   * GitHub Releases are published with the changelog.
   * Downstream jobs (`publish-worker`, `publish-server`, `publish-studio`) compile release binaries, assemble native installers/JARs, and upload them to the GitHub Release.

### 6.2 Hotfixes & Manual Version Overrides

In exceptional circumstances where a version bump must be manually defined (e.g., promotional Major version `v1.0.0` milestone alignment):
1. Update `.release-please-manifest.json` directly with the intended SemVer:
   ```json
   {
     "worker": "1.0.0",
     "server": "1.0.0",
     "studio": "1.0.0"
   }
   ```
2. Commit with conventional message: `chore: prepare v1.0.0 milestone release`.
3. Push to `master`. `release-please` will synchronize its Release PR to the manifest version targets.

---

## 7. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: The "Code-First, Docs-Never" Workflow
* **Flawed Practice:** Opening a PR with 2,000 lines of complex C++ IPC logic without touching a single document in `docs/specs/` or `docs/guides/`.
* **Failure Mode:** Reviewers cannot verify if the implementation matches architectural intent. Downstream teams discover undocumented breaking changes only after deployment.
* **Corrected Approach:** Reject PR immediately. Require Stage 2 (Spec Update) and Stage 6 (Doc Sync) before scheduling review.

### Anti-Pattern 2: Linux-Only Verification (Ignoring Windows/macOS)
* **Flawed Practice:** Developing and testing exclusively on Ubuntu, ignoring MSVC compiler warnings and POSIX-specific file path assumptions.
* **Failure Mode:** Windows CI fails with missing header errors (`<unistd.h>`), or Windows runtime crashes due to invalid named pipe paths.
* **Corrected Approach:** Rely on cross-platform abstractions (`nng`, `reproc++`, `std::filesystem`) and ensure all CI platform matrix jobs are green.

### Anti-Pattern 3: Destructive Squash Merging of Multi-Commit Milestones
* **Flawed Practice:** Using GitHub's "Squash and Merge" button to squash 20 granular commits into a single commit with message "Updates".
* **Failure Mode:** Destroys git history; `git bisect` cannot isolate which specific commit introduced a regression. Conventional Commit types are lost, breaking `release-please`.
* **Corrected Approach:** Keep commits clean and atomic, and merge using `--no-ff` merge commits.

---

## 8. Verification & Compliance Checklist

Maintainers and PR authors **MUST** verify the following audit criteria prior to merging:

- [ ] **Stage Order Maintained:** Development proceeded through the 9 stages in order.
- [ ] **Conventional Commit Title:** PR title conforms to `feat(scope): ...` format.
- [ ] **Multi-Platform CI Green:** Linux, macOS, and Windows matrix jobs have passed.
- [ ] **Sanitizers Clean:** Clang AddressSanitizer and UndefinedBehaviorSanitizer report zero issues.
- [ ] **Two Maintainer Approvals:** Core subsystem code reviewed and approved by two maintainers.
- [ ] **Non-Fast-Forward Merge:** Merged using `--no-ff` to preserve branch graph integrity.
- [ ] **Branch Deleted:** Topic branch deleted from remote immediately upon merge.
- [ ] **Release Publication Monitored:** Release PR merged and automated artifacts verified in GitHub Releases.
