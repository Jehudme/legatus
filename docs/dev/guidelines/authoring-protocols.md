# Network & Wire Protocol Authoring Specification

## Document Metadata
* **Guideline ID:** GL-0007
* **Scope:** Layer 7 Transport & Network Protocols (`docs/specs/protocols/`)
* **Status:** Final Implementation Standard
* **Audience:** Protocol Architects, Distributed Systems Engineers, Security Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

In a distributed serverless runtime where compute nodes (`legatusd`) process arbitrary high-density guest workloads under centralized orchestration (`server`) and operator monitoring (`studio`), wire protocols represent the ultimate operational contract. Ambiguities in message framing, unhandled network partitions, lack of correlation IDs, or un-jittered heartbeat storms will cause cluster-wide split-brain failures, cascading reconnect thundering herds, and undetected task execution stalls.

This specification establishes the mandatory architectural standards, envelope schemas, timing models, state machine formalisms, and error mappings for all network protocols authored within the Legatus platform (such as the **Legatus Compute Transport Protocol - LCTP** and the **Studio Cockpit Stream**). Adherence to this guideline guarantees predictable protocol evolution, absolute message determinism, and distributed fault tolerance across heterogeneous environments.

---

## 2. Normative Invariants & Core Principles

All network and wire protocol specifications **MUST** strictly adhere to the following binding invariants:

1. **Transport Security Baseline:** All remote network communications **MUST** execute exclusively over TLS 1.3 or higher. Unencrypted plaintext transports (`ws://`, `http://`) **MUST NOT** be permitted in production or staging environments.
2. **Canonical JSON Envelope Standard:** Every message transmitted over WebSocket or stream connections **MUST** be wrapped in the canonical four-field envelope: `type` (discriminator), `id` (UUIDv4 correlation identifier), `timestamp` (millisecond epoch), and `payload` (scoped object). Naked or untyped payloads **SHALL NOT** be transmitted across the wire.
3. **Formal Finite State Automaton (FSM):** Every protocol specification **MUST** include an explicit ASCII or Mermaid state diagram defining all valid client and server connection states, permitted transitions, triggers, and timeout actions. Undefined state transitions **MUST** trigger immediate connection termination.
4. **Interval Jitter & Thundering Herd Prevention:** Any periodic protocol transmission (e.g., heartbeats, status polling, reconnect attempts) **MUST** incorporate randomized jitter. Periodic timers **MUST NOT** fire at static, synchronized intervals across compute nodes.
5. **Dead-Man's Switch Requirement:** Connection liveness **MUST** be governed by a deterministic dead-man's switch. If an endpoint fails to receive an expected acknowledgement (e.g., `HEARTBEAT_ACK`) within a strictly defined timeout window ($3 \times t_{\text{interval}}$), the connection **MUST** be torn down immediately and its resources reclaimed.
6. **Strict RFC 6455 Close Code Mapping:** Every protocol error condition **MUST** map explicitly to an RFC 6455 WebSocket close code (or HTTP status code for REST), accompanied by a descriptive diagnostic reason string. Silent connection drops or generic close codes without context are strictly prohibited.
7. **Maximum Frame Bounds:** Specifications **MUST** define hard upper limits on frame size. The standard maximum frame size across Legatus transports is **32 Megabytes** (`33,554,432 bytes`). Frames exceeding this threshold **MUST** be rejected with Close Code `1009` (Message Too Big).

---

## 3. Structural Specification & Schema

### 3.1 The Canonical JSON Wire Envelope

Every frame transmitted over LCTP or related WebSocket protocols **MUST** match the following JSON schema:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "LegatusWireEnvelope",
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

### 3.2 Timing Invariants & Backoff Formulae

To prevent thundering herd conditions during cluster reconnect storms, reconnection intervals **MUST** be computed using exponential backoff with full jitter:

$$t_{\text{backoff}} = \min\left(t_{\text{max}}, \; t_{\text{base}} \times 2^{\text{attempt}}\right) + \text{UniformRandom}(0, \; t_{\text{jitter}})$$

* Standard parameters for `legatusd`:
  * $t_{\text{base}} = 1000\text{ ms}$ (1 second)
  * $t_{\text{max}} = 30000\text{ ms}$ (30 seconds)
  * $t_{\text{jitter}} = 1000\text{ ms}$

### 3.3 Protocol Error & Close Code Matrix

