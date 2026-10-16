# Legatus Studio Operator Manual & Cockpit Guide

## Document Metadata
* **Scope:** Desktop Administration Cockpit Operations (`docs/guides/04-studio.md`)
* **Status:** Final Implementation Standard
* **Audience:** Cluster Operators, Systems Administrators, SREs
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/studio-spec.md`](../specs/studio-spec.md)
  * [`docs/specs/protocols/studio-wire.md`](../specs/protocols/studio-wire.md)
  * [`docs/dev/decisions/0002-adopt-javafx-for-studio-ui.md`](../dev/decisions/0002-adopt-javafx-for-studio-ui.md)

---

## 1. Overview & Installation

**Legatus Studio** (`legatus-studio`) is a native desktop management console built on Java 21, OpenJFX 21, and the AtlantaFX design system. It connects to `legatus-server` over TLS 1.3 to stream real-time cluster telemetry, perform visual node attestation, deploy bytecode functions, and inspect task execution logs.

### 1.1 Running from Source
```bash
# Run directly with the OpenJFX Maven plugin
./server/mvnw -f studio/pom.xml javafx:run

# Or build the executable JAR and run with standard Java 21
./server/mvnw -f studio/pom.xml package -DskipTests
java -jar studio/target/legatus-studio.jar
```

### 1.2 Building Native Binaries & Packages (`jpackage`)
Studio includes an integrated Maven `jpackage` profile (`-Pjpackage`) that generates self-contained native desktop app-images and installers requiring zero pre-installed Java runtimes:

```bash
# Build standalone native app-image in studio/target/dist/legatus-studio/
./server/mvnw -f studio/pom.xml package -Pjpackage -DskipTests
```

The resulting native bundle can be launched directly:
* **Linux:** `./studio/target/dist/legatus-studio/bin/legatus-studio`
* **macOS:** `./studio/target/dist/legatus-studio/bin/legatus-studio`
* **Windows:** `.\studio\target\dist\legatus-studio\bin\legatus-studio.exe`

Platform-native system installers:
* **Linux (Debian/Ubuntu):** `jpackage --type deb ...` $\rightarrow$ `sudo dpkg -i legatus-studio_1.0.0_amd64.deb`
* **Linux (RHEL/Fedora):** `jpackage --type rpm ...` $\rightarrow$ `sudo rpm -i legatus-studio-1.0.0.x86_64.rpm`
* **macOS:** `jpackage --type dmg ...` $\rightarrow$ `LegatusStudio-1.0.0.dmg`
* **Windows:** `jpackage --type msi ...` $\rightarrow$ `LegatusStudio-1.0.0.msi`

---

## 2. Navigating the 4-Zone Cockpit

Studio organizes operations into a persistent four-zone layout:

1. **Top Telemetry Ribbon (Zone 2):**
   * **Connection Status:** Displays active connection indicator (Green = Connected, Yellow = Reconnecting with Jitter, Red = Disconnected).
   * **RTT Latency:** Displays live WebSocket round-trip ping time (e.g., `3.8ms`).
   * **Resource Gauges:** Progress bars showing aggregate cluster CPU utilization and RAM allocation.
   * **Counters:** Live displays of Active Workers, In-Flight Tasks, Queue Depth, and Pending Quarantine approvals.
2. **Left Navigation Rail (Zone 1):**
   * Instant switching between **Overview**, **Fleet**, **Quarantine**, **Functions**, **Task Explorer**, and **Secrets Vault**.
   * Keyboard shortcuts (`Ctrl+1` through `Ctrl+6` on Linux/Windows; `Cmd+1` through `Cmd+6` on macOS).
3. **Center Primary Workspace (Zone 3):**
   * Master-detail interface displaying tabular data, charts, and contextual inspector forms.
   * **Cluster Overview:** High-level cluster summary gauges (Online Workers, Aggregate CPU %, Active Tasks, Memory Pool) coupled with live alert banners:
     * *Auto-Balancer Threshold Breach Warning:* Amber banner and ribbon badge triggered when the server scheduler must exceed worker CPU/RAM/task limits to fulfill compute load without queueing.
     * *Cluster Queue Saturated Error:* Red banner and ribbon badge indicating all cluster capacity is saturated and tasks are forced into wait queues.
     * *Worker Overview Grid:* Responsive card flow (`FlowPane`) displaying individual worker cells with live normalized CPU progress bar, RAM load bar, running task badge, operational state pill, and mini CPU sparkline.
   * **Fleet Topology Table & Inspector:**
     * Sortable 9-column table: Worker Name (Friendly Name + Hostname), Source IP, formatted Uptime (`Xd Yh Zm`), CPU Load (bar + label), RAM Load (bar + label), Active Tasks, color-coded Lease Remaining badge (`>10m` green, `2-10m` amber, `<2m` red) with auto-renewal indicator, Status pill, and inline Active/Paused switch.
     * *Context-Aware Node Inspector:* Hidden when no row is selected (`visible=false, managed=false`). Upon worker selection, reveals inline friendly name editor, Active/Suspended toggle, Auto-Renew Lease toggle, threshold sliders (Target CPU 50–95%, Target RAM 50–95%, Max Tasks 1–64), live metrics, and unified **[Revoke Lease & Delete Worker]** decommission action with confirmation dialog.
4. **Bottom Collapsible Drawer (Zone 4):**
   * Expandable drawer housing the **Task Console Log Stream**, **Cluster Event Log**, and **Security Audit Trail**.
   * Toggle with `Ctrl+` `~` (backtick) or click the drawer expand button.

---

## 3. Quarantine Queue, Attestation & Auto-Accept Workflow

When a new worker daemon launches, it enters quarantine until attested and authorized by an operator or auto-accepted by policy:

```
+-----------------------------------------------------------------------------------------+
| [✓] Auto-Accept Incoming Workers (Admin Only)                                           |
| Banner: Auto-Accept Active: Incoming workers are automatically attested & named by UUID.|
|                                                                                         |
| QUARANTINE ATTESTATION: compute-linux-east-042 (10.0.4.12)                              |
| Hardware Profile: 16 Physical Cores (32 Logical) | 64.0 GB Host RAM                     |
| Declared Allocatable Quota: 16 Cores | 32.0 GB Memory Pool                              |
|                                                                                         |
| Cryptographic Identity:                                                                 |
|   Ed25519 Fingerprint: 4E:8A:BC:31:09:FF:7D:12:AA:03:91:DE:5B:C2:10:88                  |
|                                                                                         |
| Visual Confirmation Code:                                                               |
|   +---------------------------------------------------------------------------------+   |
|   |                         [ TIGER - GALAXY - 77 ]                                 |   |
|   +---------------------------------------------------------------------------------+   |
|                                                                                         |
| [ Reject & Blacklist Node ]                                   [ Approve & Issue Lease ] |
+-----------------------------------------------------------------------------------------+
```

### 3.1 Operator Approval Procedure:
1. When a new node registers, a yellow badge counter appears on the **Quarantine** navigation tab.
2. Click **Quarantine** to view pending registrations. The master list displays hostname, source IP, allocatable cores/memory, submission timestamp, and Ed25519 fingerprint.
3. Attestation card visibility is bound to selection: no card is rendered if no node is selected.
4. When selected, compare the **3-Word Visual Confirmation Code** (`Tiger-Galaxy-77`) against the physical terminal output of the host where `legatusd` was launched.
5. Click **[Approve & Issue Lease]**. Studio opens the **Assign Friendly Worker Name** modal dialog (pre-populated with suggested hostname). Enter a human-readable friendly name (e.g. `gpu-worker-01`).
6. Studio issues `POST /api/v1/admin/workers/enrollments/{id}/approve` with `friendly_name` and lease duration. The server issues a lease token and transitions the worker to `ONLINE`.
7. If the node identity is unrecognized or suspicious, click **[Reject & Blacklist Node]** to reject the registration and optionally blacklist the IP.

### 3.2 Auto-Accept Mode (Admin Only):
* Administrators possessing `ROLE_ADMIN` can toggle the **Auto-Accept Workers** switch.
* When active, a persistent cyan banner notifies operators that auto-acceptance is active.
* Incoming registration requests are automatically approved by the server with lease issuance and named using their unique UUID without operator intervention.

---

## 4. Function Deployment & Task Management

### 4.1 Functions & Bytecode Catalog
1. Navigate to **Functions** in the left navigation rail.
2. **Catalog Persistence & Metadata Inspection:**
   * Registered bytecode functions are loaded directly from `/api/solix-functions` upon session initialization and cached in `StudioContext`.
   * The catalog list displays function name, semver, entry point, 64-character SHA-256 digest, and an active **Invocations in Last Hour** usage frequency badge.
   * Selecting any function opens the dedicated **Metadata Inspector** card displaying:
     * Full Bytecode SHA-256 digest and creation timestamp.
     * **Description Textarea:** Free-form functional summary of the guest code.
     * **Tagging Chip Container:** Visual AtlantaFX tags (e.g. `crypto`, `etl`, `v2`, `batch`) with one-click removal and tag addition.
     * Click **[Save Metadata]** to persist description and tags via `PUT /api/solix-functions/{id}/metadata`.
   * **Tag Search Filter:** Use the top tag filter input to instantly isolate functions matching specific operational tags.
3. **Registering New Bytecode (.slxb):**
   * Click **[Choose File...]** to inspect any local compiled `.slxb` binary. Studio computes the SHA-256 digest in real time.
   * Provide a Function Name and Entry Point (defaults to `main`).
   * Click **[Register Bytecode]** (`POST /api/v1/bytecode`). The function is immediately cataloged and selected for execution.
4. **Transitioning to Execution:**
   * Click **[Open in Execution Workbench]** in the metadata inspector to seamlessly transition to the dedicated execution workbench with the selected function pre-loaded.

### 4.2 Dedicated Execution Workbench & Execution Profiles
1. Navigate to **Execute** in the left navigation rail (or click **[Open in Execution Workbench]** from the catalog).
2. **Persistent Execution Profile Management:**
   * Select a pre-configured execution profile from the **Execution Profile** dropdown (e.g., `Default`, `Benchmark-High-Load`, `Nightly-Batch`).
   * **[Save]:** Update parameters of the currently selected profile (persisted on server via `PUT /api/v1/execution-profiles/{id}`).
   * **[Save As New]:** Prompts for a new profile name and creates a new profile record via `POST /api/v1/execution-profiles`. Enforces uniqueness and optimistic concurrency control.
   * **[Delete]:** Permanently removes the selected profile from the cluster.
   * **[Export] & [Import]:** Export the profile as a portable `.json` configuration file or import external profile definitions.
3. **Arguments Editor & JSON Validation:**
   * Write execution parameters in JSON format. The editor performs real-time JSON syntax validation and indicates validation errors before submission.
   * Click **[Format JSON]** to automatically pretty-print and indent argument payloads.
4. **Flexible Quota Controls & Unlimited Toggles:**
   * **Instruction Budget:** Configure numeric instruction cap or check **[✓] Unlimited Instructions** ($I_{\text{budget}} = \infty$, sentinel `0`).
   * **Wall-Clock Time Limit:** Configure timeout in milliseconds or check **[✓] Unlimited Time Limit** ($T_{\text{limit}} = \infty$, sentinel `0`).
   * **Task Priority:** Select `NORMAL`, `HIGH`, `LOW`, `CRITICAL`, or `BATCH`.
   * **Failure Notification Alert:** Check **[✓] Notify on Failure**. If the task exits with non-zero exit code or fails on the worker, `legatus-server` dispatches an `EVENT_TASK_FAILED_ALERT` frame, triggering an urgent desktop toast notification and audio chime in Studio.
5. **Dispatch & Live WebSocket Telemetry:**
   * Click **[Execute Function]** to submit the task.
   * The live console automatically connects to the task's WebSocket log stream, rendering real-time instruction cycle progress meter updates and streaming standard output chunks in real time without leaving the workbench.

### 4.3 Inspecting Active Tasks, Time Ledger & Two-Stage Abort
1. Navigate to **Task Explorer** in the left rail.
2. **Task Time Ledger Columns:**
   * **Task ID:** Unique identifier for the execution unit.
   * **Function / Profile:** Function entry point name and associated Execution Profile tag (e.g. `matrix-mult [high-throughput]`).
   * **Status:** Color-coded status pills:
     * `QUEUED`: Slate badge (`#475569`) indicating the task is buffered waiting for compute capacity.
     * `EXECUTING`: Cyan badge (`#0284c7`) indicating active execution on an assigned worker node.
     * `COMPLETED`: Emerald green badge (`#15803d`) indicating successful termination with exit code 0.
     * `FAILED`: Red badge (`#b91c1c`) indicating runtime error or non-zero exit code.
     * `ABORTED`: Amber badge (`#b45309`) indicating operator-initiated cancellation.
   * **Execution Duration:** High-precision adaptive run time formatted according to duration thresholds:
     * Milliseconds for sub-second tasks (e.g., `124ms`).
     * Fractional seconds for medium tasks (e.g., `3.42s`).
     * Minutes and seconds for long-running batch jobs (e.g., `1m 15s`).
     * For tasks currently in `EXECUTING` state, durations stream and increment live on each 60Hz telemetry tick.
   * **Start Time:** Absolute timestamp (`HH:mm:ss.SSS`) recording when execution began on the worker.
   * **End Time:** Absolute completion timestamp (`HH:mm:ss.SSS`), showing `--` while tasks remain queued or running.
   * **Worker Node:** Friendly node name (or raw Worker UUID) executing the task.
