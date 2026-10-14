# Legatus Studio Subsystem Specification

## Document Metadata
* **Subsystem:** `studio` (JavaFX Operator Administration & Live Telemetry Cockpit)
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Desktop Application Engineers, UI/UX Architects, Systems Operators
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Host Runtime:** OpenJDK / Eclipse Temurin Java 21 LTS + OpenJFX 21
* **Related Specifications:**
  * `docs/specs/server-spec.md`
  * `docs/specs/worker-spec.md`
  * `docs/specs/protocols/studio-wire.md`
  * `docs/specs/protocols/api-endpoint.md`
  * `docs/dev/decisions/0002-adopt-javafx-for-studio-ui.md`
  * `docs/dev/guidelines/authoring-specs.md` (GL-0005)
  * `docs/dev/guidelines/language-conventions.md` (GL-0011)

---

## 1. System Architecture & Binary Topology

**Legatus Studio** (`legatus-studio`) is a native, cross-platform desktop administration console built on Java 21 and OpenJFX 21. It provides cluster operators with real-time fleet observability, visual cryptographic attestation for quarantined worker nodes, Solix bytecode deployment management, live task execution log streaming, and emergency task cancellation controls.

```
+---------------------------------------------------------------------------------------------------+
|                                 LEGATUS STUDIO (Java 21 / OpenJFX)                                |
|                                                                                                   |
|  +---------------------------+  +---------------------------+  +-------------------------------+  |
|  |   Fleet Topology View     |  | Quarantine Approval Modal |  |  Task & Function Workbench    |  |
|  | - Live CPU/RAM Heatmaps   |  | - Ed25519 Fingerprint     |  | - Bytecode Upload & SHA-256   |  |
|  | - Drain / Revoke Controls |  | - 3-Word Visual Code      |  | - Live Stdout Stream Console  |  |
|  | - Active Runner Tables    |  | - One-Click Attestation   |  | - 2-Stage Task Kill Button    |  |
|  +-------------+-------------+  +-------------+-------------+  +---------------+---------------+  |
|                |                              |                                |                  |
|                +------------------------------+--------------------------------+                  |
|                                               |                                                   |
|                                               v                                                   |
|                +---------------------------------------------------------------+                  |
|                |        Reactive JavaFX Property Store (UI Thread-Safe)        |                  |
|                +------------------------------+--------------------------------+                  |
|                                               | Platform.runLater() Batching                      |
|                                               | (60Hz Frame Coalescing)                           |
|                +------------------------------+--------------------------------+                  |
|                |     Virtual Thread Transport Multiplexer (Project Loom)       |                  |
|                +-------------------+---------------------------------------+---+                  |
|                                    |                                       |                      |
+------------------------------------|---------------------------------------|----------------------+
                                     |                                       |
               HTTPS REST (TLS 1.3)  |                                       |  WSS Stream (TLS 1.3)
             (/api/v1/admin/*, JWT)  |                                       |  (/ws/studio?ticket=...)
                                     v                                       v
                         +-------------------------------------------------------+
                         |                    LEGATUS SERVER                     |
                         +-------------------------------------------------------+
```

### 1.1 Architectural Invariants
1. **Strict JavaFX Application Thread Isolation:** Zero network I/O, JSON deserialization, cryptographic hashing, or disk operations **SHALL** execute on the JavaFX Application Thread. All background operations **MUST** run on Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), marshaling UI mutations exclusively via `Platform.runLater()`.
2. **60Hz Telemetry Coalescing:** High-frequency telemetry events arriving over the Studio WebSocket stream **MUST** be buffered in lock-free ring buffers and coalesced into a single UI property batch update at most once every **16.6 milliseconds** ($60\text{ Hz}$) to prevent UI thread starvation during burst cluster activity.
3. **Viewport Subscription Scoping:** To prevent client CPU saturation and memory bloat on large clusters (> 100 workers, thousands of active tasks), Studio **MUST** submit viewport filtering criteria (`viewport_filter`) in `STUDIO_SESSION_INIT`. The broker restricts high-frequency `EVENT_WORKER_STATE_CHANGED` frames strictly to visible or selected nodes, while global sparklines are driven by the 1000ms `EVENT_CLUSTER_METRIC_TICK`.
4. **Zero Plaintext Credential Storage:** Operator JWT refresh tokens and saved cluster connection profiles **MUST** be encrypted using AES-256-GCM backed by the host OS Keychain (`libsecret` on Linux, `Keychain` on macOS, `DPAPI` on Windows) before writing to local disk.
5. **Immutable Wire & State Records:** All incoming WebSocket frames and REST responses **MUST** deserialize into immutable Java 21 `record` types implementing `sealed` interfaces.