| Error Condition | RFC 6455 Code | Wire Mnemonic | Remediation / Behavioral Mandate |
| :--- | :--- | :--- | :--- |
| Normal Session Shutdown | `1000` | `NORMAL_CLOSURE` | Clean daemon exit or operator drain. No reconnect retry. |
| Malformed JSON Frame | `1002` | `PROTOCOL_ERROR` | Close immediately; log syntax defect. Do not retry without patch. |
| Unrecognized Frame `type` | `1003` | `UNSUPPORTED_DATA` | Close socket; indicates protocol version mismatch. |
| Handshake Timeout (5000ms) | `1008` | `POLICY_VIOLATION` | Terminate socket; reconnect with exponential jittered backoff. |
| Authentication Expired/Invalid | `1008` | `POLICY_VIOLATION` | Terminate socket; compute node must re-authenticate via REST. |
| Oversized Frame (> 32MB) | `1009` | `MESSAGE_TOO_BIG` | Reject frame and drop connection immediately. |
| Heartbeat Timeout (Dead-Man) | `1011` | `INTERNAL_ERROR` | Socket dropped due to missed ACKs; reconnect with backoff. |

---

## 4. Production-Ready Template

Engineers authoring new protocol specifications (`docs/specs/protocols/*.md`) **MUST** utilize the following template:

```markdown
# [Protocol Name] Specification

## Document Metadata
* **Protocol Name:** [e.g., Legatus Compute Transport Protocol (LCTP)]
* **Subsystem:** `protocols/[protocol-name]`
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Standard
* **Target Audience:** Systems Software Engineers, Protocol Architects
* **Transport:** Secure WebSocket (`wss://`) over TLS 1.3
* **Payload Encoding:** UTF-8 Encoded JSON (RFC 8259)
* **Maximum Frame Size:** 33,554,432 bytes (32 MB)

---

## 1. Protocol Architecture & Connection Lifecycle
<!-- High-level architectural overview, transport selection, and sequence flow. -->
[Provide architectural overview and sequence diagrams here]

## 2. Connection Finite State Machine (FSM)
<!-- Detailed ASCII or Mermaid diagram defining states, transitions, triggers, and timers. -->
```
    ┌──────────────┐
    │ DISCONNECTED │ ◄──────────────────────────────┐
    └──────┬───────┘                                │
           │ (Connect Trigger)                      │
           ▼                                        │ (Socket Error /
    ┌──────────────┐                                │  Timeout)
    │  CONNECTING  │                                │
    └──────┬───────┘                                │
           │ (Handshake Complete)                   │
           ▼                                        │
    ┌──────────────┐                                │
    │  OPERATIONAL │ ───────────────────────────────┘
    └──────────────┘
```

## 3. Base Frame Envelope & Field Invariants
<!-- Enforce canonical envelope fields: type, id, timestamp, payload. -->

| Envelope Field | Type | Constraint | Description |
| :--- | :--- | :--- | :--- |
| `type` | `String` | Required, Upper Snake Case | Discriminator. |
| `id` | `String` | Required, UUIDv4 | Tracing and correlation identifier. |
| `timestamp` | `Int64` | Required, Millisecond Epoch | Monotonic UTC creation timestamp. |
| `payload` | `Object` | Required, JSON Object | Scoped data container. |

## 4. Frame Catalog & Payload Schemas
<!-- Detail each frame type in both client->server and server->client directions. -->

### 4.1 `[FRAME_NAME]` (Direction: Client -> Server)
* **Trigger:** [When is this frame emitted?]
* **Timeout:** [Expected ACK response deadline]
* **Schema:**
```json
{
  "type": "FRAME_NAME",
  "id": "e2b3c4d5-6f7a-8b9c-0d1e-2f3a4b5c6d7e",
  "timestamp": 1728907200000,
  "payload": {
    "field_a": "value",
    "field_b": 100
  }
}
```

## 5. Timing, Heartbeats & Liveness Rules
<!-- Define heartbeat intervals, random jitter ranges, and dead-man's switch timeouts. -->
* **Heartbeat Cadence:** Emit `HEARTBEAT` every $5000\text{ ms} \pm 500\text{ ms}$ jitter.
* **Dead-Man's Switch:** Socket marked dead after $15000\text{ ms}$ of unacknowledged silence.

## 6. Error Handling & Close Code Matrix
<!-- Map all protocol anomalies to RFC 6455 close codes. -->

| Error Scenario | Close Code | Reason String | Action |
| :--- | :--- | :--- | :--- |
| Protocol Violation | `1002` | `ERR_PROTOCOL_MALFORMED` | Tear down connection; log error. |

