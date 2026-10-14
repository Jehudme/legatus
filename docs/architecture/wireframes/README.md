# Legatus Wireframes & UI Specifications Index

## Document Metadata
* **Scope:** UI/UX Architecture & Cockpit Wireframe Catalog (`docs/architecture/wireframes/`)
* **Status:** Final Architectural Specification
* **Target Audience:** Desktop UI Engineers, UI/UX Designers, Platform Contributors
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Governing Guidelines:** [`docs/dev/guidelines/authoring-docs.md`](../../dev/guidelines/authoring-docs.md) (`GL-0010`)

---

## 1. Overview & UI Architecture Role

The `docs/architecture/wireframes/` directory provides visual and structural specifications for all user-facing graphical interfaces within the Legatus ecosystem, primarily focusing on **Legatus Studio** (`legatus-studio`).

The UI architecture adheres to the following principles:
1. **Separation of Concerns:** UI layout and visual aesthetics are decoupled from business logic via Model-View-ViewModel (MVVM) and JavaFX Property bindings (`GL-0011`).
2. **Deterministic UI State:** Wireframes explicitly define visual states for every possible node status (`ONLINE`, `DRAINING`, `OFFLINE`, `QUARANTINED`, `REVOKED`) and task state (`QUEUED`, `DISPATCHED`, `EXECUTING`, `COMPLETED`, `ABORTING`, `FAILED`).
3. **Ergonomic Operator Safety:** High-consequence actions (e.g., node approval, task kill) require explicit multi-step confirmation or visual attestation codes.

---

## 2. Wireframe Specifications Manifest

| Specification Link | Component Target | Description | Key Views Specified |
| :--- | :--- | :--- | :--- |
| **[`studio/studio-wireframe.md`](studio/studio-wireframe.md)** | `legatus-studio` | Complete 4-zone persistent cockpit layout and view specifications. | Telemetry ribbon, fleet heatmaps, quarantine attestation modal (`Tiger-Galaxy-77`), function workbench, task console drawer. |

---

## 3. Visual Assets

* [`studio/studio-wireframe.png`](studio/studio-wireframe.png): High-resolution visual mockup of the Legatus Studio administrative console in dark theme.
