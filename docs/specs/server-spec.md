# Legatus Server Subsystem Specification

## Document Metadata
* **Subsystem:** `server` (Centralized Cluster Broker, Task Scheduler & Studio Stream Engine)
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Backend Systems Engineers, Distributed Infrastructure Architects, Security Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Host Runtime:** OpenJDK / Eclipse Temurin Java 21 LTS (Project Loom Virtual Threads enabled)
* **Related Specifications:**
  * `docs/specs/worker-spec.md`
  * `docs/specs/studio-spec.md`
  * `docs/specs/protocols/worker-wire.md`
  * `docs/specs/protocols/studio-wire.md`
  * `docs/specs/protocols/api-endpoint.md`
  * `docs/dev/decisions/0001-adopt-jhipster-for-server-backend.md`
  * `docs/dev/guidelines/authoring-specs.md` (GL-0005)
  * `docs/dev/guidelines/language-conventions.md` (GL-0011)

---

## 1. System Architecture & Binary Topology

The **Legatus Server** (`legatus-server`) is a single-binary, self-contained JVM orchestration broker that manages worker enrollment, authenticates ephemeral transport leases, schedules Solix bytecode execution across distributed compute nodes, persists cluster state in an encrypted embedded relational database, and broadcasts real-time telemetry to the **Legatus Studio** administrative cockpit.

```
+---------------------------------------------------------------------------------------------------+
|                                   LEGATUS SERVER (Java 21 LTS)                                    |
|                                                                                                   |
|  +-----------------------------+   +-----------------------------+   +-------------------------+  |
|  |   REST API Gateway (TLS)    |   |  LCTP Worker Broker (WSS)   |   | Studio Stream Engine    |  |
|  | - Node Enrollment           |   | - SESSION_INIT / ACK        |   | - Live Telemetry Push   |  |
|  | - Ephemeral Ticket Issuer   |   | - Heartbeat Dead-Man Switch |   | - Approval Events       |  |
|  | - Task Submission & Control |   | - DISPATCH_TASK / ABORT     |   | - Task Log Multiplexer  |  |
|  +--------------+--------------+   +--------------+--------------+   +------------+------------+  |
|                 |                                 |                               |               |
|                 +---------------------------------+-------------------------------+               |
|                                                   |                                               |
|                                                   v                                               |
|                 +-----------------------------------------------------------------+               |
|                 |         Virtual Thread Execution Bus (Project Loom)             |               |
|                 +-----------------------------------------------------------------+               |
|                         |                                 |                       |               |
|                         v                                 v                       v               |
|  +---------------------------------------------+   +-------------+   +-------------------------+  |
|  |     Deterministic Task Scheduler Engine     |   |  Caffeine   |   |   AES-256-GCM Secret    |  |
|  | - Capacity & Quota Bin-Packing              |   |  In-Memory  |   |   Vault & Argon2id      |  |
|  | - Priority Queue Dispatching                |   |  Ticket     |   |   Authentication Engine |  |
|  | - Fault Re-Queueing & Retry Manager         |   |  Cache      |   |                         |  |
|  +----------------------+----------------------+   +-------------+   +------------+------------+  |
|                         |                                                         |               |
|                         +----------------------------+----------------------------+               |
|                                                      |                                            |
|                                                      v                                            |
|                 +-----------------------------------------------------------------+               |
|                 |         Embedded H2 Persistence Engine (AES-128 At-Rest)        |               |
|                 | - Worker Registry, Bytecode Blobs, Task Ledger, Audit Logs      |               |
|                 +-----------------------------------------------------------------+               |
+---------------------------------------------------------------------------------------------------+
```

### 1.1 Architectural Invariants
1. **Zero Container or External Database Dependency:** The server **MUST** operate as a standalone executable JAR requiring zero external Docker containers, Redis instances, or standalone database servers. All persistent state resides in a file-backed, encrypted H2 database, while transient session tickets reside in an in-process memory cache.
2. **Project Loom Concurrency Model:** All incoming HTTP requests, LCTP WebSocket worker sessions, Studio telemetry streams, and background scheduling loops **MUST** execute on Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Blocking I/O operations **SHALL NOT** pin carrier platform threads.
3. **Immutable Domain Transfer Records:** All REST DTOs, LCTP wire payloads, and internal event bus messages **MUST** be modeled as immutable Java `record` types coupled with `sealed` interfaces for exhaustive pattern matching.
4. **Cryptographic Defense-in-Depth:** Network traffic **MUST** enforce TLS 1.3. Worker static tokens **MUST** be hashed via Argon2id. Guest task secrets **MUST** be encrypted via AES-256-GCM prior to database storage, and the H2 database file itself **MUST** enforce page-level encryption at rest (`CIPHER=AES`).

---

## 2. Technology Stack & Component Responsibilities