---

## 2. Technology Stack & Component Responsibilities

| Component Role | Selected Dependency | Minimum Version | Functional Mandate |
| :--- | :--- | :--- | :--- |
| **UI Toolkit** | `OpenJFX` (`javafx-controls`, `fxml`) | `v21.0+` | Hardware-accelerated desktop rendering, observable properties, and scene graph layout. |
| **Design System & Theme** | `AtlantaFX` | `v2.0+` | Modern dark/light high-contrast themes (`PrimerDark`, `NordDark`) tailored for telemetry cockpits. |
| **Vector Iconography** | `Ikonli` (`ikonli-javafx`, `feather`) | `v12.3+` | Resolution-independent vector icons for node states, alerts, and action toolbars. |
| **HTTP & WebSocket Client**| `java.net.http.HttpClient` | `Java 21 Built-in`| Asynchronous TLS 1.3 REST calls and full-duplex WebSocket streaming over Virtual Threads. |
| **JSON Serialization** | `Jackson` (`jackson-databind`) | `v2.17+` | Binds REST payloads and Studio Wire frames directly to Java 21 `record` types. |
| **Native Packaging** | `jpackage` / `Moditect` | `Java 21 Built-in`| Bundles self-contained native installers (`.deb`, `.rpm`, `.dmg`, `.msi`) with an embedded JRE. |
| **UI & Unit Testing** | `JUnit 5` & `TestFX` | `v4.0+` | Headless Monocle UI testing and view-model state verification. |

---

## 3. Filesystem & Operating System Boundaries

Legatus Studio resolves local operator configuration, workspace profiles, and log directories dynamically across operating systems:

