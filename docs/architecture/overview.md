# Legatus Architecture & System Overview

## Document Metadata
* **Scope:** Cluster-Wide Architectural Topology & System Invariants (`docs/architecture/`)
* **Status:** Final Architectural Specification
* **Target Audience:** Principal Systems Engineers, Infrastructure Operators, Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/worker-spec.md`](../specs/worker-spec.md)
  * [`docs/specs/stdlib-spec.md`](../specs/stdlib-spec.md)
  * [`docs/specs/server-spec.md`](../specs/server-spec.md)
  * [`docs/specs/studio-spec.md`](../specs/studio-spec.md)
  * [`docs/specs/protocols/worker-wire.md`](../specs/protocols/worker-wire.md)
  * [`docs/specs/protocols/studio-wire.md`](../specs/protocols/studio-wire.md)
  * [`docs/specs/protocols/api-endpoint.md`](../specs/protocols/api-endpoint.md)
  * [`docs/dev/decisions/README.md`](../dev/decisions/README.md) (`ADR-0001` through `ADR-0010`)

---

## 1. Executive Summary & Architectural Pillars

**Legatus** is an ultra-high-density distributed serverless compute runtime designed to execute untrusted, multi-tenant workloads with microsecond cold starts, deterministic resource metering, and absolute fault containment. The ecosystem is architected around five foundational engineering pillars:

1. **Deterministic Resource Accounting:** Compute consumption is metered in discrete virtual machine instructions ($I_{\text{consumed}} \le I_{\text{budget}}$) using the upstream **Solix Virtual Machine** ([`ADR-0004`](../dev/decisions/0004-adopt-solix-for-worker-runner.md)). A function execution consumes the exact same instruction quota on any CPU architecture (x86_64, AArch64), eliminating the nondeterminism of wall-clock billing.
2. **Strict Blast Radius Containment:** Guest workloads execute inside ephemeral child OS processes (`legatus-runner`) supervised by `reproc++` ([`ADR-0008`](../dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md)). A catastrophic failure, memory corruption, or native crash within guest code is strictly isolated to that specific task, leaving the host supervisor daemon (`legatusd`) completely unaffected.
3. **Zero External Infrastructure Dependency:** The orchestration coordinator (`legatus-server`) operates as a self-contained, standalone executable JAR ([`ADR-0001`](../dev/decisions/0001-adopt-jhipster-for-server-backend.md)). All cluster state, worker registries, task ledgers, and bytecode artifacts reside in an embedded, page-encrypted H2 relational database (`CIPHER=AES`), eliminating the operational complexity of dedicated PostgreSQL, Redis, or etcd clusters.
4. **Project Loom Concurrency Model:** Network I/O, WebSocket streaming, and task scheduling within `legatus-server` execute on Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Blocking operations do not pin OS platform threads, achieving high socket density with readable, imperative code.
5. **Visual Cryptographic Attestation:** Node onboarding enforces zero-touch provisioning coupled with Man-in-the-Middle (MitM) defense ([`ADR-0005`](../dev/decisions/0005-node-enrollment-and-attestation.md)). Unconfigured worker daemons are placed into quarantine until an operator visually verifies the node's Ed25519 fingerprint and deterministic 3-word confirmation code (`Tiger-Galaxy-77`) in the native desktop cockpit (`legatus-studio`, [`ADR-0002`](../dev/decisions/0002-adopt-javafx-for-studio-ui.md)).

---

## 2. Cluster Topographical Architecture

The Legatus cluster comprises five primary binary deliverables distributed across administrative, coordination, and compute boundaries:

```
+---------------------------------------------------------------------------------------------------+
|                                  OPERATOR & ADMINISTRATIVE PLANE                                  |
|                                                                                                   |
|   +------------------------------------+          +-------------------------------------------+   |
|   |   Operator CLI (legatus)           |          |   Legatus Studio (legatus-studio)         |   |
|   |   - Interactive Maintenance CLI    |          |   - Java 21 / OpenJFX 21 Native Cockpit   |   |
|   |   - Node Drain, Status, Log Level  |          |   - 60Hz Telemetry Stream Coalescer       |   |
|   |   - Pure Local IPC (Zero Network)  |          |   - Visual Ed25519 Quarantine Attestation |   |
|   +-----------------+------------------+          +---------------------+---------------------+   |
+---------------------|---------------------------------------------------|-------------------------+
                      |                                                   |
                      | Local IPC (ipc://)                                | HTTPS (TLS 1.3) /
                      | Unix Domain Socket / Named Pipe                   | WSS (/ws/studio)
                      v                                                   v
+---------------------------------------------------------------------------------------------------+
|                                   CENTRAL COORDINATION PLANE                                      |
|                                                                                                   |
|   +-------------------------------------------------------------------------------------------+   |
|   |   Legatus Server (legatus-server)                                                         |   |
|   |   - Java 21 LTS / Spring Boot 3 / Project Loom Virtual Thread Bus                         |   |
|   |   - Deterministic Task Scheduler (Least-Loaded / Bin-Pack / Locality-First)               |   |
|   |   - LCTP WebSocket Broker & Heartbeat Dead-Man's Switch (15s Timeout)                     |   |
|   |   - Ephemeral Burn-on-Read Ticket Issuer (30s TTL, Caffeine Cache)                        |   |
|   |   - Embedded Relational Persistence (H2 with AES-128 File-Level Encryption At-Rest)       |   |
|   |   - AES-256-GCM Envelope Secret Vault (HKDF-SHA256 derived from LEGATUS_MASTER_KEY)       |   |
|   +---------------------------------------------+---------------------------------------------+   |
+-------------------------------------------------|-------------------------------------------------+
                                                  |
                                                  | Full-Duplex WSS (TLS 1.3, /ws/worker)
                                                  | Legatus Cluster Transport Protocol (LCTP)
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                    DISTRIBUTED COMPUTE PLANE                                      |
|                                                                                                   |
|   +-------------------------------------------------------------------------------------------+   |
|   |   Legatus Worker Host (Compute Node)                                                      |   |
|   |                                                                                           |   |
|   |   +-----------------------------------------------------------------------------------+   |   |
|   |   |   Worker Supervisor Daemon (legatusd) - C++20                                     |   |   |
|   |   |   - Persistent Outbound TLS 1.3 WSS Client (IXWebSocket)                          |   |   |
|   |   |   - In-Flight Task Session Reconciliation (reconcile_tasks)                       |   |   |
|   |   |   - Local Operator IPC Listener (nng REQ0/REP0 on ipc://)                         |   |   |
|   |   |   - Two-Stage Process Lifecycle Supervisor (reproc++)                             |   |   |
|   |   +---------------------+---------------------------------------+---------------------+   |   |
|   |                         |                                       |                         |   |
|   |                         | Stdin Stream (Envelope)               | Stdin Stream (Envelope) |   |
|   |                         v                                       v                         |   |
|   |   +-----------------------------------+   +-----------------------------------+           |   |
|   |   |   Guest Runner (legatus-runner)   |   |   Guest Runner (legatus-runner)   |           |   |
|   |   |   - Ephemeral C++20 Child Process |   |   - Ephemeral C++20 Child Process |           |   |
|   |   |   - Solix VM (libsolix_vm.a)      |   |   - Solix VM (libsolix_vm.a)      |           |   |
|   |   |   - Deterministic ARC Memory      |   |   - Deterministic ARC Memory      |           |   |
|   |   |   - Instruction Counter Budget    |   |   - Instruction Counter Budget    |           |   |
|   |   |   - Zero Host Syscall Access      |   |   - Zero Host Syscall Access      |           |   |
|   |   +-----------------------------------+   +-----------------------------------+           |   |
|   +-------------------------------------------------------------------------------------------+   |
+---------------------------------------------------------------------------------------------------+
```

---

## 3. Subsystem Manifest & Core Responsibilities

| Executable / Subsystem | Runtime & Stack | Functional Mandate | Governing Specification |
| :--- | :--- | :--- | :--- |
| **`legatus-server`** | Java 21 LTS / Spring Boot 3 / Loom / H2 | Cluster state broker, worker registry, task scheduler, bytecode storage, telemetry broadcaster. | [`docs/specs/server-spec.md`](../specs/server-spec.md) |
| **`legatusd`** | C++20 / IXWebSocket / reproc++ / nng | Worker supervisor daemon, outbound LCTP WebSocket client, child process isolation, IPC listener. | [`docs/specs/worker-spec.md`](../specs/worker-spec.md) |
| **`legatus-runner`** | C++20 / Solix VM (`libsolix_vm.a`) | Ephemeral task sandbox, instruction budget enforcement, ARC memory model, zero ambient authority. | [`docs/specs/worker-spec.md`](../specs/worker-spec.md) |
| **`legatus`** | C++20 / CLI11 / nng | Local administrative CLI; communicates strictly with local `legatusd` over local IPC (`ipc://`). | [`docs/specs/worker-spec.md`](../specs/worker-spec.md) |
| **`legatus-studio`** | Java 21 / OpenJFX 21 / AtlantaFX | Desktop cockpit; 60Hz telemetry rendering, quarantine visual attestation, live task log stream. | [`docs/specs/studio-spec.md`](../specs/studio-spec.md) |

---

## 4. Network & Communication Architecture

### 4.1 Transport Boundary Protocols

The Legatus ecosystem standardizes on three network transport layers:

1. **Cluster REST Gateway (HTTPS / TLS 1.3):**
   * Exposed by `legatus-server` on port `8443` (`/api/v1/*`).
   * Manages worker quarantine registration (`/api/v1/workers/register`), ephemeral ticket exchanges (`/api/v1/auth/*`), function bytecode uploads (`/api/v1/functions`), and task submission.
   * Standardized JSON error envelope conforming to [`docs/specs/protocols/api-endpoint.md`](../specs/protocols/api-endpoint.md).
2. **Legatus Cluster Transport Protocol (LCTP over WSS / TLS 1.3):**
   * Full-duplex WebSocket connection exposed on `/ws/worker` for compute nodes and `/ws/studio` for telemetry cockpits.
   * Authentication enforced exclusively via single-use, 30-second ephemeral tickets (`ws_tkt_...`), preventing long-term static credential exposure in proxy access logs.
   * Canonical frame envelope: `type` (discriminator), `correlation_id` (UUID), `timestamp` (epoch ms), `payload` (scoped typed record).
3. **Cross-Platform Local IPC (`ipc://` via `nng`):**
   * High-speed, local-only Request/Reply framing (`NNG_PROTO_REQ0` / `NNG_PROTO_REP0`).
   * POSIX: Unix Domain Sockets at `/tmp/legatus_worker.ipc` (`0600` permissions).
   * Windows: Win32 Named Pipes at `ipc://legatus_worker_ipc` with explicit SDDL DACL (`D:(A;;GRGW;;;AU)`) to allow non-elevated operator interaction with the system service.

---

## 5. Security & Isolation Architecture

```
+---------------------------------------------------------------------------------------------------+
|                                   DEFENSE-IN-DEPTH SECURITY MODEL                                 |
|                                                                                                   |
|  [ LAYER 1: Transport Security ]                                                                  |
|  - TLS 1.3 mandatory across all REST and WebSocket connections (Zero plaintext HTTP/WS).         |
|  - Ephemeral tickets (ws_tkt_...) expire in 30 seconds and are burned atomically on read.         |
|                                                                                                   |
|  [ LAYER 2: Node Attestation & Identity ]                                                         |
|  - Worker generates Ed25519 keypair; registers public key; quarantined in PENDING_APPROVAL.      |
|  - Operator visually validates SHA-256 fingerprint & 3-word confirmation code (Tiger-Galaxy-77).  |
|  - Approved static secrets stored as Argon2id hashes (m=64MB, t=3, p=4) in server database.       |
|                                                                                                   |
|  [ LAYER 3: Storage & Secret Vaulting ]                                                           |
|  - Embedded H2 database enforces AES-128 page-level encryption at-rest (CIPHER=AES).              |
|  - Guest task secrets encrypted via AES-256-GCM using HKDF-SHA256 derived LEGATUS_MASTER_KEY.    |
|  - Decrypted secrets in memory are scrubbed immediately after transmission (explicit_bzero).      |
|                                                                                                   |
|  [ LAYER 4: Process & Runtime Isolation ]                                                         |
|  - Tasks execute in dedicated child processes (legatus-runner) isolated via reproc++.             |
|  - Two-stage kill escalation (terminate with 10s grace period -> SIGKILL / TerminateProcess).     |
|  - Solix VM executes inside pure bytecode sandbox: Zero OS syscalls, deterministic ARC memory.    |
+---------------------------------------------------------------------------------------------------+
```

---

## 6. End-to-End Operational Lifecycle Trace

```
Operator/Studio                  Legatus Server                   Legatusd Worker                 Legatus-Runner
     |                                 |                                 |                              |
     | [1] Register Node               |                                 |                              |
     |-------------------------------->|                                 |                              |
     |     (PENDING_APPROVAL)          |                                 |                              |
     |                                 |                                 |                              |
     | [2] Attest 3-Word Code          |                                 |                              |
     |     (Tiger-Galaxy-77)           |                                 |                              |
     |-------------------------------->| [3] Issue secret_token          |                              |
     |                                 |-------------------------------->|                              |
     |                                 |                                 |                              |
     |                                 | [4] Exchange Ticket & WSS Connect                              |
     |                                 |<--------------------------------|                              |
     |                                 |     SESSION_INIT -> SESSION_ACK |                              |
     |                                 |                                 |                              |
     | [5] Submit Task                 |                                 |                              |
     |-------------------------------->| [6] Bin-Pack & Dispatch         |                              |
     |                                 |     DISPATCH_TASK Frame         |                              |
     |                                 |-------------------------------->| [7] Spawn & Stream via Stdin |
     |                                 |                                 |----------------------------->|
     |                                 |                                 |                              | [8] Solix VM
     |                                 |                                 |                              |     Instruction
     |                                 |                                 |                              |     Metering
     |                                 |                                 | [9] Drain stdout & Exit      |
     |                                 |                                 |<-----------------------------|
     |                                 | [10] TASK_RESULT Frame          |                              |
     |                                 |<--------------------------------|                              |
     |                                 | [11] TASK_RESULT_ACK            |                              |
     |                                 |-------------------------------->|                              |
     | [12] Broadcast Telemetry        |                                 |                              |
     |<--------------------------------|                                 |                              |
```

---

## 7. Architecture Documentation Directory Structure

The architectural specifications in this directory detail each subsystem interaction:

```text
docs/architecture/
├── README.md                          # Master architectural navigation index
├── overview.md                        # Cluster-wide architectural topology and invariants (This file)
├── sequences/                         # Formal UML Sequence Diagrams & Operational Protocols
│   ├── README.md                      # Sequence specifications catalog
│   ├── server-operations.md           # Master key derivation, enrollment, dead-man's switch
│   ├── task-operations.md             # Scheduling, stdin streaming, execution, 2-stage abort
│   ├── telemetry-operations.md        # WebSocket streaming, 60Hz coalescing, log ring buffers
│   ├── user-operations.md             # Local CLI IPC commands, Studio attestation, bytecode deploy
│   └── worker-operations.md           # First boot, LCTP handshake, reconciliation, graceful drain
└── wireframes/
    ├── README.md                      # UI/UX design specifications catalog
    └── studio/                        # Legatus Studio UI wireframe workspace (in progress)
```