The server subsystem standardizes on the following dependencies managed via Maven (`server/pom.xml`):

| Component Role | Selected Dependency | Minimum Version | Functional Mandate |
| :--- | :--- | :--- | :--- |
| **Core Framework** | `Spring Boot` (JHipster Core) | `v3.3+` | Dependency injection, lifecycle management, configuration binding, and actuator health endpoints. |
| **Web & WebSocket Engine** | `Spring WebMVC` & `WebSocket` | `v6.1+` | Serves TLS 1.3 REST endpoints and full-duplex RFC 6455 WebSocket connections on Virtual Threads. |
| **Embedded Database** | `H2 Database Engine` | `v2.2+` | Embedded file-based ACID relational storage with `AUTO_SERVER=TRUE` and `CIPHER=AES` encryption. |
| **Schema Migrations** | `Liquibase` | `v4.27+` | Deterministic, checksum-verified SQL schema evolution on server boot. |
| **ORM & Persistence** | `Spring Data JPA` / `Hibernate` | `v6.5+` | Transactional entity persistence, optimistic locking (`@Version`), and query execution. |
| **In-Memory Ticket Cache**| `Caffeine` | `v3.1+` | High-performance, lock-free in-memory cache enforcing 30-second TTL and atomic burn-on-read semantics. |
| **Cryptography Suite** | `BouncyCastle` (`bcprov-jdk18on`)| `v1.78+` | Argon2id password hashing, Ed25519 signature verification, and AES-256-GCM envelope encryption. |
| **JSON Serialization** | `Jackson` (`jackson-databind`) | `v2.17+` | Strict schema serialization and deserialization directly to Java 21 `record` types. |
| **Observability** | `Micrometer` | `v1.13+` | Internal metrics collection exported to Studio cockpit and optional Prometheus scrapers. |
| **Test Verification** | `JUnit 5` & `AssertJ` | `v5.10+` | Hermetic unit and slice integration testing with in-memory H2 fixtures. |

---

## 3. Filesystem & Operating System Boundaries

The server **MUST** dynamically resolve configuration, database files, keystore certificates, and log paths across Linux, macOS, and Windows without hardcoded path separators:

| Resource Role | Linux / POSIX Standard | macOS Standard | Windows Standard | Override Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **Configuration File** (`server.yml`) | `/etc/legatus/server.yml` | `/Library/Application Support/Legatus/server.yml` | `%ProgramData%\Legatus\server.yml` | `--spring.config.location` or `LEGATUS_SERVER_CONFIG` |
| **H2 Database Files** (`legatus-db.mv.db`) | `/var/lib/legatus/db/` | `/Library/Application Support/Legatus/db/` | `%ProgramData%\Legatus\db\` | `legatus.storage.db-dir` |
| **TLS Keystore** (`keystore.p12`) | `/etc/legatus/tls/keystore.p12` | `/Library/Application Support/Legatus/tls/keystore.p12` | `%ProgramData%\Legatus\tls\keystore.p12` | `server.ssl.key-store` |
| **Bytecode Cache** | `/var/lib/legatus/bytecode/` | `/Library/Application Support/Legatus/bytecode/` | `%ProgramData%\Legatus\bytecode\` | `legatus.storage.bytecode-dir` |
| **Server Logs** (`server.log`) | `/var/log/legatus/server.log` | `/var/log/legatus/server.log` | `%ProgramData%\Legatus\logs\server.log` | `logging.file.name` |

---

## 4. Contractual State Machines & Core Subsystems

### 4.1 Worker Lifecycle & Quarantine Enrollment FSM

The server manages the state of every compute node in the cluster according to six deterministic states:

```
                   +-----------------------+
                   |   POST /register      |
                   +-----------+-----------+
                               |
                               v
                   +-----------------------+
         +-------- |   PENDING_APPROVAL    | --------+
         |         |     (Quarantined)     |         |
         |         +-----------+-----------+         |
(15m TTL Expired)              |            (Operator Rejects)
         |            (Operator Approves)            |
         v                     |                     v
+-----------------+            |            +-----------------+
|     EXPIRED     |            |            |     REVOKED     |
+-----------------+            v            +-----------------+
                   +-----------------------+         ^
                   |       APPROVED        |         |
                   |  (Token Issued, Idle) |         |
                   +-----------+-----------+         |
                               |                     |
                     (LCTP SESSION_INIT)             |
                               |                     |
                               v                     |
                   +-----------------------+         |
         +-------> |        ONLINE         | --------+
         |         | (Accepting Dispatches)|  (Operator Revokes)
         |         +-----------+-----------+
         |                     |
  (Reconnected)       (CLI Drain Toggle)
         |                     |
         |                     v
         |         +-----------------------+
         |         |       DRAINING        |
         |         | (Finishing In-Flight) |
         +---------+-----------+-----------+
         |                     |
         |        (Socket Closed / 15s Timeout)
         |                     |
         |                     v
         |         +-----------------------+
         +-------- |        OFFLINE        |
                   +-----------------------+
