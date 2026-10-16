# Legatus Engineering Milestones Index

## Document Metadata
* **Scope:** Phased Engineering Delivery & Release Governance (`docs/dev/milestones/`)
* **Governing Guideline:** [`docs/dev/guidelines/authoring-milestones.md`](../guidelines/authoring-milestones.md) (`GL-0008`)
* **Status:** Active Delivery Roadmap
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Overview & Governance Invariants

This directory tracks all phased engineering milestones for the Legatus distributed compute platform. In accordance with **Milestone Planning & Delivery Specification (`GL-0008`)**:

1. Every task in a milestone **MUST** be classified using the four-tier blocker taxonomy (`P0` Critical Blocker, `P1` High Priority, `P2` Medium Priority, `P3` Low Priority).
2. A milestone **SHALL NOT** transition to `CLOSED` or trigger a `release-please` production tag while any `P0` or `P1` deliverable remains incomplete.
3. Every milestone **MUST** satisfy all five pillars of the **Definition of Done (DoD)** prior to closure: code merged to `master`, 100% Catch2/JUnit dual test pass (`GL-0009`), zero ASan/UBSan memory or UB warnings, zero-drift documentation synchronization (`GL-0010`), and accepted ADRs (`GL-0006`).

---

## 2. Milestone Delivery Roadmap & Status Matrix

| Milestone ID | Document Link | Target Version | Primary Scope | Status | Target Date |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MS-001** | [`001-plan-documentation.md`](001-plan-documentation.md) | `v0.1.0-docs-foundation` | Guidelines (`GL-0000`–`GL-0011`), ADRs (`0001`–`0008`), Subsystem & Wire Specs | `CLOSED` | 2026-10-14 |
| **MS-002** | [`002-server-mvp-implementation.md`](002-server-mvp-implementation.md) | `v1.0.0-server-mvp` | `legatus-server` (Java 21, Loom, LCTP/LSTCP Brokers, Scheduler, Encrypted H2) | `CLOSED` | 2026-11-01 |
| **MS-003** | [`003-worker-mvp-implementation.md`](003-worker-mvp-implementation.md) | `v1.0.0-worker-mvp` | `legatusd`, `legatus` CLI, `legatus-runner` (C++20, `nng`, `reproc++`, Solix VM) | `CLOSED` | 2026-11-15 |
| **MS-004** | [`004-studio-mvp-documentation.md`](004-studio-mvp-documentation.md) | `v1.0.0-studio-mvp` | `legatus-studio` (OpenJFX 21, AtlantaFX, 60Hz Telemetry) & Operational Guides | `CLOSED` | 2026-12-01 |
| **MS-005** | [`005-platform-feature-completeness.md`](005-platform-feature-completeness.md) | `v1.1.0-parity` | Platform Parity (Stdout Streaming, Reconnect Reconciliation, Command Palette, Audit Export) | `ACTIVE` | 2026-12-31 |
| **MS-006** | [`006-worker-sandboxed-standard-library.md`](006-worker-sandboxed-standard-library.md) | `v1.2.0-stdlib` | Worker Sandboxed Standard Library (Safe Solixlib, Zero-Disk Sandbox, WebRequest, JSON, TaskContext) | `ACTIVE` | 2027-01-31 |
| **MS-007** | [`007-server-centric-worker-control-and-balancing.md`](007-server-centric-worker-control-and-balancing.md) | `v1.3.0-control-plane` | Server-Centric Worker Control Plane (Dynamic Config Sync, Auto-Balancer Thresholds, Live CPU Sync) | `ACTIVE` | 2027-02-15 |
| **MS-008** | [`008-studio-cockpit-fleet-and-quarantine-overhaul.md`](008-studio-cockpit-fleet-and-quarantine-overhaul.md) | `v1.4.0-cockpit-fleet` | Studio Cockpit UI Overhaul (Worker Overview Grid, Rich Fleet Table, Dynamic Inspector, Quarantine Auto-Accept) | `ACTIVE` | 2027-02-28 |
| **MS-009** | [`009-studio-function-workbench-and-execution-profiles.md`](009-studio-function-workbench-and-execution-profiles.md) | `v1.5.0-workbench-profiles` | Studio Function Catalog & Dedicated Execution Workbench (Profiles, Tagging, Unlimited Quotas, Failure Alerts) | `ACTIVE` | 2027-03-15 |
| **MS-010** | [`010-task-time-ledger-and-admin-security-overhaul.md`](010-task-time-ledger-and-admin-security-overhaul.md) | `v1.6.0-security-sync` | Task Time Ledger, User Administration Security Overhaul & Universal WebSocket Sync | `ACTIVE` | 2027-03-31 |
| **MS-011** | [`011-cockpit-ux-and-live-sync-overhaul.md`](011-cockpit-ux-and-live-sync-overhaul.md) | `v1.7.0-cockpit-sync` | Cockpit UX, Live Sync, and Runtime Telemetry Overhaul | `ACTIVE` | 2027-04-15 |
