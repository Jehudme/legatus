# Master Engineering Guidelines Specification

## Document Metadata
* **Guideline ID:** GL-0000
* **Scope:** Universal / Cluster-Wide (`legatusd`, `server`, `studio`, `solix`, `protocols`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Protocol Architects, Documentation Stewards
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `NOT RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

The Legatus runtime platform is a mission-critical, ultra-high-density distributed serverless runtime designed for deterministic compute execution across heterogeneous cluster nodes. It encompasses low-level C++20 system daemons (`legatusd`), sandboxed process runners (`legatus-runner`), a Java 21 Spring Boot orchestration coordinator (`server`), a JavaFX operator cockpit (`studio`), and an upstream deterministic guest virtual machine engine (`solix`).

In an ecosystem where nanosecond latencies, deterministic memory reclamation, strict zero-leak constraints, and cross-platform IPC (Linux, macOS, Windows) converge, informal conventions and fragmented documentation cause catastrophic operational failures. Architectural drift between binary implementations and documented invariants leads directly to cluster split-brain scenarios, memory safety vulnerabilities, unhandled protocol state divergence, and corrupted releases.

This master specification establishes the foundational governance, strict normative hierarchy, and enforcement mechanisms for the entire **Legatus Engineering Guidelines Suite** (`docs/dev/guidelines/`). It eliminates ambiguity by dictating that documentation is a formal engineering contract. Every guideline within this suite is legally binding on all codebase contributions, automated CI gates, and architectural proposals.

---

## 2. Normative Invariants & Core Principles

All contributors and maintainers **MUST** strictly adhere to the following non-negotiable principles:

1. **The Normative Authority Hierarchy:** When resolving design disagreements, code review conflicts, or architectural ambiguity, authority **MUST** be evaluated strictly according to the following total ordering:
   $$\text{Code Implementation} \prec \text{Developer Guides} \prec \text{Architecture Decisions (ADRs)} \prec \text{Subsystem Specs} \prec \text{Developer Guidelines}$$
   * If source code conflicts with a Developer Guide (`docs/guides/`), the guide **MUST** be updated or the code fixed to align with the guide.
   * If a Developer Guide conflicts with an Architecture Decision Record (`docs/dev/decisions/`), the ADR governs.
   * If an ADR conflicts with a formal Subsystem Specification (`docs/specs/`), the Subsystem Specification governs.
   * If a Subsystem Specification conflicts with a Developer Guideline (`docs/dev/guidelines/`), the Developer Guideline **SHALL** reign supreme. Any spec or code violating a guideline is non-compliant and **MUST NOT** be merged.
2. **Strict RFC 2119/8174 Compliance:** Every guideline document **MUST** express normative rules using uppercase RFC 2119 keywords (**MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL**). Uncapitalized or colloquial variants (e.g., "should", "must", "can") carry no normative force and **MUST NOT** be used to specify binding constraints.
3. **Mandatory Canonical Seven-Section Structure:** Every guideline document in `docs/dev/guidelines/` **MUST** adhere to the standard 7-section layout defined in GL-0004 without exception or section omission.
4. **Zero-Drift Invariant:** Technical documentation **SHALL NOT** lag behind code. Any pull request that alters wire schemas, CLI flags, configuration fields, error codes, or runtime invariants **MUST** update the corresponding guideline, specification, and test matrix within the exact same pull request.
5. **No Speculative or Aspirational Writing:** Guidelines **MUST NOT** specify speculative features as established facts. Unimplemented proposals **MUST** reside exclusively in draft RFCs or marked with clear experimental disclaimers.

---

## 3. Structural Specification & Schema

### 3.1 Guideline Suite Manifest & Matrix

The Legatus developer guideline suite consists of 12 canonical documents. Each document addresses a discrete operational vector and is bound to automated or procedural verification gates:

| Guideline ID | Document Path | Operational Scope | Core Focus | CI Enforcement Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **GL-0000** | `docs/dev/guidelines/README.md` | Universal | Master index, normative hierarchy, and suite governance | Markdown lint, link validation |
| **GL-0001** | `docs/dev/guidelines/authoring-commits.md` | Universal | Conventional Commits v1.0.0, Release Please, SemVer 2.0 | `commitlint`, PR title regex gate |
| **GL-0002** | `docs/dev/guidelines/branching-conventions.md` | Repository | Trunk-based development, `--no-ff` merges, branch lifecycle | GitHub Branch Protection Rules |
| **GL-0003** | `docs/dev/guidelines/authoring-workflows.md` | Development Lifecycle | End-to-end development cycle, PR gates, review checklist | CI multi-job matrix status checks |
| **GL-0004** | `docs/dev/guidelines/authoring-guidelines.md` | Meta-Standard | Schema, review rules, and lifecycle for new guidelines | Guideline schema validator |
| **GL-0005** | `docs/dev/guidelines/authoring-specs.md` | Architecture / Specs | Separation of contracts vs code, mathematical models, state | Spec linter & PR peer review |
| **GL-0006** | `docs/dev/guidelines/authoring-decisions.md` | Architecture / ADRs | MADR format, immutable decision records, trade-off matrices | ADR index validation & audit |
| **GL-0007** | `docs/dev/guidelines/authoring-protocols.md` | Network / Transport | LCTP, WebSocket envelopes, FSMs, jittered timing, close codes | Wire frame JSON schema tests |
| **GL-0008** | `docs/dev/guidelines/authoring-milestones.md` | Project Management | Milestone lifecycle, P0-P3 blocker taxonomy, Definition of Done | Milestone tracking audit |
| **GL-0009** | `docs/dev/guidelines/authoring-test.md` | Quality Engineering | Catch2 v3, JUnit 5, test matrices, ASan/UBSan zero-leak gates | `ctest`, `mvn test`, ASan/UBSan CI |
| **GL-0010** | `docs/dev/guidelines/authoring-docs.md` | Documentation | Living documentation invariants, CLI/API drift verification | `docs-verify.sh`, markdown-lint |
| **GL-0011** | `docs/dev/guidelines/language-conventions.md` | Polyglot Codebase | C++20 (RAII/std::span), Java 21 (Loom/Records), Solix (ARC) | `clang-tidy`, Checkstyle, SpotBugs |

### 3.2 Standard Document Front-Matter Metadata

Every guideline file **MUST** begin with the following metadata header block immediately succeeding the primary level-1 heading:

```markdown
# <Guideline Title>

## Document Metadata
* **Guideline ID:** GL-<XXXX>
* **Scope:** <Subsystems affected, or "Universal / Cluster-Wide">
* **Status:** <Draft | Review | Final Implementation Standard | Deprecated>
* **Audience:** Core Systems Engineers, Platform Contributors, Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`)
```

---

## 4. Production-Ready Template

Engineers creating or updating the root documentation index **MUST** use the following production template:

```markdown
# Legatus Engineering Guidelines Index

## Document Metadata
* **Guideline ID:** GL-0000
* **Scope:** Universal / Cluster-Wide
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`)

