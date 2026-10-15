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
4. **Bottom Collapsible Drawer (Zone 4):**
   * Expandable drawer housing the **Task Console Log Stream**, **Cluster Event Log**, and **Security Audit Trail**.
   * Toggle with `Ctrl+` `~` (backtick) or click the drawer expand button.

---

## 3. Visual Quarantine Attestation Workflow

When a new worker daemon launches, it enters quarantine. Operators visually attest its cryptographic fingerprint before issuing a cluster lease:

```
+-----------------------------------------------------------------------------------------+
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

### Step-by-Step Approval Procedure:
1. When a new node registers, a yellow badge counter appears on the **Quarantine** navigation tab.
2. Click **Quarantine** to view pending registrations.
3. Compare the **3-Word Visual Confirmation Code** (`Tiger-Galaxy-77`) against the physical terminal output of the host where `legatusd` was launched.
4. If the code and fingerprint match, click **[Approve & Issue Lease]**. The server hashes an issued secret token using Argon2id and transitions the node to `ONLINE`.
5. If the node identity is unrecognized or the IP is suspicious, click **[Reject & Blacklist Node]** to permanently revoke the registration.

---

## 4. Function Deployment & Task Management

### 4.1 Deploying Solix Bytecode
1. Navigate to **Functions & Bytecode** in the left rail.
2. Drag and drop a compiled `.solix` bytecode file onto the upload dropzone, or click **[Upload Bytecode]**.
3. Specify the entry point function name (e.g., `execute_workload`).
4. Set default execution constraints (Instruction Budget, Memory Limit, Timeout).
5. Click **[Publish Function]**. Studio verifies the SHA-256 digest with the server and catalogs the binary.

### 4.2 Inspecting Active Tasks & Two-Stage Abort
1. Navigate to **Task Explorer**.
2. Select any running task to open the live inspector.
3. Observe real-time Solix instruction cycle consumption against the allocated instruction budget.
4. **Triggering Two-Stage Abort:**
   * Click **[Abort Task]**.
   * Studio issues `POST /api/v1/tasks/{id}/abort` to `legatus-server`, which transmits an `ABORT_TASK` frame to `legatusd`.
   * The status badge transitions to `ABORTING (Stage 1 Grace Period - 10s Countdown)`.
   * If the child process shuts down cooperatively, the terminal status shows `ABORTED` (Exit Code 130).
   * If the process hangs, the worker executes Stage 2 unconditional termination after 10 seconds, showing `KILLED_FORCIBLY` (Exit Code 137).

---

## 5. Console Drawer & Ring Buffer Bounds

To maintain client responsiveness during high-throughput execution:
* The live task console tab stores incoming `stdout`/`stderr` chunks in an in-memory circular ring buffer.
* The buffer enforces a strict ceiling of **10,000 lines** (or **8 Megabytes** of UTF-8 text).
* When the limit is reached, older lines are automatically discarded in FIFO order.
* Operators can click **[Export Logs]** to dump the complete task transcript to a local file.

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
