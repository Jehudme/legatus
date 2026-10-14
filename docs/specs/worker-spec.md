# Legatus Worker Subsystem Specification

## Document Metadata
* **Subsystem:** `worker` (Compute Node Daemon, Operator Administration CLI & Sandboxed Task Runner)
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Systems Software Engineers, Runtime Engineers, Core Infrastructure Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Upstream Language Runtime:** Solix v1.0.0+ (`core`, `solixlib`, VM ISA `docs/spec/vm_isa.md`)
* **Related Specifications:**
  * `docs/specs/protocols/worker-wire.md`
  * `docs/specs/protocols/api-endpoint.md`
  * `docs/dev/decisions/0005-node-enrollment-and-attestation.md`
  * `docs/dev/decisions/0006-worker-reconnect-and-polling-cadence.md`
  * `docs/dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md`
  * `docs/dev/guidelines/authoring-specs.md`

---

## 1. System Architecture & Binary Topology

The Legatus worker subsystem operates as a distributed trio of discrete binary targets that cleanly decouple background cluster networking, interactive operator administration, and sandboxed guest task execution:

```
+---------------------------------------------------------------------------------------------------+
|                                      COMPUTE NODE / HOST OS                                       |
|                                                                                                   |
|   +-----------------------+      Cross-Platform nng IPC (REQ/REP)     +-----------------------+   |
|   |        legatus        | <=======================================> |       legatusd        |   |
|   |     (CLI Utility)     |    Linux/macOS: ipc:///tmp/legatusd.ipc   |  (Supervisor Daemon)  |   |
|   |  - CLI11 subcommands  |    Windows:     ipc://legatusd_ipc        |  - Outbound TLS 1.3   |   |
|   |  - up / down / drain  |                                           |  - Heartbeat Engine   |   |
|   |  - log / status / task|                                           |  - IPC REP0 Server    |   |
|   +-----------------------+                                           +-----------+-----------+   |
|                                                                                   |               |
|                                           +---------------------------------------+               |
|                                           | Spawns & Supervises via reproc++                      |
|                                           | Stdio Anonymous IPC Pipes                             |
|                                           v                                                       |
|                        +-------------------------------------+                                    |
|                        |          legatus-runner             |                                    |
|                        |      (Ephemeral Child Process)      |                                    |
|                        |  - Private Heap / Stack Arenas      |                                    |
|                        |  - ISA Opcode Budget Metering       |                                    |
|                        |  - Stdio Pipe Result Serialization  |                                    |
|                        +-------------------------------------+                                    |
+---------------------------------------------------|-----------------------------------------------+
                                                    |
                                                    | Outbound-Only TLS 1.3
                                                    | HTTPS REST + Secure WebSocket (wss://)
                                                    v
                                 +-------------------------------------+
                                 |         LEGATUS SERVER              |
                                 |  - Cluster Scheduler & Broker       |
                                 |  - Studio Cockpit Stream Engine     |
                                 +-------------------------------------+
```

### 1.1 The Three Discrete Executables

1. **`legatusd` (Supervisor Daemon):**
   * Long-running background system daemon process.
   * Maintains persistent, outbound-only TLS 1.3 WebSocket connections to the centralized Legatus Server broker.
   * Emits periodic telemetry heartbeats with randomized uniform jitter ($5000\text{ ms} \pm 500\text{ ms}$).
   * Runs an `nng` (nanomsg-next-gen) local IPC server using `NNG_PROTO_REP0` to serve administrative requests.
   * Supervises child process lifecycles via `reproc++`, enforcing two-stage termination escalation (`terminate` $\to$ `kill`).

2. **`legatus` (Operator CLI):**
   * Lightweight interactive administrative command-line utility.
   * Contains zero direct network connections to the cloud broker or server database.
   * Uses `NNG_PROTO_REQ0` over local `nng` IPC to send action commands to `legatusd` and format terminal output.

3. **`legatus-runner` (Guest Task Runner):**
   * Short-lived, isolated child process launched on demand by `legatusd`.
   * Allocates an independent virtual memory space containing private heap and stack arenas.
   * Executes compiled Solix bytecode with active instruction metering and traps abnormal runtime exits.
   * Emits execution output and terminal statistics to stdout before terminating cleanly.