```

#### Enrollment & DoS Protection Invariants:
1. **Rate Limiting:** The `POST /api/v1/workers/register` endpoint **MUST** enforce a token-bucket rate limiter permitting a maximum of **5 requests per IP address per 60 seconds**.
2. **Quarantine Queue Ceiling:** The server **MUST** cap total global `PENDING_APPROVAL` records at **50 nodes**. Additional registration requests while full **MUST** be rejected with HTTP `429 Too Many Requests`.
3. **Fingerprint Derivation:** Upon receiving a registration request, the server **MUST** independently compute $\text{SHA256}(\text{public\_key})$ and the deterministic 3-word visual confirmation code, pushing a `WORKER_ENROLLMENT_REQUEST` frame to all connected Studio cockpits.
4. **Secret Token Generation:** When an operator approves a node, the server **MUST** generate a 256-bit cryptographically random token (`wk_sec_<64_hex_chars>`), hash it using **Argon2id** ($m=65536\text{ KB}, t=3, p=4$) for database persistence, and return the plaintext token exactly once on the worker's next status poll.
5. **Bounded Argon2id Concurrency:** To prevent off-heap memory exhaustion during burst enrollment spikes (e.g., 50 concurrent approvals requiring $50 \times 64\text{ MB} = 3.2\text{ GB}$ of RAM), Argon2id key derivation **MUST** execute exclusively on a dedicated bounded executor pool capped at 4 concurrent tasks (`Executors.newFixedThreadPool(4)`), limiting peak off-heap memory consumption to $256\text{ MB}$.

---

### 4.2 Ephemeral Ticket Exchange & Burn-on-Read Cache

To prevent static credentials from appearing in WebSocket upgrade URLs or proxy logs, the server implements a two-step authentication broker:

1. **Ticket Issuance (`POST /api/v1/auth/worker`):**
   * Validates `worker_id` and verifies `secret_token` against the stored Argon2id digest.
   * Updates the worker's declared `allocatable_capacity` (`allocatable_cores`, `max_memory_bytes`, `max_instruction_budget_per_task`) in the registry.
   * Generates a single-use UUIDv4 ticket (`ws_tkt_<uuid>`) and inserts it into the `Caffeine` cache with a strict **30-second TTL**.
2. **Atomic Burn-on-Read (`GET /ws/worker?ticket=...`):**
   * During the HTTP `101 Switching Protocols` handshake, the WebSocket handshake interceptor atomically removes the ticket from cache.
   * Concurrent replay attempts with the same ticket string are guaranteed to fail lookup and **MUST** be rejected immediately with HTTP `401 Unauthorized` (or WebSocket Close Code `1008`).

---

### 4.3 Task Lifecycle & Deterministic Scheduling Engine

The server orchestrates guest Solix functions through an eight-state lifecycle:

```
                   +-----------------------+
                   |    TASK SUBMITTED     |
                   +-----------+-----------+
                               |
                               v
                   +-----------------------+
         +-------> |        QUEUED         | <-------+
         |         +-----------+-----------+         |
         |                     |                     |
  (Retry Policy        (Worker Allocated)     (Worker Offline /
   Attempts < Max)             |               Dead-Man Trigger)
         |                     v                     |
         |         +-----------------------+         |
         |         |      DISPATCHED       | --------+
         |         +-----------+-----------+         |
         |                     |                     |
         |          (First Heartbeat / Progress)     |
         |                     |                     |
         |                     v                     |
         |         +-----------------------+         |
         +-------- |       EXECUTING       | --------+
         |         +-----------+-----------+
         |                     |
         |      +--------------+--------------+--------------+
         |      |              |              |              |
         v      v              v              v              v