3. Select any running task from the table to open the live inspector.
4. Observe real-time Solix instruction cycle consumption against the allocated instruction budget, peak memory usage, and execution duration.
5. **Triggering Two-Stage Abort:**
   * Click **[Abort Task]**.
   * Studio issues `POST /api/v1/tasks/{id}/abort` to `legatus-server`, which transmits an `ABORT_TASK` frame to `legatusd`.
   * The status badge transitions to `ABORTING (Stage 1 Grace Period - 10s Countdown)`.
   * If the child process shuts down cooperatively, the terminal status shows `ABORTED` (Exit Code 130).
   * If the process hangs, the worker executes Stage 2 unconditional termination after 10 seconds, showing `KILLED_FORCIBLY` (Exit Code 137).

### 4.4 Cryptographic WORM Audit Trail & Verification
1. Navigate to **Audit Trail** in the left navigation rail.
2. The audit ledger automatically loads the complete cryptographically linked sequence of cluster events (`GET /api/v1/admin/audit-logs`) upon display.
3. Operators can filter ledger entries by **Actor** or **Action** via real-time search inputs, or click **[Refresh Ledger]** to fetch new entries.
4. Clicking **[Verify Hash Chain]** triggers an end-to-end cryptographic integrity verification (`POST /api/v1/admin/audit-logs/verify`). The server walks the SHA-256 chain from the genesis record to the current head record, updating the status badge to `VALID_CHAIN` (or `CORRUPTED_CHAIN` if tampering occurred) and reloading verified records.

