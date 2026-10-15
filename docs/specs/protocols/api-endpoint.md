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

### 1.1 Authentication Tiers & RBAC Matrix
| Endpoint Prefix | Required Role / Auth Tier | Header / Credential Mechanism |
| :--- | :--- | :--- |
| `/api/v1/workers/register*` | Public (Quarantine Rate-Limited) | Ed25519 Public Key in body; rate-limited to 5 req/min per IP. |
| `/api/v1/auth/worker` | Node Pre-Shared Secret | `worker_id` + `secret_token` verified against Argon2id hash. |
| `/api/v1/auth/operator/login` | Public (Credentials) | Username + Password verified against Argon2id hash; issues EdDSA JWT. |
| `/api/v1/auth/operator/password`| Any Authenticated Operator/Auditor | `Authorization: Bearer <operator_jwt>` (Self-service password update) |
| `/api/v1/admin/accounts/*` | `CLUSTER_ADMIN` | `Authorization: Bearer <operator_jwt>` (User & RBAC administration) |
| `/api/v1/admin/audit-logs/*` | `CLUSTER_ADMIN` or `AUDITOR` | `Authorization: Bearer <operator_jwt>` (WORM compliance log access) |
| `/api/v1/admin/workers/*` | `CLUSTER_ADMIN` or `OPERATOR` | `Authorization: Bearer <operator_jwt>` (Worker attestation & fleet control) |
| `/api/v1/admin/secrets/*` | `CLUSTER_ADMIN` | `Authorization: Bearer <operator_jwt>` (AES-256-GCM vault CRUD) |
| `/api/v1/tasks*`, `/bytecode*` | `CLUSTER_ADMIN` or `OPERATOR` | `Authorization: Bearer <operator_jwt_or_api_key>` (Workload orchestration)|
| `/api/v1/cluster/*` | Any Authenticated Operator/Auditor | `Authorization: Bearer <operator_jwt>` (Read-only cluster telemetry) |

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

### 2.3 List Quarantined Enrollments (`GET /api/v1/admin/workers/enrollments`)
Retrieves all pending worker enrollments currently awaiting operator attestation.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Response (`200 OK`):
```json
[
  {
    "enrollment_id": "enr-8821-4920-a1b2",
    "worker_id": "compute-linux-east-042",
    "hostname": "wk-east-042.internal.corp",
    "source_ip": "10.0.4.12",
    "os": "linux",
    "architecture": "x86_64",
    "fingerprint_hex": "4E:8A:BC:31:09:FF:7D:12",
    "visual_code": "Tiger-Galaxy-77",
    "allocatable_cores": 16,
    "max_memory_bytes": 34359738368,
    "submitted_at": "2026-10-15T15:00:00Z"
  }
]
```

### 2.4 Approve Worker Enrollment (`POST /api/v1/admin/workers/enrollments/{enrollment_id}/approve`)
Approves a quarantined node, generating a cryptographically secure 256-bit secret token.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Request Payload:
```json
{
  "assigned_cluster": "production-us-east"
}
```

#### Response (`200 OK`):
```json
{
  "worker_id": "compute-linux-east-042",
  "status": "APPROVED",
  "assigned_cluster": "production-us-east"
}
```

### 2.5 Reject Worker Enrollment (`POST /api/v1/admin/workers/enrollments/{enrollment_id}/reject`)
Rejects a quarantined node and optionally blacklists its source IP.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Request Payload:
```json
{
  "reason": "Host fingerprint mismatch with datacenter inventory",
  "blacklist_ip": true
}
```

#### Response (`200 OK`):
```json
{
  "worker_id": "compute-linux-east-042",
  "status": "REJECTED"
}
```

