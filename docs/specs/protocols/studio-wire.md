# Legatus Studio Live Stream Wire Protocol Specification

## Document Metadata
* **Protocol Name:** Legatus Studio Telemetry & Control Protocol (LSTCP)
* **Subsystem:** `protocols/studio-wire`
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** JavaFX UI Engineers, Backend Systems Engineers, Protocol Architects
* **Transport:** Full-Duplex Secure WebSocket (`wss://`) over TLS 1.3
* **Endpoint:** `/ws/studio?ticket=<st_tkt_uuid>`
* **Payload Encoding:** UTF-8 Encoded JSON (RFC 8259)
* **Maximum Frame Size:** 33,554,432 bytes (32 MB)
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * `docs/specs/studio-spec.md`
  * `docs/specs/server-spec.md`
  * `docs/specs/protocols/worker-wire.md`
  * `docs/dev/guidelines/authoring-protocols.md` (GL-0007)

---

## 1. Protocol Architecture & Connection Lifecycle

The **Legatus Studio Telemetry & Control Protocol (LSTCP)** provides a real-time, bi-directional event stream between the **Legatus Studio** desktop client and **Legatus Server**. It eliminates UI polling by pushing cluster topology mutations, quarantine enrollment requests, aggregate metric ticks, and live guest task output streams directly to the operator's cockpit.

```
     LEGATUS STUDIO (JavaFX Client)                              LEGATUS SERVER (Broker)
                   │                                                        │
                   │ ── 1. HTTP/1.1 Upgrade Request (TLS 1.3) ────────────> │
                   │       GET /ws/studio?ticket=st_tkt_...                 │
                   │                                                        │ ── Validates & burns ticket
                   │ <── 2. HTTP/1.1 101 Switching Protocols ────────────── │
                   │                                                        │
[NEGOTIATING]      │ ── 3. STUDIO_SESSION_INIT ───────────────────────────> │
                   │ <── 4. STUDIO_SESSION_ACK (Initial Cluster Snapshot) ─ │
                   │                                                        │
[STREAMING]        │ <── 5. EVENT_CLUSTER_METRIC_TICK (Every 1000ms) ────── │
                   │ <── 6. EVENT_ENROLLMENT_REQUESTED (Quarantine Alert) ─ │
                   │ <── 7. EVENT_WORKER_STATE_CHANGED ──────────────────── │
                   │ <── 8. EVENT_TASK_LIFECYCLE ────────────────────────── │
                   │                                                        │
[LOG SUBSCRIPTION] │ ── 9. SUBSCRIBE_TASK_LOGS (task_id) ─────────────────> │
                   │ <── 10. EVENT_TASK_LOG_CHUNK (Live stdout/stderr) ──── │
                   │ ── 11. UNSUBSCRIBE_TASK_LOGS (task_id) ──────────────> │
                   │                                                        │
[LIVENESS]         │ ── 12. STUDIO_HEARTBEAT (Every 10000ms +/- 1000ms) ──> │
                   │ <── 13. STUDIO_HEARTBEAT_ACK ───────────────────────── │
```

---

## 2. Base Frame Envelope & Invariants

In accordance with `GL-0007`, every message transmitted over the Studio WebSocket **MUST** use the canonical four-field JSON envelope:

```json
{
  "type": "FRAME_TYPE_STRING",
  "id": "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
  "timestamp": 1728907200000,
  "payload": {}
}
```

---

## 3. Session Handshake & Topic Subscription Frames

### 3.1 `STUDIO_SESSION_INIT` (Studio $\rightarrow$ Server)
Transmitted immediately after WebSocket upgrade to declare initial telemetry subscription topics.

```json
{
  "type": "STUDIO_SESSION_INIT",
  "id": "11223344-5566-4778-8899-aabbccddeeff",
  "timestamp": 1728907200100,
  "payload": {
    "studio_version": "1.0.0",
    "subscriptions": ["CLUSTER_METRICS", "WORKER_TOPOLOGY", "QUARANTINE_ALERTS", "TASK_LIFECYCLE"],
    "viewport_filter": {
      "active_view": "WORKER_LIST",
      "cluster_pool": "production-core",
      "limit": 50
    }
  }
}
```

* `payload.viewport_filter` (`Object | null`): Optional client viewport scoping. When provided, the broker filters high-frequency node state events strictly to the specified cluster pool or visible window, preventing UI thread saturation during burst cluster activity.

### 3.2 `STUDIO_SESSION_ACK` (Server $\rightarrow$ Studio)
Acknowledges session establishment and delivers the initial cluster state snapshot so the UI renders immediately without secondary REST round-trips.

```json
{
  "type": "STUDIO_SESSION_ACK",
  "id": "22334455-6677-4889-99aa-bbccddeeff00",
  "timestamp": 1728907200150,
  "payload": {
    "session_id": "st-sess-9012-ab34",
    "cluster_name": "production-core",
    "heartbeat_interval_ms": 10000,
    "pending_enrollments_count": 1,
    "online_workers_count": 14,
    "queued_tasks_count": 3,
    "executing_tasks_count": 28
  }
}
```

### 3.3 `SUBSCRIBE_TASK_LOGS` / `UNSUBSCRIBE_TASK_LOGS` (Studio $\rightarrow$ Server)
Dynamically attaches or detaches the Studio session from a specific task's live `TASK_PROGRESS` standard output stream.