---

## 2. Technology Stack & Component Responsibilities

The subsystem relies on focused, modular components managed deterministically via standard build configurations:

| Component Role | Selected Dependency | Version / Standard | Functional Mandate |
| :--- | :--- | :--- | :--- |
| **CLI Argument Parsing** | `CLI11` | `v2.4+` | Type-safe subcommand parsing, flag validation, and terminal formatting for the `legatus` CLI and `legatus-runner`. |
| **Structured Logging** | `spdlog` | `v1.14+` | Thread-safe, lock-free logging across stdout and rotating disk sinks. Dynamic in-memory log level mutation. |
| **Local Daemon IPC** | `nng` (nanomsg-next-gen) | `v1.8+` | Bridges CLI and daemon via local IPC. Abstracted over Unix Domain Sockets on POSIX and Named Pipes on Windows. |
| **REST & WebSocket Transport** | `IXWebSocket` | `v11.4+` | Drives outbound HTTPS registration/authentication and persistent TLS 1.3 WebSocket streams. |
| **Process Supervision** | `reproc++` | `v14.2+` | Subprocess spawning, non-blocking stream redirection, and two-stage process escalation (`terminate` $\to$ `kill`). |
| **JSON Serialization** | `nlohmann/json` | `v3.11+` | Serializes and parses all local IPC frames, WebSocket messages, and runner result envelopes. |
| **Configuration Parsing** | `tomlplusplus` (`toml++`) | `v3.4+` | Reads, validates, and updates persistent node settings in `worker.toml`. |
| **Cryptography Engine** | `OpenSSL` | `3.0+` (LTS) | Generates Ed25519 keypairs, produces SHA-256 fingerprints, and secures TLS 1.3 transport. |
| **Test Verification** | `Catch2` | `v3.5+` | Houses the complete unit and integration test matrix. |
| **Traffic Simulation** | `ix::WebSocketServer` | `v11.4+` | Provides an in-process mock server harness for automated integration testing. |

---

## 3. Filesystem & Operating System Boundaries

Compute nodes **MUST** resolve configuration files, state directories, sockets, and binary locations dynamically without hardcoding operating system paths.

### 3.1 Path Resolution Specification

| Resource | Target OS | Resolved Default Location | Override Mechanism |
| :--- | :--- | :--- | :--- |
| **Configuration File** (`worker.toml`) | Linux / POSIX<br>macOS<br>Windows | `/etc/legatus/worker.toml`<br>`/Library/Application Support/Legatus/worker.toml`<br>`%ProgramData%\Legatus\worker.toml` | CLI flag: `-c, --config <path>` |
| **State & Working Directory** | Linux / POSIX<br>macOS<br>Windows | `/var/lib/legatus/`<br>`/Library/Application Support/Legatus/data/`<br>`%ProgramData%\Legatus\data\` | Configuration key: `storage.state_dir` |
| **Local Log File** | Linux / POSIX<br>macOS<br>Windows | `/var/log/legatus/worker.log`<br>`/var/log/legatus/worker.log`<br>`%ProgramData%\Legatus\logs\worker.log` | Configuration key: `logging.log_directory` |
| **Local IPC Endpoint** (`nng` URI) | Linux / POSIX<br>macOS<br>Windows | `ipc:///tmp/legatus_worker.ipc`<br>`ipc:///tmp/legatus_worker.ipc`<br>`ipc://legatus_worker_ipc` (Named Pipe `\\.\pipe\legatus_worker_ipc`) | Environment: `LEGATUS_IPC_ENDPOINT` |
| **Task Runner Binary** (`legatus-runner`) | Linux / macOS<br>Windows | Co-located in same directory as `legatusd`<br>`<exe_dir>/legatus-runner`<br>`<exe_dir>\legatus-runner.exe` | Dynamic path resolution relative to process binary location |

---

## 4. Contractual State Machines & Data Flows

### 4.1 Local Daemon IPC Protocol & Administrative CLI

The `legatus` CLI **MUST NOT** interface directly with the centralized cloud server or modify node database tables. It communicates with `legatusd` exclusively through request/reply patterns (`NNG_PROTO_REQ0` on the CLI client, `NNG_PROTO_REP0` on the daemon).

