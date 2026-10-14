# Legatus Worker WebSocket Wire Protocol Specification

## Document Metadata
* **Protocol Name:** Legatus Compute Transport Protocol (LCTP)
* **Subsystem:** `protocols/worker-wire`
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Systems Software Engineers, Distributed Infrastructure Engineers, Protocol Architects
* **Transport:** Full-Duplex Secure WebSocket (`wss://`) over TLS 1.3
* **Payload Encoding:** UTF-8 Encoded JSON (RFC 8259)
* **Maximum Frame Size:** 33,554,432 bytes (32 MB)
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Upstream Virtual Machine ISA:** Solix v1.0.0+ (`core`, VM ISA `docs/spec/vm_isa.md`)
* **Related Specifications:**
  * `docs/specs/worker-spec.md`
  * `docs/specs/protocols/api-endpoint.md`
  * `docs/dev/decisions/0005-node-enrollment-and-attestation.md`
  * `docs/dev/decisions/0006-worker-reconnect-and-polling-cadence.md`
  * `docs/dev/guidelines/authoring-protocols.md`

---

## 1. Protocol Architecture & Connection Lifecycle

The Legatus Worker Wire Protocol operates as an outbound-only, persistent, bi-directional event stream. The compute node daemon (`legatusd`) acts exclusively as the transport client initiator, connecting outbound to the centralized broker gateway (`legatus-server`). This architecture eliminates any requirement for inbound firewall ports, NAT punching, or overlay VPN tunnels on compute nodes.

```
       LEGATUS WORKER (Client)                                  LEGATUS SERVER (Broker)
                  │                                                         │
                  │ ── 1. HTTP/1.1 Upgrade Request (TLS 1.3) ─────────────> │
                  │       GET /ws/worker?ticket=ws_tkt_...                  │
                  │       Host: cloud.legatus.dev:8443                      │
                  │       Upgrade: websocket                                │
                  │       Connection: Upgrade                               │
                  │       Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==       │
                  │       Sec-WebSocket-Version: 13                         │
                  │       X-Legatus-Worker-Version: 1.0.0                   │
                  │                                                         │
                  │                                                         │ ── Server validates ticket
                  │                                                         │    Burns ticket from cache
                  │ <── 2. HTTP/1.1 101 Switching Protocols ─────────────── │
                  │       Upgrade: websocket                                │
                  │       Connection: Upgrade                               │
                  │       Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=│
                  │                                                         │
                  │ === Full-Duplex Encrypted Channel Established ===       │
                  │                                                         │
[NEGOTIATION]     │ ── 3. SESSION_INIT ───────────────────────────────────> │
                  │ <── 4. SESSION_ACK ──────────────────────────────────── │ [SESSION ACTIVE]
                  │                                                         │
[OPERATIONAL]     │ ── 5. HEARTBEAT (Every 5000ms +/- 500ms Jitter) ──────> │
                  │ <── 6. HEARTBEAT_ACK ────────────────────────────────── │
                  │                                                         │
                  │ <── 7. DISPATCH_TASK ────────────────────────────────── │ [DISPATCH QUEUE]
[EXECUTING]       │ ── 8. TASK_PROGRESS (Optional Checkpoints) ───────────> │
                  │ ── 9. TASK_RESULT (Terminal Frame) ───────────────────> │
                  │                                                         │
[INTERRUPT]       │ <── 10. ABORT_TASK (Cancellation Forwarding) ────────── │
                  │ ── 11. TASK_RESULT (status=ABORTED) ──────────────────> │
                  │                                                         │
[TEARDOWN]        │ ── 12. WS Close Frame (Code 1000 / Normal) ───────────> │
                  │ <── 13. WS Close Frame Acknowledgement ──────────────── │
```

---

## 2. Connection Finite State Machine (FSM)

The worker transport client transitions through five deterministic operational states:

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