```json
{
  "type": "SUBSCRIBE_TASK_LOGS",
  "id": "33445566-7788-499a-aabb-ccddeeff0011",
  "timestamp": 1728907201000,
  "payload": {
    "task_id": "task-20261013-0091"
  }
}
```

---

## 4. Server-to-Studio Real-Time Event Catalog

### 4.1 `EVENT_ENROLLMENT_REQUESTED` (Server $\rightarrow$ Studio)
Pushed immediately when a new worker node enters `PENDING_APPROVAL`.

```json
{
  "type": "EVENT_ENROLLMENT_REQUESTED",
  "id": "44556677-8899-4aab-bbcc-ddeeff001122",
  "timestamp": 1728907202000,
  "payload": {
    "enrollment_id": "enr-8821-4920-a1b2",
    "worker_id": "compute-linux-east-042",
    "hostname": "wk-east-042.internal.corp",
    "source_ip": "10.24.18.42",
    "os": "linux",
    "architecture": "x86_64",
    "fingerprint_hex": "4E:8A:BC:31:09:FF:7D:12",
    "visual_code": "Tiger-Galaxy-77",
    "allocatable_cores": 12,
    "max_memory_bytes": 17179869184,
    "expires_at": 1728908100000
  }
}
```

### 4.2 `EVENT_CLUSTER_METRIC_TICK` (Server $\rightarrow$ Studio)
Broadcast every `1000ms` to drive live dashboard sparklines and resource gauges.

```json
{
  "type": "EVENT_CLUSTER_METRIC_TICK",
  "id": "55667788-99aa-4bbc-ccdd-eeff00112233",
  "timestamp": 1728907203000,
  "payload": {
    "nodes_online": 14,
    "nodes_draining": 1,
    "nodes_offline": 0,
    "total_allocatable_cores": 224,
    "active_allocated_cores": 96,
    "cluster_cpu_load_pct": 42.8,
    "total_allocatable_memory_bytes": 240518168576,
    "active_used_memory_bytes": 103079215104,
    "tasks_queued": 4,
    "tasks_executing": 96,
    "tasks_completed_last_60s": 1420,
    "tasks_failed_last_60s": 2
  }
}
```

### 4.3 `EVENT_WORKER_STATE_CHANGED` (Server $\rightarrow$ Studio)
Emitted whenever a worker transitions between `ONLINE`, `DRAINING`, `OFFLINE`, or `REVOKED`, or updates its heartbeat load metrics.

```json
{
  "type": "EVENT_WORKER_STATE_CHANGED",
  "id": "66778899-aabb-4ccd-ddee-ff0011223344",
  "timestamp": 1728907204000,
  "payload": {
    "worker_id": "compute-linux-east-042",
    "previous_state": "ONLINE",
    "current_state": "DRAINING",
    "cpu_load_pct": 18.4,
    "memory_used_bytes": 4294967296,
    "active_tasks_count": 1
  }
}
```

### 4.4 `EVENT_TASK_LIFECYCLE` (Server $\rightarrow$ Studio)
Emitted whenever a task transitions state (`QUEUED`, `DISPATCHED`, `EXECUTING`, `ABORTING`, `COMPLETED`, `FAILED`, `ABORTED`, `KILLED_FORCIBLY`, `CRASHED`).

```json
{
  "type": "EVENT_TASK_LIFECYCLE",
  "id": "778899aa-bbcc-4dde-eeff-001122334455",
  "timestamp": 1728907205000,
  "payload": {
    "task_id": "task-20261013-0091",
    "name": "matrix-factorization-batch",
    "status": "COMPLETED",
    "exit_code": 0,
    "assigned_worker_id": "compute-linux-east-042",
    "instructions_consumed": 1284500,
    "peak_memory_bytes": 14680064,
    "execution_wall_time_ms": 2341
  }
}
```

### 4.5 `EVENT_TASK_LOG_CHUNK` (Server $\rightarrow$ Studio)
Forwarded only to Studio sessions that issued `SUBSCRIBE_TASK_LOGS` for the target `task_id`.

```json
{
  "type": "EVENT_TASK_LOG_CHUNK",
  "id": "8899aabb-ccdd-4eef-ff00-112233445566",
  "timestamp": 1728907206000,
  "payload": {
    "task_id": "task-20261013-0091",
    "progress_pct": 52.0,
    "instructions_consumed": 24102900,
    "log_chunk": "Processed chunk 520 of 1000 items\n"
  }
}
```

---

## 5. Timing, Heartbeats & Close Codes

* **Heartbeat Cadence:** Studio transmits `STUDIO_HEARTBEAT` every $10000\text{ ms} \pm 1000\text{ ms}$ (uniform random jitter). Server responds with `STUDIO_HEARTBEAT_ACK` within $2000\text{ ms}$.
* **Dead-Man's Switch:** Sessions silent for $30000\text{ ms}$ ($3 \times t_{\text{interval}}$) are terminated with RFC 6455 Close Code `1011`.
* **Close Codes:** Adheres strictly to the RFC 6455 Close Code matrix in `GL-0007` (`1000`, `1001`, `1002`, `1008`, `1009`, `1011`).

---

## 6. Verification & Compliance Checklist

- [ ] **Envelope Conformance:** All frames include `type`, `id` (UUIDv4), `timestamp` (Int64), and `payload` (Object).
- [ ] **Burn-on-Read Handshake:** Upgrade requires a single-use `st_tkt_...` ticket.
- [ ] **Scoped Log Streaming:** `EVENT_TASK_LOG_CHUNK` is only transmitted to explicitly subscribed sessions.