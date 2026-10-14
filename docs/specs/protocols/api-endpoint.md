# Legatus REST API Gateway Specification

## Document Metadata
* **Protocol Name:** Legatus Control & Ingestion REST API
* **Subsystem:** `protocols/api-endpoint`
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Backend Engineers, Worker Daemon Implementers, Studio UI Engineers
* **Transport:** HTTPS over TLS 1.3 (`Content-Type: application/json; charset=utf-8`)
* **Base Path:** `/api/v1`
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * `docs/specs/server-spec.md`
  * `docs/specs/worker-spec.md`
  * `docs/specs/protocols/worker-wire.md`
  * `docs/specs/protocols/studio-wire.md`
  * `docs/dev/guidelines/authoring-protocols.md` (GL-0007)

---

## 1. Security, Authentication & Error Envelope Standards

All REST endpoints **MUST** be served exclusively over HTTPS using TLS 1.3. Plaintext HTTP requests **MUST** be rejected or redirected prior to payload inspection.

### 1.1 Authentication Tiers
| Endpoint Prefix | Required Authentication | Header / Credential Mechanism |
| :--- | :--- | :--- |
| `/api/v1/workers/register*` | Public (Quarantine Rate-Limited) | Ed25519 Public Key in body; rate-limited to 5 req/min per IP. |
| `/api/v1/auth/worker` | Node Pre-Shared Secret | `worker_id` + `secret_token` verified against Argon2id hash. |
| `/api/v1/auth/operator/*` | Operator Credentials | Username/Password or Refresh Token; issues short-lived EdDSA JWT. |
| `/api/v1/admin/*`, `/tasks*`, `/workers*`, `/cluster*` | Operator Bearer JWT or API Key | `Authorization: Bearer <operator_jwt_or_api_key>` |

### 1.2 Canonical REST Error Envelope
Every non-2xx HTTP response **MUST** return a JSON object conforming to the following structure:

```json
{
  "timestamp": 1728907200000,
  "status": 422,
  "error_code": "ERR_VALIDATION_FAILED",
  "message": "Field 'constraints.instruction_budget' must be between 1000 and 1000000000.",
  "path": "/api/v1/tasks",
  "trace_id": "d4e5f6a7-b8c9-4d0e-9f1a-2b3c4d5e6f7a"
}
```

---

## 2. Worker Enrollment & Attestation Endpoints

### 2.1 Submit Quarantine Registration (`POST /api/v1/workers/register`)
Invoked by an unconfigured `legatusd` node upon initial boot.

#### Request Payload:
```json
{
  "worker_id": "compute-linux-east-042",
  "hostname": "wk-east-042.internal.corp",
  "os": "linux",
  "architecture": "x86_64",
  "agent_version": "1.0.0",
  "ed25519_public_key": "MCowBQYDK2VwAyEA7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8=",
  "hardware": {
    "physical_cores": 8,
    "logical_cores": 16,
    "total_memory_bytes": 34359738368,
    "gpu_present": false
  },
  "allocatable_capacity": {
    "allocatable_cores": 12,
    "max_memory_bytes": 17179869184,
    "max_instruction_budget_per_task": 50000000
  }
}
```

#### Response (`202 Accepted`):
```json
{
  "enrollment_id": "enr-8821-4920-a1b2",
  "status": "PENDING_APPROVAL",
  "fingerprint_hex": "4E:8A:BC:31:09:FF:7D:12",
  "visual_code": "Tiger-Galaxy-77",
  "poll_interval_ms": 3000,
  "expires_at": 1728908100000
}
```

### 2.2 Poll Enrollment Status (`GET /api/v1/workers/register/{enrollment_id}/status`)
Polled by the quarantined worker every `3000ms` (+/- `500ms` jitter).

#### Response while Pending (`200 OK`):
```json
{
  "enrollment_id": "enr-8821-4920-a1b2",
  "status": "PENDING_APPROVAL",
  "secret_token": null,
  "assigned_cluster": null
}
```

#### Response upon Operator Approval (`200 OK` - Single-Read Delivery):
```json
{
  "enrollment_id": "enr-8821-4920-a1b2",
  "status": "APPROVED",
  "secret_token": "wk_sec_99a8b7c6d5e4f3a2b10f9876543210fedcba9876543210fedcba9876543210",
  "assigned_cluster": "production-us-east"
}
```

---

## 3. Ephemeral Ticket Authentication Endpoints

### 3.1 Worker WebSocket Ticket Exchange (`POST /api/v1/auth/worker`)
Exchanges the worker's long-term `secret_token` and current capacity configuration for a 30-second, single-use WebSocket upgrade ticket.

#### Request Payload:
```json
{
  "worker_id": "compute-linux-east-042",
  "secret_token": "wk_sec_99a8b7c6d5e4f3a2b10f9876543210fedcba9876543210fedcba9876543210",
  "hostname": "wk-east-042.internal.corp",
  "os": "linux",
  "architecture": "x86_64",
  "agent_version": "1.0.0",
  "allocatable_capacity": {
    "allocatable_cores": 12,
    "max_memory_bytes": 17179869184,
    "max_instruction_budget_per_task": 50000000
  }
}
```

