# Trunk-Based Branching Topology Specification

## Document Metadata
* **Guideline ID:** GL-0002
* **Scope:** Repository Git Topology & Release Management
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Release Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

In a distributed monorepo supporting high-performance native daemons (`legatusd`), cross-platform CLI utilities (`legatus`), an orchestration server (`server`), and an administration cockpit (`studio`), branch mismanagement is a primary source of integration failure. Long-lived feature branches foster massive merge conflicts, stale architectural assumptions, delayed regression detection, and broken releases.

This specification formalizes the **Trunk-Based Branching Model** for the Legatus project. It establishes `master` as the sole protected trunk of truth, dictates strict branch taxonomy and naming rules, enforces short-lived topic branches, and mandates non-fast-forward (`--no-ff`) merges to guarantee a fully auditable, bisectable, and milestone-aligned Git history.

---

## 2. Normative Invariants & Core Principles

All contributors and repository administrators **MUST** comply with the following normative rules:

1. **Protected Trunk Invariant:** The `master` branch **MUST** remain continuously production-ready, deployable, and protected by GitHub branch protection rules. Direct commits and non-reviewed pushes to `master` are strictly prohibited for all users, including repository administrators.
2. **Short-Lived Topic Branches:** Feature and bugfix branches **MUST** be short-lived, with a target lifecycle not exceeding **3 business days** from branching to merge. Long-lived branches spanning multiple weeks or months **SHALL NOT** be maintained without explicit architectural variance.
3. **Canonical Branch Naming Grammar:** Branch names **MUST** adhere to the formal syntax:
   $$\text{<category>}/\text{<scope>}-\text{<short-description>}$$
   where `<category>` is one of the approved prefixes (`feat`, `fix`, `spec`, `refactor`, `docs`, `chore`), and `<scope>` matches the registered component scopes from GL-0001.
4. **Mandatory Non-Fast-Forward Merging (`--no-ff`):** Merges into `master` **MUST** execute using the `--no-ff` (non-fast-forward) flag. This creates a dedicated merge commit that preserves the cohesive identity of the topic branch, documents the associated pull request, and enables clean atomic rollbacks.
5. **Immediate Post-Merge Branch Deletion:** Feature and topic branches **MUST** be deleted from the remote repository immediately upon successful merge into `master`. Stale remote branches cluttering the repository **MUST NOT** be retained.
6. **No Rewriting Public or Shared History:** Force-pushing (`git push --force`) to `master` or shared collaboration branches is strictly prohibited. Force-pushing with lease (`--force-with-lease`) is permitted *only* on private, unmerged topic branches to rebase onto fresh `master` commits prior to review.

---

## 3. Structural Specification & Schema

### 3.1 Branch Category Taxonomy

| Branch Category | Target Workflow | SemVer Consequence | Typical Lifetime |
| :--- | :--- | :--- | :--- |
| `feat/` | New functionality, protocol frames, CLI subcommands | Minor ($0.X.0$) or Major ($X.0.0$ if breaking) | 1–3 days |
| `fix/` | Bug fixes, memory leak patches, race condition remedies | Patch ($0.0.X$) | < 24 hours |
| `spec/` | Architectural specifications, wire frame schemas, ADRs | None (Docs) | 1–2 days |
| `refactor/` | Internal code restructuring without behavioral shifts | None | 1–2 days |
| `docs/` | Guidelines, user guides, API references | None | < 24 hours |
| `chore/` | CI workflow tuning, dependency updates, tool configs | None | < 24 hours |

### 3.2 Branch Topology Diagram

```
master ──────●───────────────────────────────●─────────────────────●───────► (Always Deployable)
              \                             / (Merge commit: --no-ff)
               \                           /
feat/worker-    ●─────────●───────────────●
heartbeat-jitter (Commit 1) (Commit 2) (Commit 3)
```

### 3.3 Protected Branch Configuration Rules

The following GitHub branch protection rules **MUST** be permanently active on `master`:
* **Require a pull request before merging:** Enabled (Minimum 2 approvals required).
* **Require status checks to pass before merging:** Enabled (All CI platform matrix jobs).
* **Require branches to be up to date before merging:** Enabled.
* **Require linear history:** Disabled (to allow explicit `--no-ff` merge commits).
* **Do not allow bypassing the above settings:** Enabled.

### 3.4 Automated Release PR Branches (`release-please`)

Per **ADR-0003** and **GL-0003**, automated release staging is performed on a dedicated system branch maintained exclusively by Google `release-please`:
* **Branch Pattern:** `release-please--branches--master`
* **Purpose:** Collects Conventional Commits from `master`, increments `.release-please-manifest.json`, and updates component `CHANGELOG.md` files.
* **Invariant:** Engineers **MUST NOT** push manual commits directly to this bot branch. When milestone criteria are satisfied, maintainers merge the associated Release PR into `master` using standard `--no-ff` merge commits, which triggers the automated tagging and artifact publication pipeline.

