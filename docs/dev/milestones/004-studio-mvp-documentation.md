# Milestone 004: Legatus Studio Desktop Cockpit MVP & End-to-End Operational Guides

## Document Metadata
* **Milestone ID:** MS-004
* **Target Version:** `v1.0.0-studio-mvp`
* **Status:** ACTIVE
* **Lead Architect:** @studio-lead
* **Target Delivery Date:** 2026-12-01
* **Related ADRs:** ADR-0002, ADR-0003, ADR-0005

---

## 1. Executive Summary & Objectives

Deliver the native cross-platform **Legatus Studio** (`legatus-studio`) desktop administration cockpit built on Java 21 LTS, OpenJFX 21, and the AtlantaFX dark-mode design system (`ADR-0002`), alongside the complete, compiler-verified end-to-end operator documentation suite (`docs/guides/`). Studio provides real-time 60Hz cluster telemetry visualization over LSTCP (`studio-wire.md`), side-by-side visual cryptographic attestation for quarantined workers (`Tiger-Galaxy-77`), Solix bytecode upload and dispatch workbenches, bounded 10,000-line live task `stdout`/`stderr` consoles, and one-click two-stage task cancellation controls.

---

## 2. Scope & Subsystems Affected

* `studio/src/main/java/dev/legatus/studio/transport/`: Project Loom Virtual Thread REST & LSTCP WebSocket client (`java.net.http.HttpClient`), 60Hz (`16.6ms`) lock-free telemetry coalescing ring buffer, and exponential jittered reconnect loop.
* `studio/src/main/java/dev/legatus/studio/ui/`: Four-zone cockpit scene graph (Left Navigation Rail, Top Telemetry Ribbon, Center Primary Workspace, Bottom Collapsible Log/Audit Drawer) styled with AtlantaFX (`PrimerDark` / `NordDark`) and Ikonli vector icons.
* `studio/src/main/java/dev/legatus/studio/security/`: OS Keychain integration (`libsecret` on Linux, `Keychain` on macOS, `DPAPI` on Windows) for AES-256-GCM encrypted operator JWT and profile storage.
* `docs/guides/`: Complete end-to-end operational guides (`01-getting-started.md`, `02-server.md`, `04-studio.md`, `05-worker.md`) verified against compiled binaries (`GL-0010`).

---

## 3. Prioritized Deliverables & Task Matrix