---

## 1. Executive Summary & Purpose
[Define the scope of the repository and the critical nature of architectural discipline across components.]

## 2. Normative Invariants & Core Principles
[Detail the non-negotiable hierarchy and RFC 2119 compliance requirements.]

## 3. Structural Specification & Schema
[Provide the complete manifest table, scope definitions, and CI mapping.]

## 4. Production-Ready Template
[Provide clean copy-pasteable navigation structures and guidelines templates.]

## 5. Concrete Production Example
[Illustrate standard operational workflows and conflict resolution cases.]

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)
[Illustrate failure modes: circular authority, silent convention changes, unverified guides.]

## 7. Verification & Compliance Checklist
[Provide sign-off checklists for documentation integrity and CI integration.]
```

---

## 5. Concrete Production Example

### Scenario: Resolving Conflict between Implementation and Guidelines

A contributor submits a pull request (`pull/142`) modifying `legatusd` process supervision. In the PR:
1. The code in `legatus-runner` utilizes `std::abort()` upon receiving an instruction budget exhaustion signal.
2. The developer updates `docs/guides/05-worker.md` stating: "The runner terminates abnormally via abort when instructions run out."
3. However, `docs/specs/worker-spec.md` Section 4.2 states: "Upon instruction quota exhaustion, `legatus-runner` **MUST** trap cleanly, format an execution error envelope to stdout, and exit with code `75` (`EX_TEMPFAIL`)."
4. Furthermore, `docs/dev/guidelines/language-conventions.md` (GL-0011) Section 2 mandates: "C++20 daemons and runners **MUST NOT** call raw `std::abort()` or `std::terminate()` in recoverable guest code paths; all lifecycle exits **MUST** be deterministic and structured."

**Resolution using the Normative Authority Hierarchy:**
* The pull request is automatically rejected during review.
* Authority ranking dictates:
  $$\text{Code} \prec \text{Guide (05-worker)} \prec \text{Spec (worker-spec)} \prec \text{Guideline (GL-0011)}$$
* The guideline (GL-0011) and subsystem specification (`worker-spec.md`) outrank both the implementation code and the introductory guide.
* The developer is required to remove `std::abort()`, implement structured exit code `75` with stdout JSON error serialization, and update `docs/guides/05-worker.md` to reflect the compliant behavior.

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: "Code as Source of Truth" (Hierarchy Inversion)
* **Flawed Practice:** An engineer merges a change to the worker WebSocket heartbeat interval from $5000\text{ ms}$ to $2000\text{ ms}$ in C++ source without updating GL-0007 or `worker-wire.md`, arguing: "The code is the ultimate truth."
* **Failure Mode:** The Java orchestration server (`server`) retains the $5000\text{ ms}$ timeout with a $15000\text{ ms}$ dead-man switch. Third-party client tools and the JavaFX console (`studio`) begin failing heartbeat timing invariants, resulting in spurious node evictions and false alerts across the cluster.
* **Corrected Approach:** Any modification to timing intervals **MUST** begin with an amendment to the protocol spec (`worker-wire.md`) and a corresponding commit adhering to GL-0001 and GL-0007.

### Anti-Pattern 2: Unstandardized Guidelines & Section Omissions
* **Flawed Practice:** Creating a guideline document `authoring-storage.md` that contains only a 10-line bulleted list of loose recommendations without metadata, schema, templates, or counter-examples.
* **Failure Mode:** Reviewers apply inconsistent standards. Without concrete counter-examples and templates, junior contributors interpret suggestions loosely, leading to configuration file rot and unparseable storage paths.
* **Corrected Approach:** Author the guideline strictly against GL-0004, including all 7 canonical sections and automated verification checks.

### Anti-Pattern 3: Colloquial Language in Normative Clauses
* **Flawed Practice:** Writing: "Engineers should probably run sanitizers if they have time."
* **Failure Mode:** Contributors bypass ASan/UBSan runs on local machines, offloading leak discovery to CI or, worse, deploying memory corruptions to bare-metal compute workers.
* **Corrected Approach:** "Engineers **MUST** execute Catch2 test binaries under AddressSanitizer (ASan) and UndefinedBehaviorSanitizer (UBSan) with zero detected errors prior to pull request submission."

---

## 7. Verification & Compliance Checklist

Before approving any modifications or additions to `docs/dev/guidelines/`, the author and reviewers **MUST** verify the following audit criteria:

- [ ] **Authority Compliance:** The document respects the Normative Authority Hierarchy ($\text{Code} \prec \text{Guides} \prec \text{ADRs} \prec \text{Specs} \prec \text{Guidelines}$).
- [ ] **Structural Adherence:** The document contains all 7 canonical sections numbered and titled identically to the specification.
- [ ] **Metadata Integrity:** The header contains valid `Guideline ID`, `Scope`, `Status`, `Audience`, and `Normative Conformance` fields.
- [ ] **RFC 2119 Precision:** All normative constraints utilize capitalized RFC 2119 keywords (**MUST**, **MUST NOT**, **SHOULD**, etc.).
- [ ] **Legatus Contextualization:** All examples, schemas, and counter-examples explicitly reference real Legatus binaries (`legatusd`, `legatus`, `server`, `studio`, `solix`).
- [ ] **Copy-Paste Template:** Section 4 contains a fully functional, unabbreviated production template.
- [ ] **Real-World Anti-Patterns:** Section 6 includes concrete flawed code/spec patterns, detailed catastrophe descriptions, and exact corrected forms.
- [ ] **Automated Linting:** Document passes Markdown linting with zero broken internal file links.