### 4.5 Operator Account Administration & RBAC Security (Admin Only)
1. **RBAC Gating & Access Control:**
   * The **User Administration** navigation tab is rendered exclusively for authenticated operators with `ROLE_ADMIN` / `ROLE_CLUSTER_ADMIN`.
   * If an unprivileged operator attempts to load the view, Studio displays a full-screen **Access Denied** lock screen.
   * Server REST endpoints (`/api/v1/admin/accounts/**`) enforce `@PreAuthorize("hasAnyAuthority(...)")`, returning HTTP 403 Forbidden to unauthorized requests.
2. **Context-Aware Master-Detail Inspector:**
   * When no operator account is selected, the inspector pane is completely concealed (`visible=false, managed=false`).
   * Selecting an account displays:
     * **Username:** Editable field with **[Save]** button to update handle with uniqueness validation.
     * **Email Address:** Editable field with **[Save]** button to update operator notification email.
     * **Account Status Toggle:** Transitions state between `ACTIVE` (green badge) and `SUSPENDED` (red badge).
     * **Role Toggle:** Toggles privileges between `OPERATOR` and `CLUSTER_ADMIN`.
     * **Self-Lockout Protection:** If the currently active administrator selects their own account, status deactivation and role demotion buttons are strictly disabled, and an amber warning banner is displayed.