| Resource Role | Linux / POSIX Standard | macOS Standard | Windows Standard | Override Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **Profile & Settings** (`studio.json`) | `~/.config/legatus/studio.json` | `~/Library/Application Support/Legatus/studio.json` | `%APPDATA%\Legatus\studio.json` | `--config <path>` CLI argument |
| **Known Hosts & Certs** | `~/.config/legatus/certs/` | `~/Library/Application Support/Legatus/certs/` | `%APPDATA%\Legatus\certs\` | UI Certificate Trust Dialog |
| **Application Logs** (`studio.log`) | `~/.local/state/legatus/studio.log` | `~/Library/Logs/Legatus/studio.log` | `%LOCALAPPDATA%\Legatus\logs\studio.log` | `--log-dir <path>` CLI argument |

---

## 4. Cockpit Views & Operational Workflows

### 4.1 Layout & Navigation Topology
The Studio interface adheres to a persistent four-zone cockpit layout:
1. **Left Navigation Rail:** Instant switching between *Cluster Overview*, *Worker Fleet*, *Quarantine Queue* (with live badge count), *Functions & Bytecode*, *Task Explorer*, and *Secrets Vault*.
2. **Top Telemetry Ribbon:** Displays active cluster connection status, round-trip ping latency ($RTT\text{ ms}$), aggregate cluster CPU/RAM utilization gauges, and active/queued task counters.
3. **Center Primary Workspace:** Contextual master-detail view for the selected navigation module.
4. **Bottom Collapsible Drawer:** Real-time event ticker, task `stdout`/`stderr` log stream terminal, and audit log inspector.

### 4.2 Visual Node Attestation Workflow
When a new `legatusd` worker registers with the server, the server broadcasts an `EVENT_ENROLLMENT_REQUESTED` frame to Studio:
1. Studio displays a non-blocking toast notification and increments the *Quarantine Queue* badge.
2. Opening the **Quarantine Attestation Modal** displays side-by-side verification cards:
   * **Node Identity:** `worker_id`, `hostname`, OS, CPU architecture, and source IP address.
   * **Hardware Profile:** Physical/logical cores, total RAM, and allocatable quota declaration.
   * **Cryptographic Fingerprint:** Uppercase colon-separated hex (`4E:8A:BC:31:09:FF:7D:12`) and the highlighted **3-Word Visual Confirmation Code** (`Tiger-Galaxy-77`).
3. The operator visually compares the 3-word code against the worker's terminal output and clicks **[Approve & Issue Lease]** (`POST /api/v1/admin/workers/enrollments/{id}/approve`) or **[Reject & Block IP]**.

### 4.3 Task Inspection & Two-Stage Cancellation Control
In the **Task Explorer**, operators can inspect any `QUEUED`, `EXECUTING`, or historical task:
* **Live Metering Gauges:** Progress bars showing real-time Solix instructions consumed vs. `instruction_budget` and memory allocated vs. `max_memory_bytes`.
* **Abort Escalation Button:** Clicking **[Abort Task]** dispatches `POST /api/v1/tasks/{task_id}/abort`. The UI transitions the task badge to `ABORTING (Stage 1 Grace Period - 10s countdown)` until the terminal `EVENT_TASK_TERMINATED` frame confirms either clean `ABORTED` (`130`) or forced `KILLED_FORCIBLY` (`137`).

---

## 5. Client Connection State Machine & Backpressure

### 5.1 Studio Connection FSM
```
    ┌────────────────┐
    │  UNAUTHENTICATED│ ◄──────────────────────────────────────┐
    └───────┬────────┘                                         │
            │ (Operator Login: POST /api/v1/auth/operator)     │
            ▼                                                  │ (401 Unauthorized /
    ┌────────────────┐                                         │  JWT Refresh Failed)
    │ TICKET_EXCHANGE│ ◄───────────────────────────────┐       │
    └───────┬────────┘                                 │       │
            │ (POST /api/v1/auth/studio/ticket)        │       │
            ▼                                          │       │
    ┌────────────────┐                                 │       │
    │   CONNECTING   │ ────────────────────────────────┼───────┘
    └───────┬────────┘ (WSS Upgrade /ws/studio)        │
            │ (STUDIO_SESSION_INIT -> ACK)             │ (Socket Drop /
            ▼                                          │  15s Dead-Man Timeout)
    ┌────────────────┐                                 │
    │   STREAMING    │ ────────────────────────────────┘
    └────────────────┘ (Exponential Jittered Backoff)
```

### 5.2 Log Stream Ring-Buffer Bounds
To prevent desktop memory exhaustion when viewing verbose guest task output:
* Each open task console tab **MUST** store lines in a fixed-capacity circular ring buffer capped at **10,000 lines** (or **8 Megabytes** of UTF-8 text).
* Older lines are evicted in FIFO order as new `EVENT_TASK_LOG_CHUNK` frames arrive.

---

## 6. Verification & Compliance Checklist

- [ ] **Zero Implementation Code:** Document contains zero multi-line Java or FXML code blocks (`GL-0005`).
- [ ] **UI Thread Safety:** All network and disk operations are isolated on Project Loom Virtual Threads (`GL-0011`).
- [ ] **60Hz Frame Batching:** High-frequency WebSocket telemetry updates are coalesced to prevent UI freezes.
- [ ] **Viewport Filtering:** Studio session initialization supports viewport scoping to prevent client CPU saturation.
- [ ] **Bounded Memory:** Task log viewers enforce the 10,000-line / 8 MB ring buffer ceiling.