1. **`DISCONNECTED`**: No active transport socket exists. Reconnection attempts **MUST** be governed by exponential backoff with full uniform random jitter ($t_{\text{backoff}} = \min(30, 2^{\text{retries}}) + \text{UniformRandom}(0, 1)$ seconds).
2. **`CONNECTING`**: The TCP socket is open, TLS 1.3 handshake negotiation has completed, and the HTTP `101 Switching Protocols` upgrade request is in flight.
3. **`NEGOTIATING`**: Transport upgraded. The client immediately transmits `SESSION_INIT`. All incoming compute dispatch is blocked until a successful `SESSION_ACK` is parsed and validated.
4. **`OPERATIONAL`**: Active healthy session. The worker emits `HEARTBEAT` every $5000\text{ ms} \pm 500\text{ ms}$, handles task execution pipelines, forwards progress metrics, and executes task interruptions.
5. **`TERMINATING`**: Clean session closure. The worker supervisor flushes pending task frames, emits a WebSocket Close Frame (`1000 Normal Closure`), and terminates the process.

---

## 3. Base Frame Envelope & Field Invariants

Every message transmitted across the WebSocket channel **MUST** be formatted as a single UTF-8 JSON object adhering to the canonical framing envelope:

```json
{
  "type": "FRAME_TYPE_STRING",
  "id": "e2b3c4d5-6f7a-8b9c-0d1e-2f3a4b5c6d7e",
  "timestamp": 1728907200000,
  "payload": {}
}
```

