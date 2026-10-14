# Milestone 001: Architectural Foundation, Engineering Guidelines & Subsystem Specifications

## Document Metadata
* **Milestone ID:** MS-001
* **Target Version:** `v0.1.0-docs-foundation`
* **Status:** VALIDATED
* **Lead Architect:** @jehud-metellus
* **Target Delivery Date:** 2026-10-14
* **Related ADRs:** ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005, ADR-0006, ADR-0007, ADR-0008

---

## 1. Executive Summary & Objectives

Establish the complete, binding constitutional and architectural foundation for the Legatus distributed serverless platform prior to writing subsystem implementation code (`GL-0003` Stage 2 Specification-First Invariant). This milestone delivers the 12-document Engineering Guidelines Suite (`GL-0000` through `GL-0011`), the 8 foundational Architecture Decision Records (`ADR-0001` through `ADR-0008`), the 3 core Subsystem Specifications (`worker-spec.md`, `server-spec.md`, `studio-spec.md`), and the 3 Layer 7 Wire Protocol Specifications (`worker-wire.md`, `studio-wire.md`, `api-endpoint.md`).

---

## 2. Scope & Subsystems Affected

* `docs/dev/guidelines/`: Master engineering constitution (`GL-0000` to `GL-0011`) governing commits, branching, workflows, guidelines, specs, ADRs, protocols, milestones, testing, living docs, and polyglot coding standards.
* `docs/dev/decisions/`: Immutable MADR log (`ADR-0001` to `ADR-0008`) justifying technology selections and security models.
* `docs/specs/`: Implementation-agnostic subsystem specifications (`worker-spec.md`, `server-spec.md`, `studio-spec.md`) with zero raw implementation code (`GL-0005`).
* `docs/specs/protocols/`: Layer 7 network contracts (`worker-wire.md`, `studio-wire.md`, `api-endpoint.md`) with canonical JSON envelopes, FSMs, and timing jitter (`GL-0007`).
* `docs/architecture/`: High-level topology diagrams, UML sequence flows, and Studio cockpit wireframe catalog.

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Master Engineering Guidelines Suite (`GL-0000` – `GL-0011`):** Author all 12 normative developer guidelines enforcing the 7-section canonical layout, RFC 2119 keywords, and concrete Legatus counter-examples. (PR #101)
- [x] `[P0]` **Core Subsystem Specifications (`GL-0005`):** Author `worker-spec.md`, `server-spec.md`, and `studio-spec.md` defining finite state machines, memory arena bounds, and deterministic exit codes (`0`, `1`, `71`, `75`, `124`, `130`, `137`, `139`) with zero implementation code blocks. (PR #102)
- [x] `[P0]` **Layer 7 Wire & REST Protocol Specifications (`GL-0007`):** Author `worker-wire.md` (LCTP), `studio-wire.md` (LSTCP), and `api-endpoint.md` with canonical 4-field JSON envelopes (`type`, `id`, `timestamp`, `payload`), 32 MB frame caps, and RFC 6455 close code matrices. (PR #103)
- [x] `[P0]` **Foundational Architecture Decision Records (`GL-0006`):** Author and accept `ADR-0001` (Spring Boot 3 / Java 21), `ADR-0002` (OpenJFX 21 / AtlantaFX), `ADR-0003` (Release Please), `ADR-0004` (Solix VM), `ADR-0005` (Quarantine Enrollment), `ADR-0006` (Jittered Heartbeats), `ADR-0007` (`nng` IPC), and `ADR-0008` (`reproc++` Isolation). (PR #105–#115)

### 3.2 P1: High Priority (Milestone Core)
- [x] `[P1]` **Milestone Delivery Governance (`MS-001` – `MS-004`):** Author phased milestone delivery plans and synchronize `docs/dev/milestones/README.md`. (PR #116)
- [x] `[P1]` **Architecture Sequence & Topology Diagrams:** Synchronize `docs/architecture/overview.md`, `docs/architecture/sequences/` (`worker-operations`, `server-operations`, `task-operations`, `telemetry-operations`, `user-operations`), and `docs/architecture/wireframes/README.md`. (PR #104)
- [x] `[P1]` **Cross-Platform Security & Boundary Hardening:** Specify Windows Named Pipe SDDL descriptors (`D:(A;;GRGW;;;AU)`), `stdin` execution envelope streaming (32KB `CreateProcessW` defense), and `CREATE_NEW_PROCESS_GROUP` console break isolation in `worker-spec.md`. (PR #115)

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [x] `[P2]` **Directory Navigation Indexes:** Author `README.md` navigation indexes across `docs/dev/guidelines/`, `docs/dev/decisions/`, `docs/dev/milestones/`, and `docs/specs/`. (PR #116)
- [x] `[P2]` **Operator & Developer Operational Guides Baseline:** Establish initial structure for `docs/guides/` (`01-getting-started.md`, `02-server.md`, `04-studio.md`, `05-worker.md`). (PR #117)

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [x] `[P3]` **Markdown Linting & Relative Link Audit:** Verify all internal Markdown links and LaTeX math blocks render cleanly across GitHub and IDE previewers. (PR #118)

---

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | N/A (Documentation-only milestone; test harnesses defined in `GL-0009`) | Schema & template inspection | [x] Verified |
| **JUnit 5 Suite** | N/A (Documentation-only milestone; test harnesses defined in `GL-0009`) | Schema & template inspection | [x] Verified |
| **Sanitizer Gate** | Zero raw implementation code in `docs/specs/` (`GL-0005`) | `scripts/verify-specs-no-code.sh` | [x] Verified |
| **Documentation** | All 12 guidelines, 8 ADRs, and 6 specs pass Markdownlint and link checks | `markdownlint-cli2` | [x] Verified |
| **ADR Governance** | `ADR-0001` through `ADR-0008` accepted and indexed in `decisions/README.md` | Audit against `GL-0006` | [x] Verified |

---

## 5. Risk Assessment & Mitigation Strategies

* **Risk 1: Specification-to-Implementation Drift During Subsequent Milestones**
  * *Mitigation:* Enforce `GL-0010` (Zero-Drift Mandate) and `GL-0003` blocking CI gates requiring any PR in `MS-002`, `MS-003`, or `MS-004` that alters wire payloads or CLI flags to co-commit specification updates.
* **Risk 2: Cross-Subsystem Exit Code or Frame Enum Mismatch**
  * *Mitigation:* Unified terminal status codes (`COMPLETED`, `FAILED`, `ERR_INSTRUCTION_BUDGET_EXCEEDED`, `ERR_OUT_OF_MEMORY`, `ERR_TIMEOUT`, `ABORTED`, `KILLED_FORCIBLY`, `CRASHED`) and exit codes (`0`, `1`, `71`, `75`, `124`, `130`, `137`, `139`) across `worker-spec.md`, `worker-wire.md`, `server-spec.md`, and `api-endpoint.md`.
