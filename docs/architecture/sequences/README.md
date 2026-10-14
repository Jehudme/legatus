# Legatus Architectural Sequences Specification Index

## Document Metadata
* **Scope:** Architectural Sequence Specifications (`docs/architecture/sequences/`)
* **Status:** Final Architectural Specification
* **Target Audience:** Systems Architects, Protocol Engineers, Platform Contributors
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Governing Guidelines:** [`docs/dev/guidelines/authoring-docs.md`](../../dev/guidelines/authoring-docs.md) (`GL-0010`), [`docs/dev/guidelines/authoring-specs.md`](../../dev/guidelines/authoring-specs.md) (`GL-0005`)

---

## 1. Overview & Architectural Role

This directory contains the formal UML sequence specifications governing all distributed interactions across the Legatus ecosystem. In accordance with the **Normative Authority Hierarchy (`GL-0000`)**, these sequence specifications define the authoritative temporal order, state transitions, wire envelopes, and error handling behaviors across subsystem boundaries.

Every sequence specification in this directory adheres to the **Specification vs. Code Separation Invariant (`GL-0005`)**: interactions are expressed exclusively through formal Mermaid sequence diagrams, state transition matrices, and concrete data contracts, with zero raw C++ or Java implementation code.

---

## 2. Sequence Specifications Catalog

| Specification File | Domain Scope | Primary Subsystems Involved | Core Workflows Specified |
| :--- | :--- | :--- | :--- |
| **[`server-operations.md`](server-operations.md)** | Server Internal Operations & Brokerage | `legatus-server`, Embedded H2, Project Loom Bus | Master key bootstrapping, Liquibase migration, worker quarantine queueing, Argon2id hashing, ephemeral ticket burn-on-read, dead-man's switch sweeper (15s timeout). |
| **[`task-operations.md`](task-operations.md)** | Task Execution, Metering & Settlement | `legatus-server`, `legatusd`, `legatus-runner`, Solix VM | REST task ingestion, capacity bin-packing (`LEAST_LOADED`), anonymous `stdin` envelope streaming, Solix instruction metering, ledger acknowledgment (`TASK_RESULT_ACK`), two-stage abort escalation (10s grace period). |
| **[`telemetry-operations.md`](telemetry-operations.md)** | Telemetry Streaming & Studio Coalescing | `legatus-server`, `legatusd`, `legatus-studio` | WSS telemetry push, server-side `viewport_filter` subscription scoping, 1000ms aggregate metric ticks, client-side 60Hz UI frame coalescing ($16.6\text{ ms}$), 10,000-line / 8 MB task log ring buffers. |
| **[`user-operations.md`](user-operations.md)** | Administrative & User Operations | `legatus` CLI, `legatus-studio`, `legatusd` | Local `nng` Request/Reply (`REQ0`/`REP0`) IPC over Unix Domain Sockets and Windows Named Pipes, Studio visual 3-word quarantine attestation (`Tiger-Galaxy-77`), Solix bytecode deployment and catalog deduplication. |
| **[`worker-operations.md`](worker-operations.md)** | Worker Daemon Lifecycle & Recovery | `legatusd`, `legatus-server`, `legatus-runner` | First-boot Ed25519 keypair generation, quarantine status polling loop ($5000\text{ ms} \pm 500\text{ ms}$ jitter), LCTP handshake, in-flight task session reconciliation (`reconcile_tasks`), heartbeat dead-man switch, expedited `DRAIN_NOTICE`. |

---

## 3. Normative Diagramming Standards

All contributors adding or amending sequence specifications within this directory **MUST** conform to the following standards:

1. **Mermaid Syntax Conformance:** Diagrams **MUST** use standard `sequenceDiagram` syntax with `autonumber` enabled to facilitate line-by-line referencing in technical reviews.
2. **Deterministic Protocol Framing:** Message arrows **MUST** specify explicit frame types matching [`docs/specs/protocols/worker-wire.md`](../../specs/protocols/worker-wire.md), [`docs/specs/protocols/studio-wire.md`](../../specs/protocols/studio-wire.md), or [`docs/specs/protocols/api-endpoint.md`](../../specs/protocols/api-endpoint.md).
3. **Explicit Concurrency Demarcation:** Parallel broadcasts, background threads, and asynchronous offloading **MUST** be explicitly modeled using `par` blocks or explicit thread participant lifelines.
4. **Boundary Security:** Diagrams **MUST** annotate credential lifetimes (e.g., 30s ticket TTL), hashing bounds (e.g., Argon2id 4-thread pool), and cryptographic scrubbing operations (`explicit_bzero`).