### 2.6 List Fleet Workers (`GET /api/v1/workers`)
Retrieves all enrolled fleet workers with active telemetry, resource utilization, and status.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Response (`200 OK`):
```json
[
  {
    "worker_id": "compute-linux-east-042",
    "hostname": "wk-east-042.internal.corp",
    "source_ip": "10.0.4.12",
    "os": "linux",
    "architecture": "x86_64",
    "state": "ONLINE",
    "allocatable_cores": 16,
    "max_memory_bytes": 34359738368,
    "cpu_load_pct": 24.5,
    "memory_used_bytes": 8589934592,
    "active_tasks_count": 3,
    "last_heartbeat": "2026-10-15T15:04:12Z"
  }
]
```

### 2.7 Toggle Worker Drain Mode (`POST /api/v1/admin/workers/{worker_id}/drain`)
Toggles drain mode on an active worker node (`ONLINE` <-> `DRAINING`). When draining, scheduler assigns no new tasks to the node while in-flight tasks finish execution.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Response (`200 OK`):
```json
{
  "worker_id": "compute-linux-east-042",
  "draining": true,
  "state": "DRAINING"
}
```

### 2.8 Revoke Worker Node (`POST /api/v1/admin/workers/{worker_id}/revoke`)
Revokes a worker's cluster lease, terminates active LCTP WebSocket sessions, and transitions state to `REVOKED`.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Response (`200 OK`):
```json
{
  "worker_id": "compute-linux-east-042",
  "status": "REVOKED"
}
```

### 2.9 Cluster Aggregate Statistics (`GET /api/v1/cluster/stats`)
Returns high-level cluster metrics for cockpit telemetry ribbons.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Response (`200 OK`):
```json
{
  "total_workers": 42,
  "online_workers": 38,
  "draining_workers": 2,
  "offline_workers": 2,
  "total_allocatable_cores": 512,
  "total_allocatable_memory_bytes": 1099511627776,
  "queue_depth": 0
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
  "account_id": "c7a8b9d0-1234-4567-89ab-cdef01234567",
  "username": "admin@legatus.internal",
  "role": "CLUSTER_ADMIN"
}
```

### 3.3 Operator Password Change (`PUT /api/v1/auth/operator/password`)
Enables an authenticated operator or auditor to update their own password. Requires validating the current password prior to setting the new password.

#### Request Headers:
* `Authorization: Bearer <operator_jwt>`

#### Request Payload:
```json
{
  "current_password": "correct-horse-battery-staple",
  "new_password": "correct-horse-battery-staple-NEW-2026!"
}
```

#### Response (`200 OK`):
```json
{
  "status": "PASSWORD_UPDATED",
  "updated_at": 1728907250000
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

### 4.5 Third-Party Webhook Ingestion Gateway (`POST /api/v1/webhooks/{function_name}`)
Ingests arbitrary incoming JSON payloads from third-party services (e.g., GitHub webhooks, payment providers, alerting systems) and enqueues them directly as Solix compute tasks without requiring a cluster operator JWT.

#### Query Parameters & Headers:
* `priority` (Optional query param): `CRITICAL`, `HIGH`, `NORMAL`, `LOW` (Defaults to `NORMAL`).
* `X-Idempotency-Key` (Optional header): Enforces deduplication tracking.
* `X-Legatus-Webhook-Secret` (Optional header / query param `secret`): Verified against `legatus.webhooks.secret` when secret authentication is enabled.

#### Request Payload:
```json
{
  "ref": "refs/heads/main",
  "action": "push",
  "commit": "a1b2c3d4"
}
```

#### Response (`202 Accepted`):
```json
{
  "task_id": "task-20261014-a9f812bc",
  "status": "QUEUED",
  "submitted_at": 1728950400000
}
```

---

## 5. Fleet Administration & Quarantine Endpoints

| Method & Path | Purpose | Required Role | Success Code |
| :--- | :--- | :--- | :--- |
| `GET /api/v1/bytecode/{sha256}` | Streams raw compiled bytecode binary directly to worker nodes. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` / `304 Not Modified` |
| `GET /api/v1/workers` | Lists all registered workers, states (`ONLINE`, `DRAINING`, `OFFLINE`), and load metrics. | `CLUSTER_ADMIN` / `OPERATOR` / `AUDITOR` | `200 OK` |
| `GET /api/v1/admin/workers/enrollments` | Lists all quarantined nodes currently in `PENDING_APPROVAL`. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` |
| `POST /api/v1/admin/workers/enrollments/{id}/approve` | Approves a quarantined node, generates its Argon2id secret token, and assigns its cluster pool. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` |
| `POST /api/v1/admin/workers/enrollments/{id}/reject` | Rejects a quarantined node and optionally blacklists the source IP. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` |
| `POST /api/v1/admin/workers/{worker_id}/drain` | Remotely toggles drain mode on an active worker. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` |
| `POST /api/v1/admin/workers/{worker_id}/revoke` | Immediately revokes a worker's credentials and severs its active WebSocket session. | `CLUSTER_ADMIN` / `OPERATOR` | `200 OK` |
| `GET /api/v1/cluster/stats` | Returns aggregate cluster cores, memory, queue depth, and 24h throughput statistics. | Any Authenticated | `200 OK` |