+---------------+  +---------------+  +---------------+  +---------------+
|    FAILED /   |  |   COMPLETED   |  |    ABORTED    |  |   QUOTA /     |
|    CRASHED    |  |  (Exit == 0)  |  | (Cancelled)   |  |  TIMEOUT ERR  |
+---------------+  +---------------+  +---------------+  +---------------+
```

#### Capacity Accounting & Bin-Packing Equations
For every `ONLINE` worker $w$, the scheduler tracks real-time resource availability based on the worker's declared capacity and currently assigned tasks $T_w$:

* **Available CPU Cores ($C_{\text{avail}}(w)$):**
  $$C_{\text{avail}}(w) = C_{\text{allocatable}}(w) - |T_w|$$
* **Available Memory ($M_{\text{avail}}(w)$):**
  $$M_{\text{avail}}(w) = M_{\text{max}}(w) - \sum_{t \in T_w} M_{\text{req}}(t)$$

A worker $w$ is **Eligible** to execute candidate task $t_{\text{cand}}$ if and only if all five scheduling predicates hold:
1. $\text{State}(w) == \texttt{ONLINE}$
2. $\text{Draining}(w) == \texttt{false}$
3. $C_{\text{avail}}(w) \ge 1$
4. $M_{\text{avail}}(w) \ge M_{\text{req}}(t_{\text{cand}}) \quad \text{AND} \quad M_{\text{free\_host}}(w) \ge M_{\text{req}}(t_{\text{cand}})$
5. $I_{\text{max\_per\_task}}(w) \ge I_{\text{budget}}(t_{\text{cand}})$

#### Scheduling Algorithm Options
Operators **MAY** configure the active scheduling strategy in `server.yml` under `legatus.scheduler.strategy`:

| Strategy Identifier | Mathematical Selection Rule | Optimal Use Case |
| :--- | :--- | :--- |
| **`LEAST_LOADED`** *(Default)* | $\arg\min_{w} \left( \frac{|T_w|}{C_{\text{allocatable}}(w)} + 0.5 \cdot \frac{\text{CPU}_{\text{host}}(w)}{100} \right)$ | Latency-sensitive microservices & webhooks; spreads load evenly across all nodes. |
| **`BIN_PACK`** | $\arg\min_{w} \left( M_{\text{avail}}(w) \right)$ | Maximizes node density; packs tasks onto partially full nodes so idle nodes can sleep. |
| **`ROUND_ROBIN`** | Cyclic cursor across eligible worker ring | Uniform, homogeneous micro-tasks with identical execution footprints. |
| **`LOCALITY_FIRST`** | Prefers workers that already hold the target `bytecode.sha256` in local cache | Large Solix bytecode binaries or tasks with heavy static initialization. |

---

### 4.4 Heartbeat Dead-Man's Switch, Drain Notices & Session Reconciliation

1. **Active Liveness Sweeper:** A background Virtual Thread wakes every `1000ms` to inspect all `ONLINE` and `DRAINING` worker sessions.
2. **Timeout Detection:** If $t_{\text{now}} - t_{\text{last\_seen}}(w) > 15000\text{ ms}$ ($3 \times t_{\text{heartbeat}}$):
   * The server forcibly closes the WebSocket connection with RFC 6455 Close Code `1011` (`INTERNAL_ERROR` / Dead-Man Timeout).
   * The worker state transitions to `OFFLINE`.
   * **Orphaned Task Recovery:** Every task $t \in T_w$ currently in `DISPATCHED` or `EXECUTING` state is evaluated against its retry policy:
     * If `t.attempt_count < t.max_retries` (and `t.idempotent == true`), the task transitions back to `QUEUED` for immediate dispatch to a healthy node.
     * Otherwise, the task transitions to `FAILED` with error code `ERR_WORKER_LOST`.
3. **Session Reconciliation on Reconnect:** When a reconnected worker transmits `SESSION_INIT` within the 15-second dead-man's window specifying `previous_session_id`:
   * If the previous session is verified, the server re-attaches the session without terminating executing tasks.
   * Tasks listed in `reconcile_tasks` that remain in server `EXECUTING` state are re-linked to the reconnected socket and returned in `reconciled_task_ids`.
   * Tasks previously presumed lost that were already re-dispatched to another node are returned in `discarded_task_ids`, instructing the worker to abort its redundant local runner.
4. **Immediate Drain Handling:** When the server receives a `DRAIN_NOTICE` frame from a worker, the broker immediately updates the node state to `DRAINING` and removes it from the scheduling candidate ring without awaiting the next scheduled heartbeat.
5. **Terminal Task Ledger Acknowledgment:** Upon receiving a terminal `TASK_RESULT` frame, the server commits the task outcome to the database in a database transaction, updates node allocatable capacity, and immediately transmits a `TASK_RESULT_ACK` frame back to the worker.

---

### 4.5 Operator Account Lifecycle & Role-Based Access Control (RBAC) FSM

The server enforces multi-operator Role-Based Access Control (RBAC) across three distinct privilege tiers, governing interactive access from Legatus Studio, the CLI, and external orchestration systems:

```
                   +-----------------------+
                   |  POST /admin/accounts |
                   +-----------+-----------+
                               |
                               v
                   +-----------------------+
                   |   PENDING_ACTIVATION  |
                   +-----------+-----------+
                               | (Activation / Initial Password Set)
                               v
                   +-----------------------+
         +-------> |        ACTIVE         | <-------+
         |         +-----------+-----------+         |
         |                     |                     |
(Admin Unlocks /        (5 Failed Logins /    (Admin Restores)
 15m Lockout Expires)    Admin Suspends)             |
         |                     |                     |
         |                     v                     |
         |         +-----------------------+         |
         +-------- |       SUSPENDED       |         |
                   +-----------+-----------+         |
                               |                     |
                       (Admin Deactivates)           |
                               |                     |
                               v                     |
                   +-----------------------+         |
                   |      DEACTIVATED      | --------+
                   |     (Soft-Deleted)    |
                   +-----------------------+