---

## 4. Production-Ready Template

Contributors **MUST** execute the following sequence when creating, maintaining, and merging topic branches:

```bash
# 1. Synchronize local master with upstream
git checkout master
git fetch origin --prune
git rebase origin/master

# 2. Cut a new topic branch using canonical naming
git checkout -b <category>/<scope>-<short-description>

# 3. Work atomically with Conventional Commits
git add <files>
git commit -m "<type>(<scope>): <imperative description>"

# 4. Keep branch up to date with master during development
git fetch origin
git rebase origin/master

# 5. Push to remote and open Pull Request
git push -u origin <category>/<scope>-<short-description>

# 6. Post-Approval Non-Fast-Forward Merge (via CLI or GitHub PR UI)
# Note: GitHub PR setting "Create a merge commit" performs this automatically.
git checkout master
git merge --no-ff <category>/<scope>-<short-description> \
  -m "Merge pull request #<PR_NUMBER> from <category>/<scope>-<short-description>"
git push origin master

# 7. Delete local and remote branches
git branch -d <category>/<scope>-<short-description>
git push origin --delete <category>/<scope>-<short-description>
```

---

## 5. Concrete Production Example

### Lifecycle of `feat/worker-heartbeat-jitter`

#### 1. Inception & Branching
```bash
git checkout master
git pull --rebase origin master
git checkout -b feat/worker-heartbeat-jitter
```

#### 2. Atomic Conventional Commits
```bash
# Commit 1: Update protocol specification first (GL-0003 Stage 2)
git add docs/specs/protocols/worker-wire.md
git commit -m "docs(wire): specify +/- 10% uniform jitter for worker heartbeats"

# Commit 2: Implement jitter calculation in daemon (GL-0003 Stage 3)
git add worker/daemon/src/heartbeat_scheduler.cpp
git commit -m "feat(worker): apply randomized uniform jitter to heartbeat timer"

# Commit 3: Add Catch2 test fixture verifying timing distribution (GL-0003 Stage 4)
git add worker/tests/unit/test_heartbeat_jitter.cpp
git commit -m "test(worker): add Catch2 verification for heartbeat jitter variance"
```

#### 3. PR Review & Non-Fast-Forward Merge
The PR is opened, passes the multi-platform CI matrix, and receives approvals from `@sys-architect` and `@worker-lead`.
The PR is merged with `--no-ff`:

```bash
git checkout master
git pull --rebase origin master
git merge --no-ff feat/worker-heartbeat-jitter -m "Merge pull request #184 from feat/worker-heartbeat-jitter"
git push origin master
git push origin --delete feat/worker-heartbeat-jitter
```

The resulting Git history clearly delineates the discrete feature delivery, allowing `git log --graph` to visualize the exact boundary of PR #184.

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Direct Pushes to `master`
```bash
# CATASTROPHIC FLAW:
git checkout master
git commit -am "quick fix for socket crash"
git push origin master
```
* **Failure Mode:** Bypasses code review, automated CI tests, AddressSanitizer checks, and conventional commit linting. If the commit contains a subtle regression, production compute nodes crash immediately upon deployment.
* **Corrected Approach:** Always open a topic branch (`fix/worker-socket-crash`) and submit a pull request through CI gates.

### Anti-Pattern 2: The Stale "Megabranch" (3-Month Lifespan)
* **Flawed Practice:** An engineer maintains a private branch `feat/massive-networking-refactor` for 90 days without merging to `master`.
* **Failure Mode:** `master` advances with hundreds of commits. When finally merged, merge conflicts are overwhelming, regressions are nearly impossible to trace, and the massive diff cannot be effectively reviewed.
* **Corrected Approach:** Break large refactorings into discrete, backward-compatible milestones that merge every 24–72 hours behind feature flags.

### Anti-Pattern 3: Ambiguous Branch Names
```bash
# FLAWED:
git checkout -b my-patch
git checkout -b bugfix
git checkout -b test1
```
* **Failure Mode:** Branch intent is completely opaque to team members and automated tooling. Impossible to map to components or issues.
* **Corrected Approach:** Use standard category and scope: `fix/runner-memory-leak`.

---

## 7. Verification & Compliance Checklist

All engineers and release maintainers **MUST** enforce the following criteria:

- [ ] **Branch Taxonomy:** Branch name matches `<category>/<scope>-<short-description>`.
- [ ] **Rebased on Master:** Branch is cleanly rebased on the latest `master` commit before merging.
- [ ] **Non-Fast-Forward Merge:** Merges into `master` execute with `--no-ff` (merge commit generated).
- [ ] **No Force Pushes to Shared:** Shared branches never experience forced history rewrites.
- [ ] **Remote Deletion:** Topic branch is deleted from GitHub immediately upon PR merge.
- [ ] **Clean Git Graph:** `git log --graph` reveals clearly bounded feature merge envelopes.