```
   legatus CLI (REQ0)                              legatusd Daemon (REP0)
           |                                                 |
           | nng_send({ "action": "SET_LOG_LEVEL", ... })    |
           | ----------------------------------------------> |
           |                                                 | Applies change dynamically in RAM
           |                                                 | (spdlog::set_level)
           | nng_recv({ "status": "SUCCESS" })               |
           | <---------------------------------------------- |
```

#### CLI Command Hierarchy (`CLI11`)

* **`legatus up [-d, --daemon] [-c, --config <path>]`**:
  Initializes and starts the daemon supervisor. If `-d` is specified, the daemon detaches and executes as a background system service.
* **`legatus down [-f, --force]`**:
  Directs the active daemon to terminate. If `--force` is omitted, the daemon enters a clean shutdown sequence, completing in-flight dispatches before stopping.
* **`legatus drain [--timeout <sec>] [--disable]`**:
  Toggles node drain mode. When draining, the node reports zero spare capacity in heartbeats, preventing the scheduler from dispatching new tasks while current tasks complete.
* **`legatus status [--json]`**:
  Queries and renders real-time daemon metrics, including active uptime, cloud WebSocket connection status, assigned cluster pool, and active task tables.
* **`legatus log level <trace|debug|info|warn|error>`**:
  Dynamically mutates daemon logging verbosity in memory without requiring a daemon restart.
* **`legatus log show [--lines <n>]`**:
  Prints configured log paths, active sinks, and the tail output of the local log file.
* **`legatus tasks list`**:
  Outputs a tabular view of all actively supervised child processes (Task ID, OS PID, instruction counter, memory footprint, elapsed runtime).
* **`legatus tasks kill <task_id>`**:
  Initiates the two-stage early termination sequence against a running task.

#### Local IPC Framing & Schema Specification

All communication exchanged over the `nng` transport **MUST** use UTF-8 encoded JSON framing adhering to the following structural schemas:

##### Generic Request Envelope:
```json
{
  "action": "ACTION_NAME_STRING",
  "params": {}
}
```

##### Generic Response Envelope:
```json
{
  "status": "SUCCESS | ERROR",
  "data": {},
  "error": {
    "code": "ERROR_CODE_STRING",
    "message": "Human-readable diagnostic description."
  }
}
```

##### Formal IPC Action Specifications:

| Action Identifier | Parameters Schema | Response Data Schema | Description |
| :--- | :--- | :--- | :--- |
| **`DAEMON_STATUS`** | `{}` | `{"version": string, "uptime_seconds": int64, "server_endpoint": string, "websocket_connected": bool, "assigned_cluster": string, "draining": bool, "active_tasks_count": int32, "log_level": string, "allocatable_cores": int32, "max_memory_bytes": int64}` | Fetches high-level daemon state and metrics. |
| **`DAEMON_DOWN`** | `{"force": bool}` | `{"terminating": bool, "grace_period_ms": int32}` | Signals the daemon process to exit. |
| **`DAEMON_DRAIN`** | `{"enabled": bool, "timeout_seconds": int32}` | `{"draining": bool, "active_tasks_remaining": int32}` | Adjusts scheduling acceptance mode for maintenance. |
| **`SET_LOG_LEVEL`** | `{"level": string}` | `{"previous_level": string, "active_level": string}` | Modifies `spdlog` filter level in memory. |
| **`GET_LOG_CONFIG`**| `{}` | `{"active_level": string, "log_file_path": string, "max_file_size_mb": int32, "max_backup_files": int32}` | Retrieves disk log routing and configuration. |
| **`ABORT_TASK`** | `{"task_id": string, "reason": string}` | `{"task_id": string, "escalation_state": string}` | Triggers termination against a specific child process. |

---

### 4.2 Node Enrollment & Operator Attestation Protocol

Compute nodes employ a **"Connect First, Approve Later"** quarantine enrollment model. Fresh workers require no pre-shared credentials or configuration tokens to initialize.

