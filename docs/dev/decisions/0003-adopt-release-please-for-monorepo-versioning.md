# ADR-0003: Adopt Conventional Commits & Google Release Please for Monorepo Versioning

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @devops-lead, @qa-lead
* **Date:** 2026-10-14
* **Technical Story:** Issue #107 / PR #110
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus platform is organized as a polyglot monorepo containing multiple independently versioned deliverables:
* `worker/`: High-density C++20 daemon (`legatusd`), runner sandbox (`legatus-runner`), and operator CLI (`legatus`).
* `server/`: Centralized orchestration broker (`legatus-server`) in Java 21 / Spring Boot 3.
* `studio/`: Cross-platform desktop telemetry cockpit (`legatus-studio`) in OpenJFX 21.
* `docs/`: Master architectural specifications, wire protocols, and engineering guidelines.

When multiple engineering teams contribute to different subsystems within the same Git repository, coordinating software releases creates severe operational bottlenecks:
1. **Manual Changelog Drift:** Handcrafted `CHANGELOG.md` files are frequently neglected, contain inconsistent formatting, omit bug fixes, or fail to highlight breaking API changes.
2. **Version Lock-Step vs. Independent Lifecycles:** Forcing all subsystems to share a single monolithic version number artificially inflates the version of unchanged components (e.g., bumping `studio` from `v1.0.0` to `v2.0.0` due to a breaking C++ wire change in `worker`). Conversely, managing independent component releases manually with manual Git tags is error-prone.
3. **Release Friction:** Requiring release engineers to manually calculate SemVer bumps, tag commits, edit release notes, and draft GitHub Releases slows down continuous delivery.

We need an automated, deterministic release and versioning mechanism that treats commit messages as the single source of truth for semantic releases across independent monorepo components.

---

## 2. Decision Drivers (Forces)

1. **Automated Semantic Versioning (SemVer 2.0.0):** Version increments (Major, Minor, Patch) must be determined deterministically by parsing commit metadata, eliminating human guesswork.
2. **Path-Scoped Monorepo Independence:** Changes to files under `worker/` must only increment the version and changelog of the `worker` component, without bumping `server` or `studio` unless shared dependencies or wire protocols are affected.
3. **Automated Changelog Curation:** Detailed, categorized changelogs (`Features`, `Bug Fixes`, `Performance Improvements`, `Breaking Changes`) must be generated automatically in each component directory.
4. **Human-in-the-Loop Release Gate:** The system must stage release candidate changes into a dedicated Pull Request, allowing maintainers to review version bumps and release notes before triggering artifact publishing.
5. **Standardized Git Commit Grammar:** Engineering contributions must adhere to a strict, machine-readable commit specification that is linted in CI.

---

## 3. Considered Options

* **Option 1:** Google `release-please` GitHub Action with component path manifests (`.release-please-manifest.json` + `release-please-config.json`) driven by Conventional Commits v1.0.0.
* **Option 2:** Manual Git Tagging with handcrafted markdown release notes and milestone tracking.
* **Option 3:** `semantic-release` (Node.js ecosystem) with community multi-semantic-release monorepo plugins.
* **Option 4:** Changesets (`@changesets/cli`) requiring explicit changeset markdown files per pull request.

---

## 4. Evaluation of Considered Options

### Option 1: Google Release Please & Conventional Commits
* **Description:** Engineers write commits strictly following Conventional Commits v1.0.0 (`feat`, `fix`, `perf`, `refactor`, `docs`, etc.) scoped to canonical components (`worker`, `server`, `studio`, `wire`, `cli`). In CI, Google's `release-please-action` analyzes the commit graph against path configurations. It continuously updates a dedicated Release PR for each component. When maintainers merge the Release PR into `master`, `release-please` automatically tags the commit (e.g., `worker-v1.2.0`), publishes GitHub Releases, and updates the local component `CHANGELOG.md`.
* **Good:**
  * **First-Class Monorepo Support:** Officially supports path-based monorepos where each subsystem maintains its own version and changelog (`worker/CHANGELOG.md`, `server/CHANGELOG.md`, etc.).
  * **Deterministic SemVer Calculation:** `feat` triggers Minor, `fix` and `perf` trigger Patch, and `feat!:` or `BREAKING CHANGE:` footers trigger Major releases.
  * **Pull Request Workflow:** Maintains an open Release PR that aggregates changes until maintainers decide to merge and cut the release.
  * **Zero Node.js Runtime in Repository:** Runs as a turnkey GitHub Action without requiring Node.js or `package.json` configurations in our C++/Java codebase.
