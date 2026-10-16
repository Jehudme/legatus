# Legatus Architecture Decision Records (ADR) Index

## Document Metadata
* **Scope:** Architectural & Systems Design Governance (`docs/dev/decisions/`)
* **Governing Guideline:** [`docs/dev/guidelines/authoring-decisions.md`](../guidelines/authoring-decisions.md) (`GL-0006`)
* **Status:** Active Immutable Log
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Overview & Governance Invariants

This directory contains the immutable historical log of all Architecture Decision Records (ADRs) for the Legatus distributed compute platform. In accordance with the **Normative Authority Hierarchy (`GL-0000`)**, accepted ADRs outrank developer guides and source code implementations, and are subordinate only to formal Subsystem Specifications (`docs/specs/`) and Developer Guidelines (`docs/dev/guidelines/`):

$$
\text{Code Implementation} \prec \text{Developer Guides} \prec \text{Architecture Decisions (ADRs)} \prec \text{Subsystem Specs} \prec \text{Developer Guidelines}
$$

Every ADR in this directory **MUST** conform to the 7-section MADR schema defined in `GL-0006`. Once merged with status `ACCEPTED`, an ADR **SHALL NOT** be rewritten; architectural changes require authoring a new sequential ADR that formally supersedes the prior record.

---

## 2. Registered Architecture Decision Records

| ADR ID | Decision Title | Subsystem Scope | Status | Date Accepted |
| :--- | :--- | :--- | :--- | :--- |
| **[ADR-0001](0001-adopt-jhipster-for-server-backend.md)** | Adopt Spring Boot 3 (JHipster Core) & Java 21 for Server Orchestration Broker | `server` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0002](0002-adopt-javafx-for-studio-ui.md)** | Adopt OpenJFX 21 (JavaFX) & AtlantaFX for Legatus Studio Desktop Cockpit | `studio` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0003](0003-adopt-release-please-for-monorepo-versioning.md)** | Adopt Conventional Commits & Google Release Please for Monorepo Versioning | `ci` / `repo-wide` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0004](0004-adopt-solix-for-worker-runner.md)** | Adopt Solix Virtual Machine for Deterministic Guest Task Execution | `worker` / `runner` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0005](0005-node-enrollment-and-attestation.md)** | Quarantine Node Enrollment, Visual Attestation & Ephemeral Ticket Exchange | `worker` / `server` / `studio` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0006](0006-worker-reconnect-and-polling-cadence.md)** | Exponential Jittered Backoff, Heartbeat Dead-Man's Switch & Session Reconciliation | `protocols/worker-wire` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0007](0007-adopt-nng-for-local-daemon-ipc.md)** | Adopt `nng` (nanomsg-next-gen) for Cross-Platform Local CLI-Daemon IPC | `worker` / `cli` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0008](0008-adopt-reproc-for-worker-task-isolation.md)** | Adopt `reproc++` for Worker Child Process Isolation & Two-Stage Kill Escalation | `worker` / `runner` | `ACCEPTED` | 2026-10-14 |
| **[ADR-0009](0009-worker-sandboxed-standard-library.md)** | Sandboxed Standard Library Architecture & Pure In-Memory Security Model | `worker` / `runner` / `server` | `ACCEPTED` | 2026-10-15 |
| **[ADR-0010](0010-server-centric-worker-control-plane.md)** | Server-Centric Worker Control Plane, Dynamic Configuration & Threshold Auto-Balancing | `worker` / `server` / `protocols/worker-wire` | `ACCEPTED` | 2026-10-16 |