#### Response (`200 OK`):
```json
{
  "ticket": "ws_tkt_e7b2a8d3-5e6f-4a1b-9c8d-7e6f5a4b3c2d",
  "ticket_ttl_seconds": 30,
  "websocket_url": "/ws/worker",
  "heartbeat_interval_ms": 5000,
  "heartbeat_timeout_ms": 15000,
  "assigned_cluster": "production-us-east"
}
```

### 3.2 Operator Login (`POST /api/v1/auth/operator/login`)
Authenticates an operator from Legatus Studio or CLI.

#### Request Payload:
```json
{
  "username": "admin@legatus.internal",
  "password": "correct-horse-battery-staple"
}
```

#### Response (`200 OK`):
```json
{
  "access_token": "eyJhbGciOiJFZERTQSI...",
  "expires_in_seconds": 3600,
  "studio_ws_ticket": "st_tkt_1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "studio_ws_url": "/ws/studio",
  "role": "CLUSTER_ADMIN"
}
```

---

## 4. Task & Function Orchestration Endpoints

### 4.1 Dispatch New Task (`POST /api/v1/tasks`)
Submits a compiled Solix workload for immediate scheduling and execution.

#### Request Payload:
```json
{
  "name": "matrix-factorization-batch",
  "priority": "HIGH",
  "idempotent": true,
  "max_retries": 2,
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
    "LOG_LEVEL": "info"
  },
  "secret_refs": ["DATABASE_KEY"]
}
```

#### Response (`202 Accepted`):
```json
{
  "task_id": "task-20261013-0091",
  "status": "QUEUED",
  "submitted_at": 1728907210000
}
```

### 4.2 Query Task Status & Result (`GET /api/v1/tasks/{task_id}`)

#### Response (`200 OK`):
```json
{
  "task_id": "task-20261013-0091",
  "name": "matrix-factorization-batch",
  "status": "COMPLETED",
  "exit_code": 0,
  "priority": "HIGH",
  "assigned_worker_id": "compute-linux-east-042",
  "attempt_count": 1,
  "submitted_at": 1728907210000,
  "started_at": 1728907210010,
  "completed_at": 1728907212350,
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
```

### 4.3 Abort Running Task (`POST /api/v1/tasks/{task_id}/abort`)
Triggers the two-stage termination escalation (`ABORT_TASK` frame) on the assigned worker node.

#### Request Payload:
```json
{
  "reason": "OPERATOR_CANCELLED",
  "grace_period_ms": 10000
}
```

#### Response (`202 Accepted`):
```json
{
  "task_id": "task-20261013-0091",
  "status": "ABORTING",
  "assigned_worker_id": "compute-linux-east-042",
  "escalation_deadline_utc": 1728907225000
}
```

### 4.4 Download Binary Bytecode (`GET /api/v1/bytecode/{sha256}`)
Streams raw compiled Solix bytecode binary directly as an `application/octet-stream`. Used by worker nodes to download binaries that are not present in local cache, eliminating the 33% Base64 encoding overhead in WebSocket `DISPATCH_TASK` frames.

#### Headers:
* `Accept: application/octet-stream`
* `If-None-Match: "<sha256>"` (Supports HTTP 304 Not Modified cache validation)

#### Response (`200 OK`):
* `Content-Type: application/octet-stream`
* `ETag: "<sha256>"`
* `Content-Length: <bytes>`
* Body: Raw binary Solix bytecode stream.

---

## 5. Fleet Administration & Quarantine Endpoints

| Method & Path | Purpose | Success Code |
| :--- | :--- | :--- |
| `GET /api/v1/bytecode/{sha256}` | Streams raw compiled bytecode binary directly to worker nodes. | `200 OK` / `304 Not Modified` |
| `GET /api/v1/workers` | Lists all registered workers, states (`ONLINE`, `DRAINING`, `OFFLINE`), and load metrics. | `200 OK` |
| `GET /api/v1/admin/workers/enrollments` | Lists all quarantined nodes currently in `PENDING_APPROVAL`. | `200 OK` |
| `POST /api/v1/admin/workers/enrollments/{id}/approve` | Approves a quarantined node, generates its Argon2id secret token, and assigns its cluster pool. | `200 OK` |
| `POST /api/v1/admin/workers/enrollments/{id}/reject` | Rejects a quarantined node and optionally blacklists the source IP. | `200 OK` |
| `POST /api/v1/admin/workers/{worker_id}/drain` | Remotely toggles drain mode on an active worker. | `200 OK` |
| `POST /api/v1/admin/workers/{worker_id}/revoke` | Immediately revokes a worker's credentials and severs its active WebSocket session. | `200 OK` |
| `GET /api/v1/cluster/stats` | Returns aggregate cluster cores, memory, queue depth, and 24h throughput statistics. | `200 OK` |

---

## 6. Verification & Compliance Checklist

- [ ] **TLS 1.3 Mandate:** All endpoints reject non-TLS traffic.
- [ ] **Status Code Alignment:** Task exit codes (`0`, `1`, `71`, `75`, `124`, `130`, `137`, `139`) match `worker-spec.md` and `worker-wire.md`.
- [ ] **Burn-on-Read Tickets:** `/api/v1/auth/worker` issues 30-second single-use tickets.
- [ ] **Rate Limiting:** `/api/v1/workers/register` enforces the 5 req/min/IP and 50 pending queue ceiling.