3. **Write-Only Password Reset Modal:**
   * Click **[Reset Password...]** to open the dedicated credential reset modal dialog.
   * Enter the new password and confirmation. Passwords must satisfy enterprise complexity (min 12 characters, mixed case, numbers, and symbols).
   * Observe the real-time **Password Strength Bar** indicating complexity rating (Weak, Moderate, Strong).
   * Plaintext passwords are never returned by the cluster, never cached in memory, and hashed with Argon2id ($m=64\text{MB}, t=3, p=4$) on the server.
4. **Universal WebSocket Reactive Synchronization:**
   * Creating an account, modifying status, changing roles, or resetting passwords triggers an instant `EVENT_USER_STATE_CHANGED` LSTCP WebSocket broadcast.
   * All connected Studio client sessions synchronize their account lists reactively without manual page refreshes.

---

## 5. Console Drawer, Event Streaming & Ring Buffer Bounds

To maintain client responsiveness during high-throughput execution:
* The live bottom console drawer displays real-time cluster events and task stdout/stderr logs.
* **Telemetry Event Streaming:** Cluster state transitions are automatically streamed to the console:
  * Connection and session acknowledgment (`[SYSTEM] Connected to Legatus cluster (session=..., heartbeat=...ms)`).
  * Worker state transitions (`[WORKER] Node <id> -> ONLINE (CPU: ...%, Memory: ...MB, Tasks: ...)`).
  * Task lifecycle events (`[TASK] Task <id> status: EXECUTING (Worker: <id>)`).
  * Worker quarantine enrollment alerts (`[QUARANTINE] Worker enrollment requested: <id> (<visual_code>, IP: ...)`).
  * Real-time task output chunks delivered via LSTCP (`EVENT_TASK_LOG_CHUNK`).