```
[ New Worker Node (legatusd) ]        [ Legatus Server Broker ]            [ Legatus Studio Cockpit ]
              |                                   |                                     |
              | 1. Generate Ed25519 Keypair       |                                     |
              | 2. POST /api/v1/workers/register  |                                     |
              |    (Hardware profile + PubKey)    |                                     |
              |---------------------------------->|                                     |
              |                                   | State: PENDING_APPROVAL             |
              |                                   | 3. Push Event: NEW_WORKER_REQUEST   |
              |                                   |------------------------------------>|
              |                                   |                                     | [Modal Displays:
              | 4. CLI renders Fingerprint:       |                                     |  "wk-042 (16 cores, 32GB)
              |    "4E:8A:BC... (Tiger-Galaxy-77)"|                                     |   4E:8A:BC... (Tiger-Galaxy-77)"]
              |                                   |                                     |
              | 5. Polls status every 3000ms      |                                     | 5. Operator verifies
              |    GET /enrollments/{id}/status   |                                     |    fingerprints & clicks [Approve]
              |---------------------------------->|                                     |----------------------------
              |                                   | 6. Server generates secret_token    |
              | 7. Returns 200 OK + secret_token  |    (Argon2id salted hash in DB)     |
              |<----------------------------------|                                     |
              |                                   |                                     |
              | 8. Atomically writes worker.toml  |                                     |
              |    Advances to Auth & WS Phase    |                                     |
```

#### Cryptographic Key Generation & Visual Fingerprint Standards

1. **Key Generation:**
   * The node **MUST** generate an asymmetric **Ed25519** keypair using OpenSSL 3.0 (`EVP_PKEY_ED25519`).
   * The 32-byte raw public key and 32-byte raw private key **MUST** be encoded in standard RFC 4648 Base64 strings.
2. **Cryptographic Fingerprint:**
   * The public key buffer **MUST** be hashed using SHA-256 ($\text{SHA256}(\text{public\_key})$).
   * The first 8 bytes of the digest **MUST** be formatted as an uppercase, colon-separated hexadecimal string: `XX:XX:XX:XX:XX:XX:XX:XX`.
3. **Visual Confirmation Code:**
   * A deterministic three-word code (e.g., `Tiger-Galaxy-77`) **MUST** be derived from the lower bytes of the digest to allow human verification in the Studio cockpit.
4. **Quarantine State Restrictions:**
   * While a node remains in `PENDING_APPROVAL`, the server broker **SHALL NOT** dispatch jobs to it.
   * Quarantined nodes **MUST NOT** inspect neighboring cluster topology, access task logs, or consume broker resources.
   * Registration requests **MUST** expire automatically after 15 minutes ($900\text{ seconds}$) if not approved by an operator.

---

### 4.3 Authentication & Ephemeral Ticket Exchange

Legatus decouples static node secrets from live transport connections through a **Two-Tier Ephemeral Ticket Exchange**.

```
[ Worker Daemon (legatusd) ]                                      [ Legatus Server ]
              |                                                                 |
              | 1. POST /api/v1/auth/worker (TLS 1.3)                           |
              |    { worker_id, secret_token, allocatable_capacity }            |
              |---------------------------------------------------------------->|
              |                                                                 | 1. Verify Argon2id hash
              |                                                                 | 2. Check cluster quota
              |                                                                 | 3. Issue ephemeral ticket
              | 2. 200 OK                                                       |    (In-memory, TTL: 30s)
              |    { ticket: "ws_tkt_...", websocket_url: "/ws/worker" }        |
              |<----------------------------------------------------------------|
              |                                                                 |
              | 3. GET /ws/worker?ticket=ws_tkt_... (Upgrade: websocket)        |
              |---------------------------------------------------------------->|
              |                                                                 | 1. Validate ticket
              |                                                                 | 2. BURN ON READ (purge)
              | 4. 101 Switching Protocols                                      |
              |<----------------------------------------------------------------|
              |                                                                 |
              | === Established Full-Duplex Authenticated Transport ===         |
```

#### Authentication Invariants