* **Bad:**
  * Requires strict commit message hygiene across all contributors; commits with improper prefixes (`"update stuff"`) will not trigger releases.
  * Squashed pull requests require careful squash commit message editing to preserve conventional commit formatting.

### Option 2: Manual Git Tagging & Changelogs
* **Description:** Maintainers manually calculate the next SemVer version, edit `CHANGELOG.md`, commit the change, and run `git tag -a server-v1.2.0`.
* **Good:**
  * No CI tooling dependencies or commit message constraints.
* **Bad:**
  * Highly prone to human error, missed releases, and inconsistent changelog formatting.
  * Fails the Definition of Done in `GL-0008` and `GL-0003` for automated, repeatable delivery.

### Option 3: Semantic Release with Multi-Semantic-Release
* **Description:** Automated release pipeline powered by the Node.js `semantic-release` ecosystem and community monorepo plugins.
* **Good:**
  * Mature ecosystem in the JavaScript/TypeScript community.
* **Bad:**
  * Community monorepo plugins are notoriously brittle, frequently unmaintained, and prone to concurrency lockups in CI.
  * Introduces an unwanted Node.js / npm runtime dependency to manage releases for a pure C++20 and Java 21 repository.

### Option 4: Changesets (`@changesets/cli`)
* **Description:** Contributors must run a CLI tool on every branch to generate a temporary Markdown changeset file describing their change.
* **Good:**
  * High control over changelog wording.
* **Bad:**
  * Significant contributor friction (creating extra files for every small bugfix).
  * Tied to the JavaScript / npm ecosystem.

---

## 5. Decision Outcome

Chosen Option: **Option 1: Google Release Please with Conventional Commits v1.0.0**.

Google `release-please` provides native path-based monorepo support, turnkey GitHub Actions integration without adding external toolchain bloat, and automated SemVer calculation driven directly by Conventional Commits.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Component versions and changelogs (`worker/CHANGELOG.md`, `server/CHANGELOG.md`, `studio/CHANGELOG.md`) are updated automatically with zero manual editing.
* Release PRs provide a clear visual staging area where maintainers can inspect upcoming releases before they are tagged and published.
* Every commit in the repository history conforms to a machine-readable, standard format.
* Tagged releases automatically trigger artifact publication pipelines (`.github/workflows/pipeline-publish.yml`).

### 6.2 Negative Consequences (Trade-offs & Technical Debt)
* **Commit Linting Enforcement:** Pull requests must pass automated commit message linting (`commitlint` in `pipeline-verify.yml`); non-conforming commits block PR merges.
* **Squash Merge Vigilance:** When using GitHub's "Squash and Merge", maintainers must ensure the PR title adheres strictly to Conventional Commits, as GitHub defaults to using the PR title for the single squashed commit.

### 6.3 Neutral Consequences
* Configuration is maintained in `.release-please-manifest.json` (tracking active versions) and `release-please-config.json` (defining component path packages).

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Formally codified in [`docs/dev/guidelines/authoring-commits.md`](../guidelines/authoring-commits.md) (`GL-0001`) and [`docs/dev/guidelines/authoring-workflows.md`](../guidelines/authoring-workflows.md) (`GL-0003`).
* **CI Configuration:**
  * `.github/workflows/pipeline-release.yml` executes `google-github-actions/release-please-action` on every push to `master`.
  * `.github/workflows/pipeline-verify.yml` validates commit message formatting on all pull request branches.
* **Automated Verification:**
  * Commits lacking conventional prefixes or scopes (`(worker)`, `(server)`, `(studio)`, etc.) are rejected by CI gate checks.
