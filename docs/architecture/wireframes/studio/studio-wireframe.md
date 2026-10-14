# Legatus Studio Multi-Page Wireframe & UI Specification

## Document Metadata
* **Scope:** Desktop UI/UX Multi-Page Wireframe Architecture (`docs/architecture/wireframes/studio/`)
* **Status:** Final Implementation Standard
* **Audience:** Desktop UI Engineers, UI/UX Designers, Platform Operators
* **Host Toolkit:** Java 21 LTS + OpenJFX 21 + AtlantaFX CSS Design System (`PrimerDark` / `NordDark`)
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/studio-spec.md`](../../../specs/studio-spec.md)
  * [`docs/specs/protocols/studio-wire.md`](../../../specs/protocols/studio-wire.md)
  * [`docs/dev/decisions/0002-adopt-javafx-for-studio-ui.md`](../../../dev/decisions/0002-adopt-javafx-for-studio-ui.md)
  * [`docs/dev/decisions/0005-node-enrollment-and-attestation.md`](../../../dev/decisions/0005-node-enrollment-and-attestation.md)

---

## 1. Executive Summary & Multi-Screen Architecture

**Legatus Studio** (`legatus-studio`) is a native desktop systems cockpit engineered for real-time observability, cryptographic node attestation, and execution governance across distributed compute clusters. 

Unlike single-page web dashboards, Legatus Studio is structured into **seven dedicated pages and modal windows**, each with a dedicated layout, state machine, and data flow:

1. **Page 1: Server Connection & Operator Authentication Window:** Standalone initial login dialog with server discovery, TLS certificate validation, and OS Keychain integration.
2. **Page 2: Fleet Topology & Heatmaps Page:** Tabular cluster inventory with real-time CPU/RAM sparklines, tag filtering, and node lifecycle controls (`Drain`, `Revoke`).
3. **Page 3: Quarantine Attestation Page & Modal:** Dual-column cryptographic attestation interface featuring colon-separated Ed25519 fingerprints and the highlighted 3-word visual confirmation code (`Tiger-Galaxy-77`).
4. **Page 4: Functions & Bytecode Workbench Page:** Split-view catalog displaying registered functions, drag-and-drop Solix bytecode upload dropzones, and entry point configuration.
5. **Page 5: Tasks Explorer & Live Inspector Page:** Task ledger with status filters, live Solix instruction cycle meters, and two-stage abort escalation controls (10-second grace countdown).
6. **Page 6: Secrets Vault Management Page:** Master-detail encrypted key-value store managing AES-256-GCM scoped task secrets.
7. **Page 7: Bottom Telemetry & Console Log Drawer:** Persistent/detachable streaming terminal console backed by a bounded 10,000-line / 8 MB circular ring buffer.

---

## 2. Page 1: Server Connection & Authentication Window (`/auth`)

The connection window is presented upon application launch when no active cluster session exists, or when the operator selects **[Switch Cluster]**:

```
+-----------------------------------------------------------------------------------------+
| Legatus Studio - Connect to Cluster                                           [-] [x]   |
+-----------------------------------------------------------------------------------------+
|                                                                                         |
|       [#] LEGATUS CLUSTER ORCHESTRATOR                                                  |
|       Secure Desktop Cockpit v1.0.0                                                     |
|                                                                                         |
|   Saved Connection Profiles:                                                            |
|   [ Production East-1 Core (https://server.internal.corp:8443)              [v] ]       |
|                                                                                         |
|   Server Connection Parameters:                                                         |
|   +---------------------------------------------------------------------------------+   |
|   | Cluster Endpoint URL:                                                           |   |
|   | [ https://server.internal.corp:8443                                           ] |   |
|   |                                                                                 |   |
|   | Custom TLS CA Certificate (Optional):                                           |   |
|   | [ /etc/legatus/tls/ca-internal.crt                              ] [ Browse... ] |   |
|   |                                                                                 |   |
|   | [ ] Disable TLS 1.3 Certificate Verification (Insecure / Dev Only)              |   |
|   +---------------------------------------------------------------------------------+   |
|                                                                                         |
|   Operator Authentication Credentials:                                                  |
|   +---------------------------------------------------------------------------------+   |
|   | Username or Operator ID:                                                        |   |
|   | [ ops-admin-01                                                                ] |   |
|   |                                                                                 |   |
|   | Password / API Token:                                                           |   |
|   | [ ****************************************                                  [o] ] |   |
|   |                                                                                 |   |
|   | [x] Remember Profile in OS Keychain (Encrypted via libsecret/Keychain/DPAPI)    |   |
|   +---------------------------------------------------------------------------------+   |
|                                                                                         |
|   Status: Ready to connect.                                                             |
|                                                                                         |
|       [ Exit Application ]                              [ Connect to Cluster ]          |
|                                                                                         |
+-----------------------------------------------------------------------------------------+
```

### 2.1 UI Component Specifications
* **Cluster Endpoint URL:** Text field with real-time URI validation (`https://` mandatory; `wss://` derived automatically).
* **TLS CA Certificate Picker:** File chooser restricted to `.crt`, `.pem`, and `.cer` formats.
* **Insecure Checkbox:** When toggled, displays a prominent amber warning alert (`> [!WARNING] Disabling TLS validation exposes cluster credentials to MitM attacks`).
* **Keychain Toggle:** Saves the refresh token encrypted via native OS APIs (`libsecret` on Linux, `Keychain` on macOS, `DPAPI` on Windows) (`ADR-0002`).
* **[Connect to Cluster] Button:** Dispatches `POST /api/v1/auth/operator` on a Project Loom Virtual Thread. On HTTP 200, initiates the WebSocket upgrade on `/ws/studio?ticket=...`.

---

## 3. Page 2: Cluster Overview & Fleet Topology Page (`/fleet`)

When connected, the main cockpit window anchors the persistent **Top Telemetry Ribbon (Zone 2)** and **Left Navigation Rail (Zone 1)**. Selecting **Fleet** renders the cluster compute node table and inspector:

```
+-------------------------------------------------------------------------------------------------------------------+
| * Legatus Core (8443) [CONNECTED]  RTT: 3.4ms | CPU: [||||||||  ] 38% | RAM: [||||||    ] 42% | Active Workers: 42 |
| In-Flight Tasks: 128 | Queue Depth: 0 | Quarantine: [2 PENDING]                         | [admin@ops] [Settings]  |
+-------------------+-----------------------------------------------------------------------------------------------+
| [*] Overview      | FLEET TOPOLOGY & COMPUTE NODES                                            [+ Provision Node]  |
| [X] Fleet (42)    | Search: [ Filter by ID, Host, Tag...   ] State: [ ALL [v] ] Sort: [ Cores (High to Low) [v] ]     |
| [!] Quarantine 2  |-----------------------------------------------------------------------------------------------|
| [ ] Functions (8) | WORKER ID       HOSTNAME          OS / ARCH      CORES (ALLOC)   RAM FREE / TOTAL   STATE    LOAD |
| [ ] Tasks (128)   |-----------------------------------------------------------------------------------------------|
| [ ] Secrets Vault | wk-east-01      compute-01.corp   Linux x86_64   16 / 16 (0)     28.4 / 32.0 GB     ONLINE   14%  |
|                   | wk-east-02      compute-02.corp   Linux x86_64   16 / 16 (4)     24.1 / 32.0 GB     ONLINE   38%  |
|                   | wk-win-01       win-srv-01.corp   Windows x86_64 32 / 32 (8)     52.8 / 64.0 GB     ONLINE   22%  |
|                   | wk-edge-09      edge-arm-09.corp  Linux AArch64  4 / 4 (4)       1.2 / 8.0 GB       DRAINING 90%  |
|                   | wk-backup-04    bkup-04.corp      Linux x86_64   8 / 8 (0)       16.0 / 16.0 GB     OFFLINE   --  |
|                   |-----------------------------------------------------------------------------------------------|
|                   | SELECTED NODE INSPECTOR: wk-east-02 (compute-02.corp)                                         |
|                   | Hardware Profile: 16 Physical Cores (32 HT) | 32.0 GB ECC RAM | Linux 6.8.0-45-generic        |
|                   | Node Tags: zone=us-east-1a, tier=compute-high, gpu=none                                       |
|                   | Active Tasks (4): tsk-01HXYZ (82%), tsk-01HXYA (14%), tsk-01HXYB (4%), tsk-01HXYC (0%)        |
|                   | CPU Sparkline (60s):  _/\_/\__/\/\_ 38% peak | RAM Sparkline: ____________-- 24.1 GB free       |
|                   | Session ID: ses_01H89ABC | Connected Since: 2026-10-14 12:00:00 (Uptime: 5h 32m)              |
|                   |                                                                                               |
|                   |   [ Copy Fingerprint ]       [ Ping Node (3.2ms) ]       [ Drain Node ]       [ Revoke Node ]     |
+-------------------+-----------------------------------------------------------------------------------------------+
| Tabs: [Live Console (tsk-01HXYZ)]  [Cluster Event Log]  [Audit Trail]                       [^ Expand] [v Hide]   |
+-------------------------------------------------------------------------------------------------------------------+
```

### 3.1 UI Component Specifications
* **State Filter:** Dropdown filter with color pills: `ALL` (default), `ONLINE` (green), `DRAINING` (amber), `OFFLINE` (grey).
* **Selected Node Inspector:** Collapsible master-detail card dynamically updated via JavaFX Observable properties.
* **[Drain Node] Button:** Dispatches `POST /api/v1/admin/workers/{id}/drain`. Updates status badge to `DRAINING` with spinner.
* **[Revoke Node] Button:** Danger button triggering confirmation modal: `Revoke lease for wk-east-02? Node will be severed immediately.`

---

## 4. Page 3: Quarantine Attestation Page & Approval Modal (`/quarantine`)

This view manages untrusted compute nodes awaiting operator cryptographic attestation before being admitted into the cluster scheduling ring:

```
+-------------------------------------------------------------------------------------------------------------------+
| * Legatus Core (8443) [CONNECTED]  RTT: 3.4ms | CPU: [||||||||  ] 38% | RAM: [||||||    ] 42% | Active Workers: 42 |
| In-Flight Tasks: 128 | Queue Depth: 0 | Quarantine: [2 PENDING]                         | [admin@ops] [Settings]  |
+-------------------+-----------------------------------------------------------------------------------------------+
| [*] Overview      | QUARANTINE ATTESTATION QUEUE (2 Nodes Pending Verification)                                   |
| [ ] Fleet (42)    | All unapproved worker daemons remain strictly isolated. Zero tasks will be dispatched.       |
| [!] Quarantine 2  |-----------------------------------------------------------------------------------------------|
| [ ] Functions (8) | PENDING REGISTRATION QUEUE:                                                                   |
| [ ] Tasks (128)   | +-------------------------------------------------------------------------------------------+ |
| [ ] Secrets Vault | | [*] compute-linux-east-042 (10.0.4.12) | Linux x86_64 | 16 Cores | Submitted 2m ago       | |
|                   | | [ ] compute-edge-arm-003  (10.0.9.88) | Linux AArch64| 4 Cores  | Submitted 8m ago       | |
|                   | +-------------------------------------------------------------------------------------------+ |
|                   |                                                                                               |
|                   | +-- DUAL-COLUMN CRYPTOGRAPHIC ATTESTATION INSPECTOR ----------------------------------------+ |
|                   | | CLAIMED NODE IDENTITY & HARDWARE:        | CRYPTOGRAPHIC VERIFICATION & ATTESTATION:      | |
|                   | | Worker ID: compute-linux-east-042        | Ed25519 Public Key:                            | |
|                   | | Hostname:  wk-east-042.internal.corp     | 7c9f8a12e34b56d...                             | |
|                   | | Source IP: 10.0.4.12 (Direct Connect)    |                                                | |
|                   | | OS/Kernel: Linux 6.8.0 x86_64            | SHA-256 Public Key Fingerprint:                | |
|                   | | Hardware:  16 Physical Cores / 32 GB RAM | 4E:8A:BC:31:09:FF:7D:12:AA:03:91:DE:5B:C2:10:88 | |
|                   | | Allocatable Core Override: [ 16  [^][v] ]|                                                | |
|                   | | Memory Pool Cap (GB):      [ 32  [^][v] ]| DETERMINISTIC 3-WORD VISUAL CONFIRMATION CODE:  | |
|                   | | Tags: [ zone=us-east-1a, tier=standard ] | +--------------------------------------------+ | |
|                   | |                                          | |          [ TIGER - GALAXY - 77 ]           | | |
|                   | | Physical Operator Verification Check:    | +--------------------------------------------+ | |
|                   | | [x] Hostname matches inventory rack tag  | > Confirm this exact code matches the terminal | |
|                   | | [x] Source IP belongs to management VLAN | > output of the newly provisioned worker host. | |
|                   | |                                          |                                                | |
|                   | |   [ Reject & Blacklist Host IP ]         |   [ Approve & Issue Long-Term Cluster Lease ]  | |
|                   | +-------------------------------------------------------------------------------------------+ |
+-------------------+-----------------------------------------------------------------------------------------------+
| Tabs: [Live Console]  [Cluster Event Log]  [Audit Trail]                                    [^ Expand] [v Hide]   |
+-------------------------------------------------------------------------------------------------------------------+
```

### 4.1 UI Component Specifications
* **3-Word Visual Confirmation Code Card:** Rendered in large bold font (`20pt`) with a vibrant amber border (`#f59e0b`).
* **Allocatable Core Override Spinner:** Allows the operator to reduce the declared cores before granting the lease (e.g., reserving 2 cores for host OS background tasks).
* **[Approve & Issue Lease] Button:** Primary green button. Calls `POST /api/v1/admin/workers/enrollments/{id}/approve` with allocatable overrides. Transitions node to `APPROVED` and updates the queue badge.

---

## 5. Page 4: Functions & Bytecode Workbench Page (`/functions`)

This page enables developers and operators to catalog, upload, inspect, and configure Solix bytecode artifacts (`.solix` / `.slxb`):

```
+-------------------------------------------------------------------------------------------------------------------+
| * Legatus Core (8443) [CONNECTED]  RTT: 3.4ms | CPU: [||||||||  ] 38% | RAM: [||||||    ] 42% | Active Workers: 42 |
| In-Flight Tasks: 128 | Queue Depth: 0 | Quarantine: [2 PENDING]                         | [admin@ops] [Settings]  |
+-------------------+-----------------------------------------------------------------------------------------------+
| [*] Overview      | FUNCTIONS & SOLIX BYTECODE WORKBENCH                                      [+ Upload Function] |
| [ ] Fleet (42)    | Manage registered guest bytecode binaries and runtime execution quotas.                       |
| [!] Quarantine 2  |-----------------------------------------------------------------------------------------------|
| [X] Functions (8) | FUNCTION NAME        SEMVER     ENTRY POINT       SHA-256 DIGEST     DEFAULT BUDGET   ACTIVE TASKS|
| [ ] Tasks (128)   |-----------------------------------------------------------------------------------------------|
| [ ] Secrets Vault | img-resizer-service  v1.2.0     resize_jpeg       4a7b9c...e01f      1,000,000 cycles 42          |
|                   | payment-fraud-check  v2.0.1     evaluate_risk     8f1d2c...31aa      500,000 cycles   18          |
|                   | data-indexer         v0.9.4     build_index       c34e5a...99bb      5,000,000 cycles 64          |
|                   | webhook-dispatcher   v1.0.0     dispatch_event    11aa22...dd44      250,000 cycles    4          |
|                   |-----------------------------------------------------------------------------------------------|
|                   | +-- BYTECODE DEPLOYMENT & METADATA CONFIGURATION -------------------------------------------+ |
|                   | | Drag & Drop Compiled Solix Binary (.solix, .slxb):                                        | |
|                   | | +---------------------------------------------------------------------------------------+ | |
|                   | | |       [+] Drag and drop .solix bytecode file here, or click to [Browse...]            | | |
|                   | | |       Current File: img-resizer-v1.3.0.solix (142.4 KB, SHA-256: 9e2c...88ff)         | | |
|                   | | +---------------------------------------------------------------------------------------+ | |
|                   | | Function Configuration:                                                                 | |
|                   | | Function Identifier: [ img-resizer-service      ] SemVer Version:  [ v1.3.0             ]   | |
|                   | | Entry Point Symbol:  [ process_image_pipeline   ] Default Timeout: [ 10000ms    [^][v]  ]   | |
|                   | | Instruction Budget:  [ 1000000 cycles   [^][v]  ] Max Memory Cap:  [ 67108864 B (64MB)  ]   | |
|                   | |                                                                                         | |
|                   | |   [ Discard Draft ]                                    [ Deploy Function to Cluster ]   | |
|                   | +-------------------------------------------------------------------------------------------+ |
+-------------------+-----------------------------------------------------------------------------------------------+
| Tabs: [Live Console]  [Cluster Event Log]  [Audit Trail]                                    [^ Expand] [v Hide]   |
+-------------------------------------------------------------------------------------------------------------------+
```

### 5.1 UI Component Specifications
* **Dropzone:** Drag-and-drop target accepting only `.solix` and `.slxb` files. Automatically calculates and displays local SHA-256 digest upon file selection.
* **Instruction Budget Spinner:** Bounded between `1,000` and `1,000,000,000` cycles (`GL-0005`).
* **[Deploy Function to Cluster] Button:** Multipart upload to `POST /api/v1/functions`. On success, catalogs the function and triggers bytecode pre-caching on eligible worker nodes.

---

## 6. Page 5: Tasks Explorer & Inspection Page (`/tasks`)

The Task Explorer provides cluster-wide task ledger queries, real-time Solix execution metering, and emergency cancellation:

```
+-------------------------------------------------------------------------------------------------------------------+
| * Legatus Core (8443) [CONNECTED]  RTT: 3.4ms | CPU: [||||||||  ] 38% | RAM: [||||||    ] 42% | Active Workers: 42 |
| In-Flight Tasks: 128 | Queue Depth: 0 | Quarantine: [2 PENDING]                         | [admin@ops] [Settings]  |
+-------------------+-----------------------------------------------------------------------------------------------+
| [*] Overview      | TASK EXPLORER & EXECUTION LEDGER                                         [+ Submit Test Task] |
| [ ] Fleet (42)    | Filter: Status [ ALL [v] ] Worker [ ALL [v] ] Function [ img-resizer-service [v] ] Query: [  ]    |
| [!] Quarantine 2  |-----------------------------------------------------------------------------------------------|
| [ ] Functions (8) | TASK ID          FUNCTION             WORKER       STATUS      INSTRUCTIONS      MEM PEAK  WALL   |
| [X] Tasks (128)   |-----------------------------------------------------------------------------------------------|
| [ ] Secrets Vault | tsk-01HXYZ789A   img-resizer-service  wk-east-02   EXECUTING   [||||||||  ] 82%  38.2 MB   142ms  |
|                   | tsk-01HXYZ789B   payment-fraud-check  wk-east-01   COMPLETED   142,090 (28%)     18.4 MB   48ms   |
|                   | tsk-01HXYZ789C   data-indexer         wk-win-01    EXECUTING   [||||      ] 40%  54.1 MB   820ms  |
|                   | tsk-01HXYZ789D   webhook-dispatcher   wk-edge-09   FAILED (70) 1,000,000 (100%)  12.0 MB   310ms  |
|                   |-----------------------------------------------------------------------------------------------|
|                   | LIVE TASK INSPECTOR: tsk-01HXYZ789A (img-resizer-service)                                     |
|                   | Execution Node: wk-east-02 (PID: 4821) | Dispatched: 2026-10-14 17:30:00.100 (142ms ago)       |
|                   | Solix Instruction Progress: [||||||||||||||||||||||||||||||||||||        ] 824,019 / 1,000,000|
|                   | Peak Resident Memory:       [||||||||||||||||                            ] 38.2 MB / 64.0 MB  |
|                   | Input Arguments JSON:       {"source_s3": "bucket/img.jpg", "target_w": 1920, "target_h": 1080} |
|                   | Secrets Scope Injected:     scope:task:tsk-01HXYZ789A (2 keys decrypted into stdin)          |
|                   |                                                                                               |
|                   |   [ View Live Console Tab ]       [ Copy Trace ID ]              [ Abort Task (2-Stage) ]     |
+-------------------+-----------------------------------------------------------------------------------------------+
| Tabs: [Live Console (tsk-01HXYZ789A)]  [Cluster Event Log]  [Audit Trail]                   [^ Expand] [v Hide]   |
+-------------------------------------------------------------------------------------------------------------------+
```

### 6.1 Two-Stage Abort Modal Specification
Clicking **[Abort Task (2-Stage)]** opens the confirmation modal with a live grace countdown:

```
+-----------------------------------------------------------------------------------------+
| CONFIRM TASK ABORT ESCALATION: tsk-01HXYZ789A                                           |
| Function: img-resizer-service | Node: wk-east-02 (PID: 4821)                            |
|                                                                                         |
| Warning: Halting a running task interrupts active guest execution.                      |
|                                                                                         |
| Escalation Lifecycle:                                                                   |
| 1. Stage 1 (Cooperative): Delivers SIGTERM/CTRL_BREAK; grants 10s grace period.         |
| 2. Stage 2 (Unconditional): Forcibly destroys runner process via SIGKILL/Terminate.    |
|                                                                                         |
| Reason for Cancellation: [ Operator manual intervention from Studio Cockpit           ] |
|                                                                                         |
|   [ Cancel / Dismiss ]                                [ Execute Cooperative Abort (10s) ]|
+-----------------------------------------------------------------------------------------+
```

---

## 7. Page 6: Secrets Vault Management Page (`/secrets`)

The Secrets Vault manages encrypted environment variables and guest credentials passed to tasks:

```
+-------------------------------------------------------------------------------------------------------------------+
| * Legatus Core (8443) [CONNECTED]  RTT: 3.4ms | CPU: [||||||||  ] 38% | RAM: [||||||    ] 42% | Active Workers: 42 |
| In-Flight Tasks: 128 | Queue Depth: 0 | Quarantine: [2 PENDING]                         | [admin@ops] [Settings]  |
+-------------------+-----------------------------------------------------------------------------------------------+
| [*] Overview      | ENCRYPTED SECRETS VAULT (AES-256-GCM Envelope Encryption)                     [+ Create Secret]   |
| [ ] Fleet (42)    | All secrets are encrypted at rest with HKDF-SHA256 keys derived from LEGATUS_MASTER_KEY.     |
| [!] Quarantine 2  |-----------------------------------------------------------------------------------------------|
| [ ] Functions (8) | SECRET KEY NAME           SCOPE / FUNCTION TARGET     LAST UPDATED         GCM AUTH TAG (HEX) |
| [ ] Tasks (128)   |-----------------------------------------------------------------------------------------------|
| [X] Secrets Vault | AWS_S3_ACCESS_KEY         global                      2026-10-14 09:12:00  4f8a...9911        |
|                   | AWS_S3_SECRET_KEY         global                      2026-10-14 09:12:00  77bb...cc33        |
|                   | STRIPE_PAYMENT_SECRET     payment-fraud-check         2026-10-12 14:00:00  12ad...88ef        |
|                   | DATABASE_PROD_URL         data-indexer                2026-10-10 18:30:00  ee44...bb77        |
|                   |-----------------------------------------------------------------------------------------------|
|                   | +-- CREATE / ROTATE ENCRYPTED SECRET MODAL -------------------------------------------------+ |
|                   | | Secret Name / Identifier: [ STRIPE_PAYMENT_SECRET                                       ] | |
|                   | | Secret Scope:             [ Function Scoped: payment-fraud-check                     [v]] | |
|                   | | Secret Value (Masked):    [ ********************************************************** [o]] | |
|                   | |                                                                                           | |
|                   | | Cryptographic Guarantees:                                                                 | |
|                   | | - Plaintext is never stored in server database or logs.                                   | |
|                   | | - Decrypted in-memory immediately prior to task dispatch; scrubbed via explicit_bzero.    | |
|                   | |                                                                                           | |
|                   | |   [ Cancel ]                                            [ Encrypt & Store Secret ]        | |
|                   | +-------------------------------------------------------------------------------------------+ |
+-------------------+-----------------------------------------------------------------------------------------------+
| Tabs: [Live Console]  [Cluster Event Log]  [Audit Trail]                                    [^ Expand] [v Hide]   |
+-------------------------------------------------------------------------------------------------------------------+
```

---

## 8. Page 7: Bottom Telemetry & Console Log Drawer (`/console`)

The bottom drawer is dockable, expandable, and detachable into a dedicated window:

```
+-------------------------------------------------------------------------------------------------------------------+
| Tabs: [* Live Console (tsk-01HXYZ789A)]  [Cluster Event Ticker (42)]  [Audit Log (128)]     [Detach] [^ Expand] [x] |
| Search: [ Regex filter...    ] Log Level: [ ALL [v] ] [x] Auto-Scroll  [x] ANSI Colors  Buffer: 8,420/10,000 lines|
|-------------------------------------------------------------------------------------------------------------------|
| 17:30:00.102 [tsk-01HXYZ789A] [INFO]  Runner child spawned (PID: 4821) via reproc++ with CREATE_NEW_PROCESS_GROUP  |
| 17:30:00.105 [tsk-01HXYZ789A] [INFO]  Execution envelope streamed to child STDIN (EOF sent, 1,482 bytes read)      |
| 17:30:00.110 [tsk-01HXYZ789A] [DEBUG] Solix VM context allocated: arena 64MB, entry symbol 'process_image_pipeline'|
| 17:30:00.125 [tsk-01HXYZ789A] [INFO]  [GUEST STDOUT] Image decoding started: target format JPEG, dimensions 1920x1080|
| 17:30:00.142 [tsk-01HXYZ789A] [DEBUG] Solix cycle checkpoint: 412,000 instructions executed. RSS: 24.1 MB         |
| 17:30:00.180 [tsk-01HXYZ789A] [INFO]  [GUEST STDOUT] Downsampling pass completed. Resampling bicubic matrix...      |
| 17:30:00.210 [tsk-01HXYZ789A] [DEBUG] Solix cycle checkpoint: 824,019 instructions executed. RSS: 38.2 MB         |
+-------------------------------------------------------------------------------------------------------------------+
```

---

## 9. Visual Mockup Reference

A multi-panel architectural wireframe blueprint displaying all six core application windows is available at:
* Visual File: [`docs/architecture/wireframes/studio/studio-wireframe.png`](studio-wireframe.png)

```markdown
![Legatus Studio Multi-Screen Cockpit Wireframe Board](studio-wireframe.png)
```

---

## 10. Verification & TestFX Conformance Checklist

- [ ] **7 Distinct Screen Definitions:** Explicitly specifies Connection, Fleet, Quarantine, Functions, Tasks, Secrets, and Console Drawer.
- [ ] **Thread Confinement:** All UI scene graph mutations are isolated to `Platform.runLater()` (`GL-0011`).
- [ ] **Attestation Modal Elements:** Exposes test IDs `#txt-fingerprint`, `#lbl-confirmation-code`, `#btn-approve`, `#btn-reject`.
- [ ] **Two-Stage Abort Modal:** Exposes `#btn-abort-stage1`, `#lbl-grace-countdown`, `#btn-force-kill`.
- [ ] **Ring Buffer Ceiling:** Console enforces strict 10,000-line / 8 MB FIFO discard bounds.
- [ ] **Headless Test Pass:** Verified under OpenJFX Monocle and `TestFX` in CI.
