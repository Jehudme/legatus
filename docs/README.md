# Legatus Documentation Portal

## Document Metadata
* **Scope:** Master Documentation Index & Subsystem Navigation (`docs/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Distributed Systems Architects, Operations Personnel
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Governing Guidelines:** [`docs/dev/guidelines/README.md`](dev/guidelines/README.md) (`GL-0000`), [`docs/dev/guidelines/authoring-docs.md`](dev/guidelines/authoring-docs.md) (`GL-0010`)

---

## 1. Executive Summary & Architecture Overview

Welcome to the **Legatus** engineering documentation suite. Legatus is an ultra-high-density distributed serverless runtime platform featuring:
* **`legatusd` & `legatus`:** C++20 worker daemon supervisor and local operator CLI, leveraging `nng` local IPC, `reproc++` child process isolation, and outbound TLS 1.3 WebSockets.
* **`legatus-runner`:** Ephemeral C++20 guest execution sandbox embedding the upstream deterministic **Solix Virtual Machine** (`libsolix_vm.a`) with Automatic Reference Counting (ARC) memory and discrete instruction budgeting.
* **`legatus-server`:** Central orchestration broker in Java 21 LTS / Spring Boot 3 running on Project Loom Virtual Threads, backed by an embedded page-encrypted H2 database (`CIPHER=AES`).
* **`legatus-studio`:** Cross-platform native desktop administration cockpit built on Java 21, OpenJFX 21, and AtlantaFX, featuring 60Hz telemetry frame coalescing and visual cryptographic node attestation.

Every document in this directory adheres to strict enterprise open-source invariants: zero multi-line implementation code in specifications (`GL-0005`), machine-readable Conventional Commits (`GL-0001`), and zero-drift synchronization between code and documentation (`GL-0010`).

---

## 2. Normative Authority Hierarchy

In the event of an ambiguity or conflict between different artifacts in this repository, contributors and reviewers **MUST** enforce the following binding authority hierarchy established in [`GL-0000`](dev/guidelines/README.md):

$$
\text{Code Implementation} \prec \text{Developer Guides} \prec \text{Architecture Decisions (ADRs)} \prec \text{Subsystem Specs} \prec \text{Developer Guidelines}
$$

* **Developer Guidelines (`docs/dev/guidelines/`):** Highest normative authority. Governs engineering invariants, review gates, and formatting standards.
* **Subsystem Specifications (`docs/specs/`):** Authoritative behavioral contracts, finite state machines, memory bounds, and wire envelopes. Code that deviates from a specification is non-compliant and must be fixed.
* **Architecture Decision Records (`docs/dev/decisions/`):** Immutable historical log explaining *why* technical options were selected and their trade-offs.
* **Developer & Operator Guides (`docs/guides/`):** Operational walk-throughs and procedures, verified continuously against compiled binaries.
* **Source Code (`worker/`, `server/`, `studio/`):** Subordinate executable realization of the contracts defined in specifications.

---

## 3. Master Documentation Directory Manifest

```text
docs/
├── README.md                      # Master documentation portal (This file)
├── architecture/                  # High-level system topology, sequence flows, and wireframes
│   ├── README.md                  # Architecture suite index
│   ├── overview.md                # Cluster-wide architectural topology and invariants
│   └── sequences/                 # UML Sequence Specifications (Mermaid)
│       ├── README.md              # Sequence catalog and diagramming standard
│       ├── server-operations.md   # Master key derivation, H2 storage, dead-man sweeper
│       ├── task-operations.md     # Stdin execution streaming, Solix metering, 2-stage abort
│       ├── telemetry-operations.md# Viewport subscription scoping, 60Hz UI frame batching
│       ├── user-operations.md     # Local nng CLI IPC, Studio visual attestation
│       └── worker-operations.md   # First boot, LCTP handshake, session reconciliation, drain
├── dev/                           # Internal engineering governance and developer standards
│   ├── decisions/                 # Architecture Decision Records (ADRs, MADR schema)
│   │   ├── README.md              # ADR index and immutability governance
│   │   └── 0001–0008              # Technology selections (Spring Boot, JavaFX, Solix, etc.)
│   ├── guidelines/                # The 12-Document Master Engineering Guidelines Suite
│   │   ├── README.md              # Master guidelines index (GL-0000)
│   │   └── GL-0001 to GL-0011     # Commits, branching, workflows, specs, testing, etc.
│   └── milestones/                # Phased delivery governance and definition of done (DoD)
│       ├── README.md              # Milestone roadmap index
│       └── 001 to 004             # Foundation, Server MVP, Worker MVP, Studio MVP
├── guides/                        # Operator and developer operational guides
│   ├── README.md                  # Guides catalog and verification invariants
│   ├── 01-getting-started.md      # Multi-binary local cluster quickstart
│   ├── 02-server.md               # Server deployment, configuration, and TLS setup
│   ├── 04-studio.md               # Studio cockpit operator manual and visual attestation
│   └── 05-worker.md               # Worker node installation, worker.toml, and CLI reference
└── specs/                         # Core Subsystem Specifications & Wire Protocols
    ├── README.md                  # Specifications catalog
    ├── server-spec.md             # Server subsystem specification (Loom, H2, Scheduler)
    ├── studio-spec.md             # Studio subsystem specification (OpenJFX, AtlantaFX, 60Hz)
    ├── worker-spec.md             # Worker subsystem specification (reproc++, nng, Solix)
    └── protocols/                 # Layer 7 Wire Protocol Specifications
        ├── api-endpoint.md        # REST API Gateway specification (HTTPS / TLS 1.3)
        ├── studio-wire.md         # Studio Telemetry Stream protocol (LSTCP over WSS)
        └── worker-wire.md         # Worker Cluster Transport Protocol (LCTP over WSS)
```

---

## 4. How to Navigate and Contribute

1. **New to Legatus?** Begin with [`docs/architecture/overview.md`](architecture/overview.md) for the high-level system overview, followed by [`docs/guides/01-getting-started.md`](guides/01-getting-started.md) to launch a local cluster.
2. **Implementing Features?** Review the corresponding subsystem specification in [`docs/specs/`](specs/) and the polyglot coding standards in [`docs/dev/guidelines/language-conventions.md`](dev/guidelines/language-conventions.md) (`GL-0011`).
3. **Submitting Commits & PRs?** All commits must strictly conform to Conventional Commits v1.0.0 in [`docs/dev/guidelines/authoring-commits.md`](dev/guidelines/authoring-commits.md) (`GL-0001`) and pass all verification gates defined in [`docs/dev/guidelines/authoring-workflows.md`](dev/guidelines/authoring-workflows.md) (`GL-0003`).