---

## 6. Operator Account Management & RBAC Endpoints

All endpoints under `/api/v1/admin/accounts` require an authenticated operator session with the **`CLUSTER_ADMIN`** role.

### 6.1 List Operator Accounts (`GET /api/v1/admin/accounts`)
Returns a paginated list of registered accounts with optional role and status filtering.

#### Query Parameters:
* `role` (Optional): Filter by `CLUSTER_ADMIN`, `OPERATOR`, `AUDITOR`.
* `status` (Optional): Filter by `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`.
* `page` (Default: `0`): Page index.
* `size` (Default: `20`, Max: `100`): Page size.

#### Response (`200 OK`):
```json
{
  "content": [
    {
      "account_id": "c7a8b9d0-1234-4567-89ab-cdef01234567",
      "username": "admin@legatus.internal",
      "email": "admin@legatus.internal",
      "role": "CLUSTER_ADMIN",
      "status": "ACTIVE",
      "failed_login_attempts": 0,
      "lockout_until": null,
      "created_at": 1728900000000,
      "updated_at": 1728907200000
    },
    {
      "account_id": "d8e9f0a1-2345-6789-abcd-ef0123456789",
      "username": "sre-lead@legatus.internal",
      "email": "sre-lead@legatus.internal",
      "role": "OPERATOR",
      "status": "ACTIVE",
      "failed_login_attempts": 0,
      "lockout_until": null,
      "created_at": 1728903600000,
      "updated_at": 1728903600000
    }
  ],
  "page": 0,
  "size": 20,
  "total_elements": 2,
  "total_pages": 1
}
```

### 6.2 Create Operator Account (`POST /api/v1/admin/accounts`)
Provisions a new operator or compliance auditor account. Passwords **MUST** satisfy complexity constraints (minimum 12 characters).

#### Request Payload:
```json
{
  "username": "auditor-external@compliance.corp",
  "email": "auditor-external@compliance.corp",
  "role": "AUDITOR",
  "initial_password": "super-secure-auditor-pass-2026!"
}
```

#### Response (`201 Created`):
```json
{
  "account_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
  "username": "auditor-external@compliance.corp",
  "email": "auditor-external@compliance.corp",
  "role": "AUDITOR",
  "status": "ACTIVE",
  "created_at": 1728908000000
}
```

### 6.3 Update Account Profile & Role (`PUT /api/v1/admin/accounts/{account_id}`)
Modifies profile email and assigned RBAC role.

#### Request Payload:
```json
{
  "email": "auditor-promoted@compliance.corp",
  "role": "CLUSTER_ADMIN"
}
```

#### Response (`200 OK`):
```json
{
  "account_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
  "username": "auditor-external@compliance.corp",
  "email": "auditor-promoted@compliance.corp",
  "role": "CLUSTER_ADMIN",
  "status": "ACTIVE",
  "updated_at": 1728908100000
}
```