```

#### 4.5.1 RBAC Roles & Privilege Matrix
The server defines three normative roles:
* **`CLUSTER_ADMIN`:** Full supervisory authority. Can create and manage operator accounts, assign and revoke roles, approve/reject quarantined workers, configure secrets in `secrets_vault`, purge tasks, and execute all operational workflows.
* **`OPERATOR`:** Operational execution authority. Can approve/reject quarantined workers, deploy Solix bytecode functions, submit/abort compute tasks, and view cluster telemetry. **MUST NOT** manage operator accounts, access raw secret vault payloads, or invoke maintenance purge endpoints.
* **`AUDITOR`:** Read-only compliance authority. Can inspect cluster status, view tasks and functions, inspect immutable audit logs, trigger cryptographic hash-chain verification, and export compliance reports. **MUST NOT** mutate cluster configuration, submit tasks, approve workers, or modify accounts.

| Operation / Endpoint Category | `CLUSTER_ADMIN` | `OPERATOR` | `AUDITOR` | Non-Authenticated |
| :--- | :---: | :---: | :---: | :---: |
| **Account Administration** (`/api/v1/admin/accounts/*`) | Full Access | Denied (`403`) | Denied (`403`) | Denied (`401`) |
| **Self Password Change** (`/api/v1/auth/operator/password`)| Allowed | Allowed | Allowed | Denied (`401`) |
| **Worker Quarantine Approval/Rejection** | Allowed | Allowed | Denied (`403`) | Denied (`401`) |
| **Worker Credential Revocation / Drain** | Allowed | Allowed | Denied (`403`) | Denied (`401`) |
| **Solix Function Upload & Cataloging** | Allowed | Allowed | Denied (`403`) | Denied (`401`) |
| **Task Submission & Cancellation** | Allowed | Allowed | Denied (`403`) | Denied (`401`) |
| **Secrets Vault CRUD** (`/api/v1/admin/secrets/*`) | Full Access | Denied (`403`) | Denied (`403`) | Denied (`401`) |
| **Audit Log Query & Export** (`/api/v1/admin/audit-logs/*`) | Allowed | Denied (`403`) | Allowed | Denied (`401`) |
| **Audit Log Hash-Chain Verification** | Allowed | Denied (`403`) | Allowed | Denied (`401`) |
| **Cluster Telemetry & Read-Only Topology** | Allowed | Allowed | Allowed | Denied (`401`) |

#### 4.5.2 Account Security & Lockout Invariants
1. **Password Hashing:** Account passwords **MUST** be hashed using Argon2id ($m=65536\text{ KB}, t=3, p=4$) on the dedicated bounded executor pool (`Executors.newFixedThreadPool(4)`).
2. **Password Policy:** Passwords **MUST** contain a minimum of 12 characters, including uppercase, lowercase, numbers, and symbols.
3. **Brute-Force Lockout:** If an account accumulates **5 consecutive failed login attempts**, the server **MUST** immediately transition the account status to `SUSPENDED` and set `lockout_until = t_now + 15 minutes`. Requests during lockout **MUST** be rejected with HTTP `423 Locked` (`ERR_ACCOUNT_LOCKED`). Successful authentication resets `failed_login_attempts` to 0.
4. **Soft-Deletion Mandate:** Accounts **MUST NOT** be hard-deleted via SQL `DELETE`. When an administrator invokes account deletion, the server **MUST** transition the account to `DEACTIVATED`, revoke all active JWT sessions, and retain the account record to preserve referential integrity across the immutable audit log.
5. **Initial Bootstrap Account:** Upon initial boot, if the `accounts` table contains zero records, the server **MUST** automatically provision a bootstrap account (`admin@legatus.internal`) with the `CLUSTER_ADMIN` role, deriving its initial password from `LEGATUS_ADMIN_INITIAL_PASSWORD` or generating a 32-character cryptographically secure token output once to stdout with a mandatory change notice.

---

## 5. Persistence Schema & Encryption-at-Rest (`H2`)

The server persists cluster state in an embedded H2 database protected by AES-128 file encryption (`jdbc:h2:file:<path>;CIPHER=AES;AUTO_SERVER=TRUE;FILE_LOCK=FS;LOCK_TIMEOUT=10000`).

### 5.1 Core Relational Tables (Liquibase Managed)

| Table Name | Primary Key | Optimistic Lock | Core Columns & Purpose |
| :--- | :--- | :--- | :--- |
| `accounts` | `account_id` (`UUID`) | `version` (`BIGINT`) | `username` (`VARCHAR(255)` UNIQUE), `email` (`VARCHAR(255)`), `password_hash` (`VARCHAR(255)` Argon2id), `role` (`VARCHAR(32)`: `CLUSTER_ADMIN`, `OPERATOR`, `AUDITOR`), `status` (`VARCHAR(32)`: `PENDING_ACTIVATION`, `ACTIVE`, `SUSPENDED`, `DEACTIVATED`), `failed_login_attempts` (`INT`), `lockout_until` (`TIMESTAMP`), `created_at`, `updated_at`. |
| `workers` | `worker_id` (`VARCHAR(64)`) | `version` (`BIGINT`) | `hostname`, `os`, `arch`, `ed25519_pubkey`, `fingerprint`, `secret_hash` (Argon2id), `state`, `allocatable_cores`, `max_memory_bytes`, `max_instruction_budget`, `last_seen_at`. |
| `functions` | `function_id` (`UUID`) | `version` (`BIGINT`) | `name`, `semver`, `entry_point`, `bytecode_sha256`, `bytecode_blob` (`BLOB`), `default_budget`, `default_memory_bytes`, `default_timeout_ms`, `created_at`. |
| `tasks` | `task_id` (`VARCHAR(64)`) | `version` (`BIGINT`) | `function_id`, `assigned_worker_id`, `status`, `priority` (`INT`), `exit_code`, `instructions_consumed`, `peak_memory_bytes`, `wall_time_ms`, `input_json`, `output_json`, `error_json`, `attempt_count`, `max_retries`, `created_at`, `completed_at`. |
| `secrets_vault` | `secret_key` (`VARCHAR(128)`) | `version` (`BIGINT`) | `scope`, `cipher_text` (`VARBINARY`), `gcm_iv` (`BINARY(12)`), `auth_tag` (`BINARY(16)`), `updated_at`. |
| `audit_logs` | `event_id` (`UUID`) | None (WORM Append-Only)| `sequence_num` (`BIGINT` UNIQUE AUTO_INCREMENT), `timestamp` (`TIMESTAMP`), `actor_id` (`VARCHAR(255)`), `actor_role` (`VARCHAR(32)`), `event_type` (`VARCHAR(64)`), `target_type` (`VARCHAR(64)`), `target_id` (`VARCHAR(255)`), `action_status` (`VARCHAR(32)`), `metadata_json` (`CLOB`), `source_ip` (`VARCHAR(45)`), `prev_record_hash` (`CHAR(64)`), `record_hash` (`CHAR(64)`), `compliance_flag` (`VARCHAR(64)`). |

### 5.2 Envelope Encryption for Task Secrets & Master Key Bootstrap
Guest secrets stored in `secrets_vault` **MUST NOT** rely solely on H2 disk encryption:
1. **Master Key Bootstrapping:** In production environments (`prod` profile), the server **MUST** derive a 256-bit Master Key from the `LEGATUS_MASTER_KEY` environment variable using **HKDF-SHA256**. If `LEGATUS_MASTER_KEY` is omitted, the server **MUST** immediately abort startup with exit code `78` (`EX_CONFIG`). In `dev` profile, an ephemeral key in `.legatus-dev-key` may be used with a prominent warning.
2. Each secret is encrypted individually using **AES-256-GCM** with a unique 96-bit random Initialization Vector (IV).
3. Secrets are decrypted solely in memory immediately prior to constructing a `DISPATCH_TASK` WebSocket frame, and the plaintext byte arrays are zeroed immediately after socket transmission.

### 5.3 Statutory Compliance & Immutable WORM Audit Trail

Legatus Server implements a regulatory-grade Write-Once-Read-Many (WORM) audit subsystem designed to satisfy statutory compliance mandates including **SOC 2 Type II (CC6.8, CC7.2)**, **ISO/IEC 27001:2022 (Control A.8.15 - Logging)**, **HIPAA Security Rule (§ 164.312(b) - Audit Controls)**, and **GDPR Article 30**.

#### 5.3.1 Statutory Zero-Deletion Invariant
1. **Absolute Non-Deletability:** Audit log records **MUST NEVER** be deleted, truncated, or overwritten under any operational circumstance, including maintenance tasks, database compaction, or administrative override.
2. **Database-Level Rejection:** The H2 database schema **MUST** register trigger invariants on the `audit_logs` table that abort any SQL `DELETE` or `UPDATE` statements immediately with a fatal SQL exception:
   `Statutory Violation: Audit log records are legally immutable and cannot be deleted or modified.`
3. **REST Protocol Rejection:** The REST API **MUST NOT** provide any endpoint that facilitates the deletion of audit logs. Any `DELETE /api/v1/admin/audit-logs/*` request **MUST** be rejected with HTTP `405 Method Not Allowed` or HTTP `403 Forbidden` (`ERR_AUDIT_LOG_IMMUTABLE`).
4. **Auto-Purge Exemption:** The server's automated maintenance sweeper (`legatus.retention.auto-purge`) that purges completed task records older than 7 days **MUST EXPLICITLY EXEMPT** `audit_logs`. The `audit_logs` table is permanent and legally immutable.

#### 5.3.2 Cryptographic Hash Chaining (Sequential Tamper-Evidence)
Every record in `audit_logs` is cryptographically chained to its immediate predecessor using sequential SHA-256 hashing, forming an immutable ledger:

```
[Record 1: Genesis]               [Record 2]                         [Record N: Head]
+---------------------------+     +---------------------------+      +---------------------------+
| sequence_num: 1           |     | sequence_num: 2           |      | sequence_num: N           |
| prev_record_hash: 000...0 | --> | prev_record_hash: H_1     | ---> | prev_record_hash: H_{N-1} |
| record_hash: H_1          |     | record_hash: H_2          |      | record_hash: H_N          |
+---------------------------+     +---------------------------+      +---------------------------+
```

1. **Genesis Hash:** For `sequence_num = 1`, `prev_record_hash` **MUST** be set to 64 ASCII zeroes (`0000000000000000000000000000000000000000000000000000000000000000`).
2. **Current Record Hash Derivation ($H_i$):**
   $$H_i = \text{SHA256}(\text{sequence\_num}_i \parallel \text{prev\_record\_hash}_i \parallel \text{timestamp}_i \parallel \text{actor\_id}_i \parallel \text{actor\_role}_i \parallel \text{event\_type}_i \parallel \text{target\_type}_i \parallel \text{target\_id}_i \parallel \text{action\_status}_i \parallel \text{metadata\_json}_i \parallel \text{source\_ip}_i)$$
3. **Sequential Verification Algorithm:** The server provides `/api/v1/admin/audit-logs/verify` which streams records sequentially from sequence 1 to $N$, re-computing $H_i$ and verifying $H_i == \text{record\_hash}_i$ and $\text{prev\_record\_hash}_{i+1} == \text{record\_hash}_i$. If any discrepancy is detected, the verification fails immediately with `ERR_AUDIT_CHAIN_CORRUPTED`, pinpointing the exact corrupt sequence number.

#### 5.3.3 Meta-Auditing ("Auditing the Auditors") Invariant
In compliance with regulatory standards, all operations that inspect, query, verify, or attempt to manipulate audit logs **MUST THEMSELVES GENERATE AN AUDIT RECORD**:
* Every invocation of `GET /api/v1/admin/audit-logs` appends an `AUDIT_LOG_ACCESSED` event recording the querying actor, queried time window, and filter parameters.
* Every invocation of `GET /api/v1/admin/audit-logs/verify` appends an `AUDIT_LOG_VERIFIED` event recording the verification outcome and range.
* Every invocation of `POST /api/v1/admin/audit-logs/export` appends an `AUDIT_LOG_EXPORTED` event recording the exported scope and destination.
* Any unauthorized attempt to delete or alter audit logs triggers an immediate high-priority security event: `AUDIT_LOG_TAMPER_ATTEMPT` with `action_status = 'DENIED'`.

---

## 6. Configurable Server Features & Operational Options (`server.yml`)

The Legatus Server provides modular capabilities that operators can enable or tune depending on their deployment topology:

### 6.1 Optional Feature Matrix

| Feature Module | Config Key | Default | Description |
| :--- | :--- | :--- | :--- |
| **Bytecode Deduplication Cache** | `legatus.features.bytecode-cache` | `true` | Instead of sending large Base64 bytecode in every `DISPATCH_TASK`, sends only `sha256` if the worker already cached the binary. |
| **Cron & Scheduled Triggers** | `legatus.features.cron-scheduler` | `true` | Built-in cron engine that triggers registered Solix functions on recurring schedules (`0 */5 * * * *`). |
| **Webhook Ingestion Gateway** | `legatus.features.webhooks` | `true` | Exposes `/api/v1/webhooks/{function_name}` to turn incoming third-party HTTP POST payloads directly into queued Solix tasks. |
| **Workflow DAG Chaining** | `legatus.features.workflows` | `false` | Allows chaining output JSON of Task A directly into input arguments of Task B (pipeline orchestration). |
| **Auto-Purge Task History** | `legatus.retention.auto-purge` | `true` | Background sweeper that purges completed task logs from `tasks` older than `retention.days` (default: 7 days) to keep the H2 file compact. Under statutory compliance mandates, `audit_logs` is strictly exempted and never purged. |
| **Prometheus Metrics Endpoint**| `management.endpoints.prometheus` | `false` | Exposes `/actuator/prometheus` for external Grafana/Prometheus scraping alongside Legatus Studio. |

### 6.2 Reference Configuration Schema (`server.yml`)

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3
    key-store: /etc/legatus/tls/keystore.p12
    key-store-type: PKCS12

spring:
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:h2:file:/var/lib/legatus/db/legatus;CIPHER=AES;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver

legatus:
  cluster:
    name: "production-core"
    max-pending-enrollments: 50
    enrollment-ttl-seconds: 900
    ticket-ttl-seconds: 30
  heartbeat:
    interval-ms: 5000
    timeout-ms: 15000
  scheduler:
    strategy: LEAST_LOADED
    dispatch-batch-size: 64
    max-queue-depth: 100000
    default-max-retries: 2
  retention:
    auto-purge: true
    completed-task-ttl-hours: 168
```