* **Node Secret Security:** Secrets (`secret_token`) stored in the server database **MUST** be salted and hashed using **Argon2id** (memory: $64\text{ MB}$, iterations: $3$, parallelism: $4$). Secrets **SHALL NOT** be stored in plaintext.
* **Mandatory TLS 1.3:** All HTTP and WebSocket exchanges **MUST** require TLS 1.3 or higher. Plaintext connections (`http://` or `ws://`) **MUST** be rejected immediately.
* **Burn-on-Read Ticket Lifecycle:** Ephemeral tickets **MUST** have a maximum time-to-live (TTL) of **30 seconds**. When the HTTP `101 Switching Protocols` handshake completes, the broker **MUST** validate the ticket and delete it from cache immediately. Any subsequent replay attempt **MUST** yield `401 Unauthorized`.
* **Resource Quota Self-Declaration:** During `POST /api/v1/auth/worker`, the worker **MUST** declare its allocatable capacity (`allocatable_cores`, `max_memory_bytes`, `max_instruction_budget_per_task`), which the server broker uses for job scheduling.

---

### 4.4 Live Transport & Connection Lifecycle FSM

All persistent streaming communication uses an outbound `IXWebSocket` connection over TLS 1.3. The worker transport client transitions through five deterministic operational states:

```
    ┌────────────────┐
    │  DISCONNECTED  │ ◄───────────────────────────────┐
    └───────┬────────┘                                 │
            │ (Connect / Exponential Jittered Backoff) │
            ▼                                          │
    ┌────────────────┐                                 │ (Socket Error /
    │   CONNECTING   │                                 │  TCP Reset /
    └───────┬────────┘                                 │  Timeout)
            │ (101 Switching Protocols)                │
            ▼                                          │
    ┌────────────────┐                                 │
    │  NEGOTIATING   │ ────────────────────────────────┤
    └───────┬────────┘ (Negotiation Timeout: 5000ms)   │
            │ (SESSION_INIT -> SESSION_ACK)            │
            ▼                                          │
    ┌────────────────┐                                 │
    │  OPERATIONAL   │ ────────────────────────────────┤
    └───────┬────────┘                                 │
            │ (SIGINT / Shutdown / Drain Complete)     │
            ▼                                          │
    ┌────────────────┐                                 │
    │  TERMINATING   │ ────────────────────────────────┘
    └────────────────┘ (WS Close Code 1000 Sent)
```

1. **`DISCONNECTED`**: No active transport socket exists. Reconnection is governed by exponential backoff with full uniform jitter:
   $$t_{\text{backoff}} = \min\left(30, \; 2^{\text{retries}}\right) + \text{UniformRandom}(0, 1) \quad [\text{seconds}]$$
2. **`CONNECTING`**: TCP socket established; TLS 1.3 handshake negotiation completed; HTTP `101 Switching Protocols` upgrade request in flight.
3. **`NEGOTIATING`**: Transport upgraded to WebSocket framing. The worker daemon immediately transmits `SESSION_INIT`. All incoming compute task dispatches **MUST** be blocked until a valid `SESSION_ACK` is parsed and accepted.
4. **`OPERATIONAL`**: Active healthy session. The worker emits `HEARTBEAT` every $5000\text{ ms} \pm 500\text{ ms}$, processes task dispatch frames, forwards progress metrics, and executes cancellations.
5. **`TERMINATING`**: Clean session closure. The supervisor flushes pending task frames, emits a WebSocket Close Frame (`1000 Normal Closure`), and terminates cleanly.

#### Telemetry Invariants & Dead-Man's Timers

* **Transmission Cadence:** The worker **MUST** transmit a `HEARTBEAT` frame every **5000 milliseconds** ($\pm 500\text{ ms}$ randomized jitter).
* **Acknowledgement Deadline:** The broker **MUST** respond to each `HEARTBEAT` with a `HEARTBEAT_ACK` frame within **2000 milliseconds**.
* **Server Dead-Man's Window:** If the broker receives no traffic from a worker for **15000 milliseconds** ($3 \times t_{\text{interval}}$), the broker **MUST** mark the node `OFFLINE`, close the socket, and re-enqueue in-flight tasks.
* **Client Dead-Man's Window:** If the worker receives no `HEARTBEAT_ACK` or broker traffic for **15000 milliseconds**, the worker **MUST** abort the socket and transition to `DISCONNECTED`.

---

## 5. Resource Ceilings, Execution Sandboxing & Memory Model