### 6.4 Mutate Account Status (`PATCH /api/v1/admin/accounts/{account_id}/status`)
Transitions account lifecycle between `ACTIVE`, `SUSPENDED`, or `DEACTIVATED`.

#### Request Payload:
```json
{
  "status": "SUSPENDED",
  "reason": "Administrative suspension pending security review"
}
```

#### Response (`200 OK`):
```json
{
  "account_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
  "status": "SUSPENDED",
  "updated_at": 1728908200000
}
```

### 6.5 Soft-Delete Account (`DELETE /api/v1/admin/accounts/{account_id}`)
In accordance with compliance auditability invariants, accounts **MUST NOT** be purged from the database via hard SQL `DELETE`. Invoking this endpoint transitions the account to `DEACTIVATED` and immediately revokes all active JWT sessions.

#### Response (`200 OK`):
```json
{
  "account_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
  "status": "DEACTIVATED",
  "message": "Account successfully deactivated. Historical audit records preserved."
}
```

### 6.6 Force Reset Password (`POST /api/v1/admin/accounts/{account_id}/reset-password`)
Forces a password reset for a target account.

#### Request Payload:
```json
{
  "temporary_password": "new-temporary-secure-pass-2026!"
}
```

#### Response (`200 OK`):
```json
{
  "account_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
  "status": "PASSWORD_RESET_SUCCESS",
  "updated_at": 1728908300000
}
```

---

## 7. Statutory Compliance & Immutable WORM Audit Trail Endpoints

Endpoints in this section implement regulatory compliance reporting and hash-chain verification for statutory audits (SOC 2 Type II, ISO/IEC 27001, HIPAA, GDPR Art. 30). Endpoints require either the **`CLUSTER_ADMIN`** or **`AUDITOR`** role.

### 7.1 Query Immutable Audit Logs (`GET /api/v1/admin/audit-logs`)
Retrieves cryptographically chained audit log records.

> [!IMPORTANT]
> **Meta-Auditing Invariant:** Invoking this endpoint automatically appends an `AUDIT_LOG_ACCESSED` event to the immutable audit chain recording the caller's identity, timestamp, and query parameters.

#### Query Parameters:
* `actor_id` (Optional): Filter by operator or worker identity.
* `event_type` (Optional): Filter by event mnemonic (e.g. `ACCOUNT_CREATED`, `WORKER_APPROVED`, `TASK_SUBMITTED`).
* `from_seq` / `to_seq` (Optional): Filter by sequence numbers.
* `since` / `until` (Optional): Millisecond UTC epoch range.
* `page` (Default: `0`), `size` (Default: `50`, Max: `200`).

#### Response (`200 OK`):
```json
{
  "content": [
    {
      "event_id": "f1a2b3c4-d5e6-7890-abcd-ef1234567890",
      "sequence_num": 1042,
      "timestamp": 1728907200000,
      "actor_id": "admin@legatus.internal",
      "actor_role": "CLUSTER_ADMIN",
      "event_type": "ACCOUNT_CREATED",
      "target_type": "ACCOUNT",
      "target_id": "e9f0a1b2-3456-789a-bcde-f0123456789a",
      "action_status": "SUCCESS",
      "metadata": {
        "created_role": "AUDITOR",
        "username": "auditor-external@compliance.corp"
      },
      "source_ip": "192.168.1.100",
      "prev_record_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
      "record_hash": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
      "compliance_flag": "SOC2_AUDIT_TRAIL"
    }
  ],
  "page": 0,
  "size": 50,
  "total_elements": 1042,
  "total_pages": 21
}
```

### 7.2 Verify Cryptographic Hash Chain (`GET /api/v1/admin/audit-logs/verify`)
Executes an integrity verification scan across the audit log hash chain, verifying every record's SHA-256 hash against its predecessor from genesis (`sequence_num: 1`) to head.