* **Live Filtering & Auto-Scroll:** Operators can type into the filter field to perform instantaneous case-insensitive substring search across all console lines. When **Auto-Scroll** is checked, the view automatically pins to the latest output.
* **Ring Buffer Bounds:** The console buffer enforces a strict ceiling of **10,000 lines** (or **8 Megabytes** of UTF-8 text). When exceeded, older lines are evicted in FIFO order. Operators can clear the buffer at any time using **[Clear]**.

---

## 6. Keyboard Shortcuts & Accessibility

| Shortcut (Linux/Win) | Shortcut (macOS) | Action |
| :--- | :--- | :--- |
| `Ctrl + K` | `Cmd + K` | Open Universal Command Palette (jump to node, task, or function) |
| `Ctrl + 1` ... `Ctrl + 6` | `Cmd + 1` ... `Cmd + 6` | Switch Navigation Rail Views |
| `Ctrl + ~` | `Cmd + ~` | Toggle Bottom Console Drawer |
| `Ctrl + T` | `Cmd + T` | Toggle Theme (`PrimerDark` $\leftrightarrow$ `NordDark`) |
| `Ctrl + Shift + A` | `Cmd + Shift + A` | Open Quarantine Attestation Queue |
| `Escape` | `Escape` | Close active modal dialog |

---

## 7. UI Thread Safety & Transport Invariants

To guarantee glitch-free desktop performance under high-frequency cluster telemetry:
* **JavaFX Application Thread Isolation:** All asynchronous background completions (HTTP Virtual Thread REST calls, LSTCP WebSocket event streams, and countdown timers) dispatch property and collection mutations through `FxAsync.runOnFxThread`.
* **Synchronous Latch Coordination:** When asynchronous tasks complete on background threads, `FxAsync` coordinates with the JavaFX event loop using counting latches, guaranteeing consistent model state prior to subsequent operations.
* **WebSocket Path Normalization:** Studio automatically normalizes server connection URLs, preventing path segment duplication (`/ws/studio`) across direct and reverse-proxied endpoints.
* **URI Path Character Escaping:** REST requests escape space-containing identifiers in path segments (e.g., test fixtures or custom node tags) to ensure strict RFC 3986 compliance.