### 3.1 Envelope JSON Schema

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "LCTPWireEnvelope",
  "type": "object",
  "required": ["type", "id", "timestamp", "payload"],
  "additionalProperties": false,
  "properties": {
    "type": {
      "type": "string",
      "pattern": "^[A-Z0-9_]{3,64}$",
      "description": "Upper snake-case message type discriminator."
    },
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "RFC 4122 compliant UUIDv4 correlation identifier."
    },
    "timestamp": {
      "type": "integer",
      "minimum": 0,
      "description": "Monotonic UTC Unix epoch timestamp in milliseconds."
    },
    "payload": {
      "type": "object",
      "description": "Contextual payload container mapped to the frame type. Must be {} if empty."
    }
  }
}
```

### 3.2 Envelope Header Field Specifications

| Envelope Field | Type | Constraint | Description |
| :--- | :--- | :--- | :--- |
| `type` | `String` | Required, Upper Snake Case | Canonical frame discriminator (e.g., `SESSION_INIT`, `DISPATCH_TASK`). |
| `id` | `String` | Required, UUIDv4 | Universally unique correlation identifier generated per frame for logging and tracing. |
| `timestamp` | `Int64` | Required, Positive Integer | UTC Unix epoch timestamp in milliseconds when the frame was constructed in memory. |
| `payload` | `Object` | Required, JSON Object | Payload data container scoped strictly to the specified frame `type`. Must be `{}` if empty. |

### 3.3 Wire Transmission Invariants

* **Strict Frame Size Limits:** The maximum single WebSocket frame size accepted by the server or worker is **32 Megabytes** (`33,554,432 bytes`). Messages exceeding this limit **MUST** trigger immediate connection termination with Close Code `1009` (Message Too Big).
* **Text Opcode Exclusivity:** All protocol communication uses WebSocket Opcode `0x1` (Text Frame). Binary Opcode `0x2` is reserved for future compressed telemetry channels.
* **Ping / Pong Heartbeats:** Independent of protocol-level `HEARTBEAT` frames, both endpoints **MUST** reply immediately to transport-level WebSocket Ping control frames (`Opcode 0x9`) with matching Pong frames (`Opcode 0xA`).

---

## 4. Frame Catalog & Payload Schemas

### 4.1 `SESSION_INIT` (Worker $\rightarrow$ Server)

Transmitted by the worker daemon immediately upon receiving the HTTP `101 Switching Protocols` handshake. Declares identity, hardware architecture, guest VM parameters, and compute allocation boundaries.

#### Frame Schema:
```json
{
  "type": "SESSION_INIT",
  "id": "c4b2a8d3-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
  "timestamp": 1728907200100,
  "payload": {
    "worker_id": "compute-linux-east-042",
    "hostname": "wk-east-042.internal.corp",
    "os": "linux",
    "architecture": "x86_64",
    "agent_version": "1.0.0",
    "runtime": {
      "engine": "solix",
      "version": "1.0.0",
      "isa_version": "v1.1",
      "features": ["arc_deterministic", "callee_frame_alloc", "native_ffi", "isa_metering"]
    },
    "hardware": {
      "physical_cores": 8,
      "logical_cores": 16,
      "total_memory_bytes": 34359738368,
      "gpu_present": false,
      "gpu_model": null
    },
    "allocatable_capacity": {
      "allocatable_cores": 12,
      "max_memory_bytes": 17179869184,
      "max_instruction_budget_per_task": 50000000
    }
  }
}
```

#### Field Definitions:
* `payload.worker_id` (`String`): The unique, stable identity string stored in the compute node's `worker.toml`.
* `payload.hostname` (`String`): The network domain or host identity of the node machine.
* `payload.os` (`String`): Normalized OS kernel identifier: `"linux"`, `"windows"`, or `"macos"`.
* `payload.architecture` (`String`): Normalized processor architecture: `"x86_64"` or `"aarch64"`.
* `payload.agent_version` (`String`): Semantic version of the `legatusd` daemon executable.
* `payload.runtime.engine` (`String`): Bytecode guest engine identifier (always `"solix"`).
* `payload.runtime.features` (`Array[String]`): Supported runtime execution capabilities.
* `payload.allocatable_capacity.allocatable_cores` (`Int32`): Maximum concurrent CPU threads the node permits the server broker to schedule at any single instant.
* `payload.allocatable_capacity.max_memory_bytes` (`Int64`): Cumulative memory budget in bytes dedicated across all child runner processes.
* `payload.allocatable_capacity.max_instruction_budget_per_task` (`Int64`): Upper instruction quota threshold accepted by this worker for a single task dispatch.

---

### 4.2 `SESSION_ACK` (Server $\rightarrow$ Worker)

Server response acknowledging registration, granting lease authorization, assigning cluster routing pools, and establishing heartbeat timing rules.

#### Frame Schema:
```json
{
  "type": "SESSION_ACK",
  "id": "e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b",
  "timestamp": 1728907200250,
  "payload": {
    "session_id": "sess-8823-4921-bc7e",
    "status": "ACCEPTED",
    "assigned_cluster": "production-us-east",
    "heartbeat_interval_ms": 5000,
    "heartbeat_timeout_ms": 15000,
    "server_time_utc": 1728907200248
  }
}
```

#### Rejection Schema:
If the node is unauthorized, the ticket is stale, or version negotiation fails, the server responds with a rejection frame before terminating the socket:

```json
{
  "type": "SESSION_ACK",
  "id": "e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b",
  "timestamp": 1728907200250,
  "payload": {
    "session_id": null,
    "status": "REJECTED_UNAUTHORIZED",
    "reason": "Provided single-use ticket has expired or is invalid.",
    "heartbeat_interval_ms": 0,
    "heartbeat_timeout_ms": 0,
    "server_time_utc": 1728907200248
  }
}
```

| Acceptance Status | Meaning | Action Taken |
| :--- | :--- | :--- |
| `ACCEPTED` | Registration accepted. Worker enters `OPERATIONAL` state. | Starts heartbeat timer; enables dispatch acceptance. |
| `REJECTED_UNAUTHORIZED` | Node token or ticket verification failed. | Worker closes socket, aborts reconnection until re-enrolled. |
| `REJECTED_VERSION_MISMATCH` | Worker version is deprecated or incompatible. | Worker halts and logs critical upgrade directive. |
| `REJECTED_CAPACITY_EXCEEDED` | Server cluster pool is at maximum capacity. | Worker enters backoff sleep for 60 seconds before retrying. |

---

### 4.3 `HEARTBEAT` (Worker $\rightarrow$ Server)

Transmitted periodically by the worker to report live telemetry, host resource utilization, and actively supervised child tasks.

#### Frame Schema:
```json
{
  "type": "HEARTBEAT",
  "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "timestamp": 1728907205000,
  "payload": {
    "session_id": "sess-8823-4921-bc7e",
    "draining": false,
    "host_metrics": {
      "cpu_load_pct": 18.4,
      "gpu_load_pct": 0.0,
      "memory_used_bytes": 4294967296,
      "memory_free_bytes": 12884901888
    },
    "running_tasks": [
      {
        "task_id": "task-20261013-0091",
        "pid": 48201,
        "status": "EXECUTING",
        "instructions_consumed": 1420580,
        "memory_used_bytes": 33554432,
        "elapsed_wall_time_ms": 1250
      }
    ]
  }
}
```

#### Field Specifications:
* `payload.draining` (`Boolean`): Indicates whether the node is in `DRAINING` mode. If `true`, the broker scheduler dispatches zero new tasks.
* `payload.host_metrics.cpu_load_pct` (`Float`): Aggregate host system CPU load ($0.0 \le \text{load} \le 100.0$).
* `payload.host_metrics.gpu_load_pct` (`Float`): Host graphics/compute unit utilization ($0.0$ if none).
* `payload.host_metrics.memory_used_bytes` (`Int64`): Total physical RAM currently committed by the host OS.
* `payload.host_metrics.memory_free_bytes` (`Int64`): Unallocated physical RAM available to the host OS.
* `payload.running_tasks` (`Array[Object]`): An array of descriptors for every child task actively supervised on the compute node.

---

### 4.4 `HEARTBEAT_ACK` (Server $\rightarrow$ Worker)

Acknowledges receipt of a heartbeat, resetting the dead-man's window.

#### Frame Schema:
```json
{
  "type": "HEARTBEAT_ACK",
  "id": "f5e4d3c2-b1a0-4f9e-8d7c-6b5a4f3e2d1c",
  "timestamp": 1728907205015,
  "payload": {
    "session_id": "sess-8823-4921-bc7e",
    "server_time_utc": 1728907205014
  }
}
```

---

### 4.5 `DISPATCH_TASK` (Server $\rightarrow$ Worker)

Dispatches an executable compute job to the node. The payload contains Base64-encoded compiled Solix bytecode, operational execution constraints, inputs, environment flags, and decrypted execution secrets.

#### Frame Schema:
```json
{
  "type": "DISPATCH_TASK",
  "id": "9b1c2d3e-4f5a-6b7c-8d9e-0f1a2b3c4d5e",
  "timestamp": 1728907210000,
  "payload": {
    "task_id": "task-20261013-0091",
    "entry_point": "main",
    "bytecode": {
      "format": "solix_bc_v1",
      "encoding": "base64",
      "sha256": "8a3f89b4c0e1d2c3b4a5f6e7d8c9b0a1f2e3d4c5b6a7f8e9d0c1b2a3f4e5d6c7",
      "payload": "U09MSVhfQkMxAQAAAAMAAQAAAAgAAAA="
    },
    "constraints": {
      "instruction_budget": 50000000,
      "max_memory_bytes": 67108864,
      "timeout_ms": 30000
    },
    "arguments": {
      "batch_size": 1000,
      "multiplier": 4.5
    },
    "env": {
      "LOG_LEVEL": "info",
      "APP_ENV": "production"
    },
    "secrets": {
      "DATABASE_KEY": "sk_live_9f823a01"
    }
  }
}
```

#### Field Specifications:
* `payload.task_id` (`String`): Globally unique tracking identifier for this specific job invocation.
* `payload.entry_point` (`String`): Target entry method symbol in the bytecode (default: `"main"`).
* `payload.bytecode.format` (`String`): Virtual machine binary format specification (`"solix_bc_v1"`).
* `payload.bytecode.sha256` (`String`): SHA-256 cryptographic digest of the raw binary payload prior to Base64 encoding. The worker **MUST** verify this digest before spawning the execution runner.
* `payload.bytecode.payload` (`String`): Standard RFC 4648 Base64-encoded bytecode binary.
* `payload.constraints.instruction_budget` (`Int64`): Maximum opcode instructions the guest VM is permitted to execute before trapping with exit code `75`.
* `payload.constraints.max_memory_bytes` (`Int64`): Maximum virtual memory allocation boundary dedicated to the child process.
* `payload.constraints.timeout_ms` (`Int32`): Hard wall-clock execution deadline in milliseconds.
* `payload.arguments` (`Object`): Arbitrary JSON object passed into the guest Solix entry point.
* `payload.env` (`Object`): Map of string environment variables injected into the execution context.
* `payload.secrets` (`Object`): Key-value decrypted credentials mapped exclusively into RAM.

---

### 4.6 `TASK_PROGRESS` (Worker $\rightarrow$ Server, Optional)

Emitted by long-running Solix computations to report metered instruction consumption, intermediate completion estimates, and incremental standard output chunks.

#### Frame Schema:
```json
{
  "type": "TASK_PROGRESS",
  "id": "b3c4d5e6-7f8a-9b0c-1d2e-3f4a5b6c7d8e",
  "timestamp": 1728907211500,
  "payload": {
    "task_id": "task-20261013-0091",
    "progress_pct": 52.0,
    "instructions_consumed": 24102900,
    "memory_used_bytes": 18874368,
    "log_chunk": "Processed chunk 520 of 1000 items\n"
  }
}
```

---

### 4.7 `TASK_RESULT` (Worker $\rightarrow$ Server)

Terminal frame emitted by the worker supervisor immediately upon child process exit, error trap, budget exhaustion, or cancellation. Signifies that node capacity has been freed.

#### Successful Execution (`COMPLETED`):
```json
{
  "type": "TASK_RESULT",
  "id": "7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
  "timestamp": 1728907212350,
  "payload": {
    "task_id": "task-20261013-0091",
    "status": "COMPLETED",
    "exit_code": 0,
    "metrics": {
      "instructions_consumed": 1284500,
      "peak_memory_bytes": 14680064,
      "execution_wall_time_ms": 2341
    },
    "output": {
      "records_processed": 1000,
      "matrix_checksum": 45000.0
    },
    "error": null
  }
}
```

#### Instruction Budget Exhaustion (`ERR_INSTRUCTION_BUDGET_EXCEEDED`):
```json
{
  "type": "TASK_RESULT",
  "id": "7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
  "timestamp": 1728907212350,
  "payload": {
    "task_id": "task-20261013-0091",
    "status": "ERR_INSTRUCTION_BUDGET_EXCEEDED",
    "exit_code": 75,
    "metrics": {
      "instructions_consumed": 50000000,
      "peak_memory_bytes": 12582912,
      "execution_wall_time_ms": 4820
    },
    "output": null,
    "error": {
      "code": "BUDGET_QUOTA_EXCEEDED",
      "message": "Task exceeded allocated instruction quota of 50000000 instructions."
    }
  }
}
```

#### Terminal Status Codes Matrix:

| `payload.status` Value | Description | Exit Code Category |
| :--- | :--- | :--- |
| `COMPLETED` | Execution completed without errors; `main()` returned 0. | `0` (`EX_OK`) |
| `FAILED` | Guest code completed with an uncaught exception or non-zero exit. | `1` (`EX_GENERAL`) |
| `ERR_INSTRUCTION_BUDGET_EXCEEDED` | The Solix opcode metering loop halted because the task exceeded budget. | `75` (`EX_TEMPFAIL`) |
| `ERR_OUT_OF_MEMORY` | The runner process memory ceiling or OS OOM limit was tripped. | `71` (`EX_OSERR`) |
| `ERR_TIMEOUT` | Wall-clock execution time surpassed `timeout_ms`. | `124` (`EX_TIMEOUT`) |
| `ERR_BYTECODE_INVALID` | Worker failed SHA-256 verification or bytecode deserialization. | `1` (`EX_GENERAL`) |
| `ABORTED` | Task was stopped cleanly within the 10-second graceful window. | `130` (`EX_INTERRUPT`) |
| `KILLED_FORCIBLY` | Task hung and was terminated by supervisor with SIGKILL / Win32 Kill. | `137` (`EX_KILL`) |
| `CRASHED` | Process crashed abnormally due to an OS signal (`SIGSEGV`, `SIGBUS`, etc.). | `139` (`EX_SEGV`) |

---

### 4.8 `ABORT_TASK` (Server $\rightarrow$ Worker)

Initiates early termination of an executing task.

#### Frame Schema:
```json
{
  "type": "ABORT_TASK",
  "id": "3d4e5f6a-7b8c-9d0e-1f2a-3b4c5d6e7f8a",
  "timestamp": 1728907215000,
  "payload": {
    "task_id": "task-20261013-0091",
    "reason": "OPERATOR_CANCELLED",
    "grace_period_ms": 10000
  }
}
```

---

### 4.9 `PROTOCOL_ERROR` (Bidirectional)

Emitted when a node or broker receives a frame with an unrecognized `type`, an unparseable JSON envelope, or an incompatible schema.

#### Frame Schema:
```json
{
  "type": "PROTOCOL_ERROR",
  "id": "8f7e6d5c-4b3a-2109-8765-4321fedcba98",
  "timestamp": 1728907220000,
  "payload": {
    "originating_frame_id": "c4b2a8d3-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
    "error_code": "INVALID_JSON_ENVELOPE",
    "message": "Field 'timestamp' is missing or not an integer."
  }
}
```

---

## 5. Timing, Heartbeats & Liveness Rules

### 5.1 Cadence and Jitter Invariants

To eliminate thundering herd synchronization spikes across thousands of cluster nodes:
* The worker daemon **MUST** emit `HEARTBEAT` every $5000\text{ ms} \pm 500\text{ ms}$ (randomized uniform jitter).
* The broker **MUST** reply with `HEARTBEAT_ACK` within **2000 milliseconds**.

### 5.2 Dead-Man's Switch Timeout

* **Server Dead-Man's Window:** If the broker receives no messages from a worker for **15000 milliseconds** ($3 \times t_{\text{interval}}$), the broker **MUST** mark the node `OFFLINE`, close the socket, and re-enqueue in-flight tasks.
* **Client Dead-Man's Window:** If the worker receives no `HEARTBEAT_ACK` or broker traffic for **15000 milliseconds**, the worker **MUST** abort the socket and initiate reconnection backoff.

### 5.3 Reconnect Backoff Model

When disconnected from the broker, the worker client **MUST** calculate reconnect intervals using:
$$t_{\text{backoff}} = \min\left(30, \; 2^{\text{attempt}}\right) + \text{UniformRandom}(0, 1) \quad [\text{seconds}]$$

---

## 6. Error Handling & Close Code Matrix

Both server and worker **MUST** conform to RFC 6455 closure codes:

| Close Code | RFC 6455 Label | Protocol Context & Action |
| :--- | :--- | :--- |
| `1000` | Normal Closure | Clean shutdown (`legatus down`, node drain, service termination). |
| `1001` | Going Away | Server broker restarting or undergoing rolling maintenance. |
| `1002` | Protocol Error | Endpoint received a corrupted or non-conformant frame. |
| `1008` | Policy Violation | Ephemeral connection ticket expired, was burned, or failed Argon2id validation. |
| `1009` | Message Too Big | Frame exceeded the 32 MB buffer ceiling. Drop connection immediately. |
| `1011` | Internal Error | Unhandled internal runtime exception within the daemon or server broker. |

---

## 7. Verification & Compliance Checklist

Protocol implementers and reviewers **MUST** verify the following audit criteria:

- [ ] **Transport Invariant:** Transport mandates TLS 1.3 with full-duplex WebSocket framing.
- [ ] **Envelope Schema:** Base frame schema includes `type`, `id` (UUIDv4), `timestamp` (Int64), and `payload` (Object).
- [ ] **Finite State Machine:** All five connection states (`DISCONNECTED`, `CONNECTING`, `NEGOTIATING`, `OPERATIONAL`, `TERMINATING`) and timeout transitions are modeled.
- [ ] **Timing Jitter:** Heartbeats specify randomized uniform jitter ($5000\text{ ms} \pm 500\text{ ms}$).
- [ ] **Dead-Man's Switch:** 15,000ms dead-man's switch logic is enforced on both client and broker.
- [ ] **Close Code Mappings:** Every protocol failure maps to an RFC 6455 close code.
- [ ] **Frame Size Cap:** 32 MB frame ceiling enforced with close code `1009`.