> [!IMPORTANT]
> **Meta-Auditing Invariant:** Invoking this endpoint automatically appends an `AUDIT_LOG_VERIFIED` event to the immutable chain.

#### Response when Intact (`200 OK`):
```json
{
  "status": "VALID",
  "verified_records_count": 1042,
  "from_sequence": 1,
  "to_sequence": 1042,
  "genesis_hash": "0000000000000000000000000000000000000000000000000000000000000000",
  "head_hash": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
  "chain_integrity": true,
  "tamper_detected": false,
  "discrepancy": null
}
```

#### Response when Tampered (`500 Internal Server Error`):
```json
{
  "timestamp": 1728907300000,
  "status": 500,
  "error_code": "ERR_AUDIT_CHAIN_CORRUPTED",
  "message": "Cryptographic hash chain broken at sequence number 42: Record hash does not match recomputed SHA-256 digest.",
  "path": "/api/v1/admin/audit-logs/verify",
  "trace_id": "f7e8d9c0-b1a2-3456-7890-abcdef123456"
}
```

### 7.3 Export Signed Audit Trail (`POST /api/v1/admin/audit-logs/export`)
Generates a cryptographically verifiable export package for external regulatory auditors.

#### Request Payload:
```json
{
  "format": "JSON_CANONICAL",
  "since": 1728864000000,
  "until": 1728950400000,
  "compliance_standard": "SOC2_TYPE2"
}
```

#### Response (`200 OK`):
```json
{
  "export_id": "exp-20261014-0012",
  "generated_at": 1728907400000,
  "exported_by": "auditor-external@compliance.corp",
  "record_count": 1042,
  "from_sequence": 1,
  "to_sequence": 1042,
  "head_hash": "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
  "records": [ ... ],
  "meta_audit_sequence": 1043
}
```

### 7.4 Statutory Non-Deletability Rejection (`DELETE /api/v1/admin/audit-logs/*`)
All attempts to delete, truncate, or drop audit log records **MUST** be rejected immediately.

#### Response (`405 Method Not Allowed` / `403 Forbidden`):
```json
{
  "timestamp": 1728907500000,
  "status": 405,
  "error_code": "ERR_AUDIT_LOG_IMMUTABLE",
  "message": "Statutory Violation: Audit log records are legally immutable under SOC 2, ISO 27001, HIPAA, and GDPR regulatory compliance standards and cannot be deleted or purged.",
  "path": "/api/v1/admin/audit-logs/all",
  "trace_id": "a1b2c3d4-e5f6-7890-1234-567890abcdef"
}
```
*Remediation & Security Action:* Emits a high-severity `AUDIT_LOG_TAMPER_ATTEMPT` audit entry with `action_status = 'DENIED'` recording the caller IP and credentials.

---

## 8. Verification & Compliance Checklist

- [ ] **TLS 1.3 Mandate:** All endpoints reject non-TLS traffic.
- [ ] **Status Code Alignment:** Task exit codes (`0`, `1`, `71`, `75`, `124`, `130`, `137`, `139`) match `worker-spec.md` and `worker-wire.md`.
- [ ] **Burn-on-Read Tickets:** `/api/v1/auth/worker` issues 30-second single-use tickets.
- [ ] **Rate Limiting:** `/api/v1/workers/register` enforces the 5 req/min/IP and 50 pending queue ceiling.
- [ ] **RBAC Tier Enforcement:** Account manipulation endpoints strictly restricted to `CLUSTER_ADMIN`; audit verification and query endpoints restricted to `CLUSTER_ADMIN` and `AUDITOR`.
- [ ] **Statutory WORM Non-Deletability:** Audit log `DELETE` requests rejected with HTTP `405` (`ERR_AUDIT_LOG_IMMUTABLE`) and recorded via meta-audit log.
- [ ] **Cryptographic Hash Chaining:** Audit log entries verify sequential SHA-256 derivation ($H_i = \text{SHA256}(\dots)$).