## 7. Verification & Compliance Checklist
<!-- Enforce schema validation and integration test requirements. -->
- [ ] Envelope strictly adheres to Section 3.1 JSON schema.
- [ ] Jitter formula verified in integration tests.
```

---

## 5. Concrete Production Example

### LCTP Task Dispatch & Terminal Result Frames

Below is an authoritative excerpt from the Legatus Compute Transport Protocol (`docs/specs/protocols/worker-wire.md`):

#### 1. `DISPATCH_TASK` (Server $\rightarrow$ Worker)
Transmitted by `legatus-server` to allocate a compute task to an available worker daemon.

```json
{
  "type": "DISPATCH_TASK",
  "id": "7f8a9b0c-1d2e-4f5a-8b9c-0d1e2f3a4b5c",
  "timestamp": 1728907200500,
  "payload": {
    "task_id": "tsk-9941-8812-beef",
    "function_name": "solix_matrix_mult",
    "bytecode_uri": "https://storage.legatus.internal/bytecode/mat_v2.sox",
    "bytecode_sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "instruction_budget": 50000000,
    "memory_limit_bytes": 134217728,
    "wall_clock_timeout_ms": 10000,
    "arguments": ["--dimension=1024", "--precision=fp64"]
  }
}
```

#### 2. `TASK_RESULT` (Worker $\rightarrow$ Server)
Terminal response emitted by `legatusd` upon completion, trap, or cancellation of the guest runner process.

```json
{
  "type": "TASK_RESULT",
  "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "timestamp": 1728907205200,
  "payload": {
    "task_id": "tsk-9941-8812-beef",
    "status": "COMPLETED",
    "exit_code": 0,
    "instructions_executed": 38412901,
    "peak_memory_bytes": 67108864,
    "wall_clock_duration_ms": 4680,
    "stdout_digest_sha256": "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
    "error_diagnostic": null
  }
}
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Naked or Untyped Frame Payloads
* **Flawed Practice:** Emitting raw JSON strings or arrays directly over the WebSocket without the standard envelope:
```json
// FLAWED:
{"task_id": "123", "status": "DONE"}
```
* **Failure Mode:** Receiving message multiplexers cannot route the frame without parsing arbitrary fields. Logging infrastructure cannot extract correlation IDs (`id`) or trace latencies. When new frames are introduced, unversioned deserializers throw unhandled exceptions.
* **Corrected Approach:** Always wrap the data in the canonical four-field envelope (`type`, `id`, `timestamp`, `payload`).

### Anti-Pattern 2: Missing Monotonic Timestamp & Correlation UUID
* **Flawed Practice:** Relying on network arrival time or local system wall-clock without an envelope timestamp, and omitting unique request IDs:
```json
// FLAWED:
{
  "type": "HEARTBEAT",
  "payload": {"cores_used": 4}
}
```
* **Failure Mode:** Out-of-order packets during TCP retransmits cannot be detected. Network tracing tools cannot correlate requests with server responses, making distributed latency profiling impossible.
* **Corrected Approach:** Provide a valid UUIDv4 in `id` and a UTC millisecond epoch in `timestamp`.

### Anti-Pattern 3: Static, Synchronized Timers (Thundering Herd)
* **Flawed Practice:** Hardcoding a strict 5000ms timer across all 10,000 cluster worker daemons without jitter.
* **Failure Mode:** When the centralized server broker restarts or recovers from a network blip, all 10,000 workers attempt to reconnect and transmit heartbeats at the exact same millisecond boundary, saturating server NICs, triggering connection drops, and creating an unending reconnect cascade.
* **Corrected Approach:** Apply $\pm 10\%$ uniform random jitter to all periodic heartbeats and exponential backoff to reconnect loops.

---

## 7. Verification & Compliance Checklist

Protocol designers and code reviewers **MUST** verify the following audit criteria:

- [ ] **Transport Invariant:** Transport explicitly mandates TLS 1.3 with secure WebSocket framing.
- [ ] **Envelope Schema:** Base frame schema includes `type`, `id` (UUIDv4), `timestamp` (Int64), and `payload` (Object).
- [ ] **Finite State Machine:** All connection states, timeouts, and state transitions are formally diagrammed.
- [ ] **Timing Jitter:** All heartbeats and reconnect loops specify randomized interval jitter.
- [ ] **Dead-Man's Switch:** Dead-man's switch timeout rules ($3 \times t_{\text{interval}}$) are documented and verified.
- [ ] **Close Code Mappings:** Every error scenario maps to an RFC 6455 close code with a clear diagnostic reason string.
- [ ] **Frame Size Cap:** Strict 32 MB frame size limit is documented and enforced by wire parsers.