To guarantee that guest tasks cannot compromise the supervisor daemon or corrupt host memory across invocations, tasks execute strictly out-of-process in `legatus-runner`.

### 5.1 Out-of-Process Task Execution Topology

```
+-------------------------------------------------------------------------+
|                  LEGATUS WORKER SUPERVISOR (Parent)                     |
|                                                                         |
|   Spawns child runner via reproc++                                      |
|   CLI Parameters:                                                       |
|     legatus-runner --task-id <id> --budget <num> --max-mem <bytes>      |
|                    <envelope_json_string>                               |
+------------------------------------+------------------------------------+
                                     |
                  Anonymous OS Pipes | Process Virtual Memory Boundary
                                     |
+------------------------------------v------------------------------------+
|                  LEGATUS RUNNER PROCESS (Child)                         |
|                                                                         |
|   1. Extracts execution envelope from command-line arguments            |
|   2. Decodes compiled Solix bytecode payload                            |
|   3. Initializes solix::RuntimeOptions:                                 |
|      - opts.program_args = { envelope_json_string }                     |
|      - opts.heap_capacity = max_memory_bytes / 8                        |
|   4. Invokes Solix VM entry point: static int32 main(char[][] args)     |
|   5. Guest script reads JSON input from args[0]                         |
|   6. Result emitted to stdout; exit code returned to supervisor         |
+-------------------------------------------------------------------------+
```

### 5.2 Execution Envelope & Calling Convention

1. **Parameter Assembly:** The supervisor combines task inputs, environment settings, and decrypted secrets into a structured JSON envelope string passed in `argv[4]`.
2. **Solix VM Invocation Contract:** The guest entry point adheres to the contract:
   $$\texttt{static int32 main(char[][] args)}$$
   The runner passes the JSON envelope string in index 0 of program arguments. The VM allocates this as an array of character strings on the guest heap prior to entry.
3. **Guest Data Flow:**
   * The guest program accesses input data via `args[0]`.
   * Execution output is emitted to the standard output pipe as a single UTF-8 JSON object.
   * Standard error is redirected to the supervisor's diagnostic capture buffer.

### 5.3 Mathematical Memory Arena Bounds

The runner environment enforces strict virtual memory limits on child processes:

* **Maximum Virtual Memory Ceiling ($M_{\text{max}}$):** Configured per task dispatch, upper bounded by node configuration:
  $$M_{\text{max}} \le \text{allocatable\_capacity.max\_memory\_bytes} \quad (\text{default: } 134,217,728\text{ bytes / } 128\text{ MB})$$
* **Stack Arena Limit ($M_{\text{stack}}$):** Fixed at $8,388,608\text{ bytes}$ ($8\text{ MB}$).
* **Heap Arena Ceiling ($M_{\text{heap}}$):** Derived directly as:
  $$M_{\text{heap}} = M_{\text{max}} - M_{\text{stack}}$$
* **Allocation Bounds Violation:** Any memory allocation request exceeding $M_{\text{max}}$ **MUST** immediately trigger a `TRAP_OUT_OF_MEMORY` event, terminating the runner process with exit code `71` (`EX_OSERR`).

### 5.4 Instruction Budget Accounting (ISA-Level Metering)

To guarantee deterministic execution time independent of host CPU frequency and architectural differences:

1. **Quota Allocation:** Each task dispatch specifies an integer instruction budget $I_{\text{budget}} \in [10^3, 10^9]$.
2. **Opcode Accounting:** The Solix VM decrements its instruction counter during execution. Budget verification **MUST** occur on all branching and invocation opcodes:
   * Branching opcodes: `OpCode::JUMP`, `OpCode::JUMP_IF_FALSE`, `OpCode::JUMP_IF_TRUE`.
   * Invocation opcodes: `OpCode::CALL`, `OpCode::CALL_VIRTUAL`, `OpCode::CALL_INTERFACE`.
3. **Exhaustion Semantics:** When remaining instructions reach $0$, the engine **MUST NOT** crash or invoke `std::abort()`. The runner **MUST**:
   * Halt execution immediately.
   * Flush executed opcode metrics to the stdout pipe.
   * Return terminal exit code `75` (`EX_TEMPFAIL`).