### 3.0 P0: Foundation, Dependencies & Vector Assets (Completed)
- [x] `[P0]` **Modular OpenJFX 21 & AtlantaFX 2.0.1 Foundation:** Configure Java 21 LTS release target, OpenJFX 21.0.5 controls/fxml/graphics/base, AtlantaFX 2.0.1, Ikonli 12.3.1, Jackson 2.17.2, and TestFX 4.0.18. (Issue #400)
- [x] `[P0]` **Dynamic ViewLoaderService & ThemeService:** Provide decoupled FXML loader (`ViewLoaderService`) supporting classpath resolution and dynamic container view switching, and live AtlantaFX theme switcher (`ThemeService`). (Issue #400)
- [x] `[P0]` **Vector Icon Suite:** Author 18 high-fidelity SVG icon assets for OS badges, cluster telemetry gauges, status dots, and cryptographic attestation cards. (Issue #400)
- [x] `[P0]` **Observable Domain Models & Business Services Layer:** Implemented 8 observable domain models (`ClusterMetrics`, `WorkerNode`, `QuarantineEnrollment`, `TaskRecord`, `BytecodeItem`, `SecretItem`, `OperatorAccount`, `AuditLogEntry`) and 10 business services (`AuthService`, `WorkerService`, `TaskService`, `BytecodeService`, `SecretsService`, `UserManagementService`, `AuditLogService`, `TelemetryService`, `NotificationService`, `DialogService`). (Issue #400)
- [x] `[P0]` **Decoupled MVVM ViewModels Layer:** Implemented 14 headlessly verified ViewModels (`MainViewModel`, `LoginViewModel`, `RibbonViewModel`, `NavigationViewModel`, `FleetViewModel`, `QuarantineViewModel`, `FunctionsViewModel`, `TasksViewModel`, `SecretsViewModel`, `UserManagementViewModel`, `AuditTrailViewModel`, `SettingsViewModel`, `DrawerViewModel`, `ClusterOverviewViewModel`). (Issue #400)

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Virtual Thread Transport & 60Hz Telemetry Coalescer (`GL-0011`):** Isolate all HTTP/2 REST calls and `/ws/studio` WebSocket frame deserialization onto Java 21 Virtual Threads, buffering incoming events and marshaling UI property updates via `Platform.runLater()` at most once every `16.6ms` (60Hz) so the JavaFX Application Thread never blocks or stutters. (Issue #401)
- [ ] `[P0]` **Quarantine Visual Attestation Modal (`ADR-0005`):** Implement real-time `EVENT_ENROLLMENT_REQUESTED` toast notifications and the side-by-side attestation modal displaying node hardware specs, colon-separated SHA-256 hex fingerprint (`4E:8A:BC:31:09:FF:7D:12`), and highlighted 3-word visual confirmation code (`Tiger-Galaxy-77`) with **[Approve & Issue Lease]** and **[Reject & Block IP]** actions. (Issue #402)
- [x] `[P0]` **Bounded 10,000-Line Task Log Ring Buffer:** Implement `SUBSCRIBE_TASK_LOGS` / `UNSUBSCRIBE_TASK_LOGS` streaming console tabs backed by a fixed-capacity circular buffer capped at 10,000 lines or $8\text{ MB}$ of UTF-8 text to prevent desktop OOM on verbose guest tasks (`studio-spec.md` Section 5.2). (Issue #403)
- [x] `[P0]` **OS Keychain Credential Encryption:** Encrypt saved operator refresh tokens and cluster connection profiles in `studio.json` using AES-256-GCM backed by host OS native credential stores (`libsecret`, macOS `Keychain`, Windows `DPAPI`) with zero plaintext token storage on disk. (Issue #404)

### 3.2 P1: High Priority (Milestone Core)
- [ ] `[P1]` **Fleet Topology View & Viewport Subscription Filtering:** Implement live worker fleet tables, CPU/RAM utilization heatmaps, remote **[Drain]** and **[Revoke]** controls, and `viewport_filter` scoping in `STUDIO_SESSION_INIT` (`studio-wire.md` Section 3.1). (Issue #405)
- [ ] `[P1]` **Task & Function Workbench (Bytecode Upload & Two-Stage Abort):** Implement Solix `.slxb` bytecode file upload with automatic SHA-256 calculation, task dispatch form, live instruction/memory progress gauges, and **[Abort Task]** button with 10-second Stage 1 grace period countdown. (Issue #406)
- [ ] `[P1]` **End-to-End Living Operational Guides (`docs/guides/`):** Author and verify `01-getting-started.md`, `02-server.md`, `04-studio.md`, and `05-worker.md` against actual `CLI11` flags, `server.yml` properties, and REST DTOs (`GL-0010`). (Issue #407)
- [ ] `[P1]` **Cross-Platform Native Packaging (`jpackage`):** Configure Maven `jpackage` profiles to build self-contained native desktop installers (`.deb`, `.rpm`, `.dmg`, `.msi`) with a stripped modular JRE image. (Issue #408)

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [ ] `[P2]` **Secrets Vault Management View:** Implement write-only AES-256-GCM secret key creation, rotation, and deletion UI in Studio (`secrets_vault` table). (Issue #409)
- [ ] `[P2]` **AtlantaFX Theme Switcher & Keyboard Shortcuts:** Support instant toggling between `PrimerDark` and `NordDark` themes and keyboard navigation shortcuts (`Ctrl+K` command palette). (Issue #410)

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [ ] `[P3]` **Exportable Cluster Audit Log CSV/JSON:** Allow operators to export filtered security audit log records (`audit_logs`) from the bottom drawer to local JSON or CSV files. (Issue #411)

---

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | 100% pass on full-cluster E2E tests (`legatusd` + `legatus-server` + `legatus-studio`) | `ctest --output-on-failure` | [ ] Pending |
| **JUnit 5 & TestFX Suite** | 100% pass across headless Monocle `TestFX` UI suites and ViewModel unit tests (`GL-0009`) | `mvn clean verify -Pprod,headless-test` | [ ] Pending |
| **Static Analysis Gate** | Zero SpotBugs high-confidence defects and zero Checkstyle errors (`GL-0011`) | `mvn checkstyle:check spotbugs:check` | [ ] Pending |
| **Documentation** | All 4 operator guides (`docs/guides/`) pass automated CLI/API drift verification (`GL-0010`) | `scripts/verify-cli-docs.sh` | [ ] Pending |
| **ADR Governance** | `ADR-0002` and `ADR-0005` compliance verified across UI and transport layers | Maintainer audit (`GL-0006`) | [ ] Pending |

---

## 5. Risk Assessment & Mitigation Strategies

* **Risk 1: JavaFX Application Thread Freeze During High-Throughput Cluster Event Storms**
  * *Mitigation:* Enforce the `16.6ms` (60Hz) lock-free coalescing ring buffer (`TelemetryCoalescer`) and `viewport_filter` subscription scoping (`studio-spec.md` Sections 1.1 & 5.2); verify in headless `TestFX` stress tests injecting 10,000 simulated `EVENT_TASK_LIFECYCLE` frames per second.
* **Risk 2: Headless CI Failures on OpenJFX Scene Graph Tests**
  * *Mitigation:* Standardize on `TestFX` with the `Monocle` headless glass platform in GitHub Actions across Linux, macOS, and Windows runners.
