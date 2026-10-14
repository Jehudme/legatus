# Legatus Architecture Documentation Suite

## Document Metadata
* **Scope:** Architectural Blueprints & System Topologies (`docs/architecture/`)
* **Status:** Final Architectural Specification
* **Target Audience:** Systems Architects, Core Contributors, Security Engineers, Platform Operators
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Governing Guidelines:** [`docs/dev/guidelines/README.md`](../dev/guidelines/README.md) (`GL-0000`), [`docs/dev/guidelines/authoring-docs.md`](../dev/guidelines/authoring-docs.md) (`GL-0010`)

---

## 1. Executive Summary & Purpose

The `docs/architecture/` directory contains the authoritative high-level architectural blueprints, cross-subsystem interaction topologies, formal sequence diagrams, and visual wireframes for the **Legatus** distributed serverless compute runtime.

While Subsystem Specifications ([`docs/specs/`](../specs/)) define individual component contracts and Developer Guidelines ([`docs/dev/guidelines/`](../dev/guidelines/)) govern engineering procedures, the Architecture Suite establishes the overarching system topology, end-to-end data pipelines, multi-process fault isolation boundaries, and temporal sequence invariants across all distributed nodes.

---

## 2. Architecture Manifest & Navigation Map

```text
docs/architecture/
├── README.md                      # Master architecture directory index (This file)
├── overview.md                    # Cluster-wide architectural topology, binary manifest & security model
├── sequences/                     # Formal UML Sequence Specifications (Mermaid)
│   ├── README.md                  # Sequence catalog and normative diagramming rules
│   ├── server-operations.md       # Master key bootstrap, Liquibase migrations, dead-man sweeper
│   ├── task-operations.md         # Stdin envelope injection, Solix VM metering, 2-stage abort
│   ├── telemetry-operations.md    # Viewport subscription scoping, 60Hz UI frame coalescing
│   ├── user-operations.md         # Local nng CLI IPC, Studio visual attestation, bytecode deploy
│   └── worker-operations.md       # First boot, LCTP handshake, session reconciliation, node drain
└── wireframes/                    # UI/UX Specifications & Cockpit Visual Layouts
    ├── README.md                  # Wireframes catalog
    └── studio/                    # Legatus Studio UI wireframe workspace (in progress)
```

---

## 3. Normative Authority Hierarchy

In accordance with [`docs/dev/guidelines/README.md`](../dev/guidelines/README.md) (`GL-0000`), architectural documentation is situated within the following normative authority hierarchy:

$$
\text{Code Implementation} \prec \text{Developer Guides} \prec \text{Architecture Decisions (ADRs)} \prec \text{Subsystem Specs} \prec \text{Developer Guidelines}
$$

* Architectural sequence diagrams and topologies **MUST** strictly mirror the state machines and envelope schemas defined in [`docs/specs/`](../specs/).
* Any proposed architectural divergence between documentation and code **MUST** be resolved by either updating the code to match the architectural standard, or by authoring a formal Architecture Decision Record ([`docs/dev/decisions/`](../dev/decisions/)) approved by lead architects.

---

## 4. Architectural Verification & Governance

All architectural documents in this tree are subjected to automated CI verification gates:
1. **Zero Multi-Line Implementation Code (`GL-0005`):** Architectural documents must describe topologies, sequence flows, mathematical equations, and interfaces, with zero raw C++ or Java implementation code.
2. **Mermaid Syntax Validation:** All Mermaid code blocks (`sequenceDiagram`, `flowchart`, `graph`) are parsed in CI to guarantee zero rendering syntax errors.
3. **Cross-Platform Parity:** Architectural workflows must document deterministic behavior across Linux, macOS, and Windows.