### 5.5 In-Memory Secret Sanitization

* Sensitive task inputs, environment variables, and decrypted credentials **MUST** reside exclusively in RAM and **SHALL NOT** be written to disk.
* Upon process exit or unhandled termination, the runner **MUST** scrub all memory buffers containing credentials using `explicit_bzero` on POSIX systems or `SecureZeroMemory` on Windows prior to deallocation. Standard `memset` **MUST NOT** be used.

---

## 6. Failure Modes, Traps & Exit Codes

### 6.1 Early Termination & Two-Stage Process Escalation

When an operator triggers task cancellation (via CLI `legatus tasks kill <id>` or through an `ABORT_TASK` WebSocket frame), the supervisor executes a two-stage process escalation sequence managed by `reproc++`:

```
Server Broker / CLI                           Supervisor Daemon                            Child Runner
      |                                             |                                          |
      | --- ABORT_TASK (task_id, reason) ---------> |                                          |
      |                                             | 1. Stage 1: Cooperative Stop             |
      |                                             |    reproc::stop::terminate              |
      |                                             | ---------------------------------------> |
      |                                             |    (Linux: SIGTERM, Win: CTRL_BREAK)     |
      |                                             |                                          | [VM unwinds stack,
      |                                             |                                          |  runs finally blocks,
      |                                             |                                          |  exits 0 / 130]
      |                                             | 2. Await exit (10s Grace Period)         |
      |                                             |                                          |
      |                                             | === SCENARIO A: Exits within 10s ======= |
      |                                             |<-----------------------------------------|
      | <--- TASK_RESULT (status: ABORTED) -------- |                                          |
      |                                             |                                          |
      |                                             | === SCENARIO B: Hangs (Timeout expired) = |
      |                                             | 3. Stage 2: Hard Process Kill            |
      |                                             |    reproc::stop::kill                    |
      |                                             | ---------------------------------------> | [Kernel destroys
      |                                             |                                          |  process in RAM]
      |                                             |<-----------------------------------------|
      | <--- TASK_RESULT (status: KILLED_FORCIBLY) -|                                          |
```

#### Escalation Rules & Invariants

1. **Target Verification:** The supervisor verifies the task in its process registry. If `task_id` is unknown or already terminated, the request is ignored without error.
2. **Stage 1 (Cooperative Stop):**
   * The supervisor executes `reproc::stop::terminate`.
   * **Linux / POSIX:** The OS delivers `SIGTERM`. The child runner traps the signal, breaks the VM instruction loop at the next branch instruction, and exits cleanly.
   * **Windows:** The OS emits `CTRL_BREAK_EVENT` and closes the `stdin` pipe. The child's console control handler flags execution termination.
3. **Grace Period (10 Seconds):**
   * The supervisor waits for child process termination for up to **10,000 milliseconds**.
4. **Stage 2 (Hard Process Destruction):**
   * If the child process is hung or fails to exit after 10 seconds, the supervisor executes `reproc::stop::kill` (`SIGKILL` on POSIX, `TerminateProcess()` on Windows).
   * The OS kernel terminates the process immediately.
5. **Terminal Frame Emission:**
   * The supervisor removes the task from its registry and transmits a `TASK_RESULT` frame reporting status `ABORTED` (if Stage 1 succeeded) or `KILLED_FORCIBLY` (if Stage 2 was required).

### 6.2 Deterministic Exit Code Matrix

| Exit Code | Standard Mnemonic | Trigger Condition | System Action |
| :--- | :--- | :--- | :--- |
| `0` | `EX_OK` | Clean guest completion; `main()` returned 0 | Results parsed and transmitted in `TASK_RESULT`. |
| `1` | `EX_GENERAL` | Guest uncaught exception or non-zero exit | Error diagnostic captured and reported. |
| `71` | `EX_OSERR` | Out of memory; memory arena bounds violated | Task halted; reported as `ERR_OUT_OF_MEMORY`. |
| `75` | `EX_TEMPFAIL` | Instruction budget exhausted | Task trapped; reported as `ERR_INSTRUCTION_BUDGET_EXCEEDED`. |
| `124` | `EX_TIMEOUT` | Wall-clock execution timeout surpassed | Task stopped; reported as `ERR_TIMEOUT`. |
| `130` | `EX_INTERRUPT`| Clean early termination via cooperative Stage 1 | Task reported as `ABORTED`. |
| `137` | `EX_KILL` | Hard process kill via Stage 2 SIGKILL / Win32 | Process reclaimed; reported as `KILLED_FORCIBLY`. |
| `139` | `EX_SEGV` | Process crashed abnormally via kernel `SIGSEGV` | Process crashed; reported as `CRASHED`. |