---

## 7. Failure Modes, Traps & Error Matrix

| Error Condition | Layer / Context | HTTP / WS Code | Canonical Error Mnemonic | System Remediation Action |
| :--- | :--- | :--- | :--- | :--- |
| Enrollment Queue Full (> 50) | REST `/register` | HTTP `429` | `ERR_ENROLLMENT_QUEUE_FULL` | Reject request; alert operator in Studio cockpit of potential DoS. |
| Invalid or Replayed Ticket | WS `/ws/worker` | WS `1008` / HTTP `401`| `ERR_TICKET_INVALID_OR_BURNED`| Abort upgrade immediately; log source IP in security audit table. |
| Bytecode SHA-256 Mismatch | REST Function Upload | HTTP `400` | `ERR_BYTECODE_DIGEST_MISMATCH`| Reject upload; discard binary stream before database commit. |
| Task Queue Saturated | REST Task Submit | HTTP `503` | `ERR_CLUSTER_QUEUE_EXHAUSTED` | Reject submission with `Retry-After` header; apply backpressure. |
| Optimistic Lock Conflict | JPA Scheduler Transaction| Internal Retry | `ERR_CONCURRENT_STATE_MUTATION`| Catch `OptimisticLockingFailureException`; refresh entity and retry selection. |
| Worker Dead-Man Timeout (15s) | LCTP Broker Sweeper | WS `1011` | `ERR_WORKER_HEARTBEAT_TIMEOUT`| Tear down socket; mark worker `OFFLINE`; re-queue eligible tasks. |
| Oversized Wire Frame (> 32MB) | LCTP / Studio WS | WS `1009` | `ERR_FRAME_TOO_LARGE` | Drop WebSocket session immediately to prevent JVM heap exhaustion. |
| Account Suspended / Locked | REST Auth | HTTP `423` | `ERR_ACCOUNT_LOCKED` | Reject login; account locked due to 5 failed attempts or admin suspension. |
| Account Deactivated | REST Auth | HTTP `403` | `ERR_ACCOUNT_DISABLED` | Reject login; soft-deleted accounts cannot authenticate or obtain JWTs. |
| Account Collision | REST `/admin/accounts` | HTTP `409` | `ERR_ACCOUNT_EXISTS` | Reject creation; username or email already registered in cluster. |
| Account Not Found | REST `/admin/accounts` | HTTP `404` | `ERR_ACCOUNT_NOT_FOUND` | Reject mutation; target account ID does not exist in database. |
| Insufficient RBAC Privilege | REST Authorization | HTTP `403` | `ERR_INSUFFICIENT_PRIVILEGES` | Reject request; role lacks required permission; emit audit event. |
| Statutory Log Deletion Prohibited | REST `/admin/audit-logs` | HTTP `405` / `403` | `ERR_AUDIT_LOG_IMMUTABLE` | Reject deletion immediately; emit `AUDIT_LOG_TAMPER_ATTEMPT` audit entry. |
| Audit Hash Chain Checksum Error | REST `/admin/audit-logs/verify`| HTTP `500` | `ERR_AUDIT_CHAIN_CORRUPTED` | Alert operator of tamper incident; isolate database and report sequence ID. |

---

## 8. Verification & Compliance Checklist

Reviewers and backend engineers **MUST** verify the following audit criteria:

- [ ] **Zero Implementation Code:** Document contains zero multi-line Java, SQL, or C++ implementation code blocks (`GL-0005`).
- [ ] **RFC 2119 Precision:** All normative constraints utilize bold, uppercase RFC 2119 keywords.
- [ ] **Project Loom Conformance:** All WebSocket handlers, REST controllers, and schedulers execute on Java 21 Virtual Threads (`GL-0011`).
- [ ] **Cryptographic Compliance:** Argon2id parameters, AES-256-GCM secret vaulting, H2 AES encryption, and TLS 1.3 invariants are enforced.
- [ ] **Scheduling Determinism:** Eligibility predicates and bin-packing equations match worker capacity declarations in `worker-spec.md` and `worker-wire.md`.
- [ ] **Dual Test Coverage:** Positive and negative JUnit 5 test suites verify ticket burn-on-read, dead-man's switch task re-queueing, and rate limiting (`GL-0009`).