---

## 7. Node Configuration Schema (`worker.toml`)

The worker daemon stores its persistent identity and network configuration in a TOML v1.0.0 file (`worker.toml`):

```toml
[server]
endpoint = "https://cloud.legatus.dev:8443"
tls_verify_peer = true
ca_cert_path = "/etc/ssl/certs/ca-certificates.crt"

[identity]
worker_id = "worker-node-us-east-042"
secret_token = "wk_sec_99a8b7c6d5e4f3a2b10f"
public_key = "MCowBQYDK2VwAyEA7f..."

[resources]
allocatable_cores = 16
max_memory_bytes = 17179869184
max_instruction_budget_per_task = 50000000

[telemetry]
heartbeat_interval_ms = 5000
heartbeat_timeout_ms = 15000

[logging]
level = "info"
log_directory = "/var/log/legatus"
max_file_size_mb = 50
max_backup_files = 5
```

### 7.1 Configuration Keys & Constraints

| Section | Key | Type | Default | Description |
| :--- | :--- | :--- | :--- | :--- |
| `[server]` | `endpoint` | String | (Required) | Base URL of the Legatus Server (`https://host:port`). |
| `[server]` | `tls_verify_peer` | Boolean | `true` | Enforces certificate chain verification against CA roots. |
| `[server]` | `ca_cert_path` | String | `""` | Custom trusted CA bundle path (uses system trust store if empty). |
| `[identity]` | `worker_id` | String | Hostname | Unique node identifier within the cluster. |
| `[identity]` | `secret_token` | String | `""` | Pre-shared secret generated upon enrollment approval. |
| `[identity]` | `public_key` | String | `""` | Base64-encoded Ed25519 public key. |
| `[resources]` | `allocatable_cores` | Integer | System Cores | Maximum concurrent CPU threads scheduled to this node. |
| `[resources]` | `max_memory_bytes` | Integer | 50% System RAM | Cumulative memory ceiling dedicated across all child tasks. |
| `[resources]` | `max_instruction_budget_per_task` | Integer | `50000000` | Maximum instruction count accepted for a single task. |
| `[telemetry]` | `heartbeat_interval_ms`| Integer | `5000` | Cadence for emitting `HEARTBEAT` frames. |
| `[telemetry]` | `heartbeat_timeout_ms` | Integer | `15000` | Socket timeout if no server heartbeat response arrives. |
| `[logging]` | `level` | String | `"info"` | Log level filter (`trace`, `debug`, `info`, `warn`, `error`). |
| `[logging]` | `max_file_size_mb` | Integer | `50` | Maximum size per log file before rotation. |
| `[logging]` | `max_backup_files` | Integer | `5` | Maximum number of rotated log archives retained on disk. |

---

## 8. Verification & Compliance Checklist

Reviewers and platform engineers **MUST** verify the following audit criteria:

- [ ] **Zero Implementation Code:** Document contains zero multi-line C++, Java, or Solix code blocks; contracts are expressed via tables, ASCII diagrams, and mathematical bounds.
- [ ] **RFC 2119 Capitalization:** All normative constraints use bold, capitalized RFC 2119 keywords.
- [ ] **Memory & Resource Bounds:** Concrete byte ceilings and instruction quotas are formally defined.
- [ ] **Exit Code Determinism:** All terminal states map to standard POSIX/BSD exit codes.
- [ ] **Cross-Platform Parity:** Path resolution, IPC, and termination escalation are defined across Linux, macOS, and Windows.
- [ ] **Sanitization Mandate:** Explicit zeroing (`explicit_bzero` / `SecureZeroMemory`) is mandated for all secret memory buffers.