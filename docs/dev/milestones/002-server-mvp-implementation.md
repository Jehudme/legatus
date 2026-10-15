# Milestone 002: Legatus Server Orchestration Broker MVP Implementation

## Document Metadata
* **Milestone ID:** MS-002
* **Target Version:** `v1.0.0-server-mvp`
* **Status:** ACTIVE
* **Lead Architect:** @server-lead
* **Target Delivery Date:** 2026-11-01
* **Related ADRs:** ADR-0001, ADR-0003, ADR-0005, ADR-0006

---

## 1. Executive Summary & Objectives

Deliver the production-ready **Legatus Server** (`legatus-server`) orchestration broker in Java 21 LTS and Spring Boot 3.3+ (`ADR-0001`). The server provides quarantine node enrollment with Ed25519 visual fingerprint calculation (`ADR-0005`), Argon2id credential verification on a bounded carrier pool, Caffeine 30-second atomic burn-on-read WebSocket upgrade tickets, full-duplex LCTP (`worker-wire.md`) and LSTCP (`studio-wire.md`) WebSocket brokers running on Project Loom Virtual Threads, deterministic capacity-aware task scheduling, and AES-encrypted embedded H2 relational persistence managed by Liquibase.

---

## 2. Scope & Subsystems Affected

* `server/src/main/java/dev/legatus/server/security/`: TLS 1.3 enforcement, Argon2id hashing pool, RBAC filter (`CLUSTER_ADMIN`, `OPERATOR`, `AUDITOR`), brute-force lockout manager, AES-256-GCM `secrets_vault` envelope encryption, EdDSA operator JWT filter, and Caffeine burn-on-read ticket cache.
* `server/src/main/java/dev/legatus/server/web/rest/`: REST API Gateway (`api-endpoint.md`) covering `/workers/register`, `/auth/worker`, `/auth/operator/*`, `/tasks`, `/bytecode/{sha256}`, `/admin/workers/*`, `/admin/accounts/*`, and `/admin/audit-logs/*`.
* `server/src/main/java/dev/legatus/server/websocket/`: Full-duplex LCTP (`/ws/worker`) and LSTCP (`/ws/studio`) WebSocket handlers, envelope validation, and 15-second dead-man's switch sweepers.
* `server/src/main/java/dev/legatus/server/scheduler/`: Deterministic task scheduler supporting `LEAST_LOADED`, `BIN_PACK`, `ROUND_ROBIN`, and `LOCALITY_FIRST` strategies with optimistic locking (`@Version`).
* `server/src/main/resources/config/liquibase/`: Liquibase schema migrations for `accounts`, `workers`, `functions`, `tasks`, `secrets_vault`, and immutable WORM `audit_logs` on embedded H2 (`CIPHER=AES`).

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Quarantine Enrollment & Rate Limiter (`/api/v1/workers/register`):** Implement Ed25519 public key ingestion, SHA-256 hex fingerprint (`XX:XX:...`) and deterministic 3-word visual code (`Tiger-Galaxy-77`) derivation, 5 req/min/IP token-bucket rate limiter, 50-node global pending queue cap (`429 Too Many Requests`), and 15-minute TTL expiration sweeper. (Issue #201)
  - [x] Ed25519 Fingerprint & Visual Code Service (`Ed25519FingerprintService`) with SHA-256 colon-delimited fingerprints, deterministic 3-word visual confirmation codes, Base64/Hex support, and 100% test coverage.
  - [x] Rate-limited `/api/v1/workers/register`, status polling, and `/api/v1/auth/worker` ticket exchange controller (`WorkerEnrollmentResource`, `WorkerEnrollmentService`) with 5 req/min/IP rate limiting and single-read token delivery.
- [x] `[P0]` **Bounded Argon2id Verification Pool & Ticket Broker (`/api/v1/auth/worker`):** Implement Argon2id ($m=64\text{ MB}, t=3, p=4$) token hashing pinned to a bounded 4-thread pool (`Executors.newFixedThreadPool(4)`) and issue 30-second single-use `ws_tkt_<uuid>` tickets in Caffeine with atomic `asMap().remove(ticket)` burn-on-read during WebSocket handshake. (Issue #202)
  - [x] Bounded Argon2id Service (`Argon2idService`) with 4-carrier thread pool, $m=64\text{ MB}$, $t=3$, $p=4$, and 100% test coverage.
- [x] `[P0]` **Multi-Operator Account & RBAC Security Engine (`/api/v1/admin/accounts`, `/api/v1/auth/operator/*`):** Implement `accounts` table, Argon2id password hashing, EdDSA JWT claims with roles (`CLUSTER_ADMIN`, `OPERATOR`, `AUDITOR`), brute-force lockout (5 failed attempts / 15-minute lock), account CRUD endpoints, soft-deletion semantics, and initial admin bootstrap. (Issue #213)
  - [x] Operator Account & Authentication Service (`OperatorAccountService`) with automatic admin bootstrap (`admin@legatus.internal`), Argon2id password verification, 5-attempt brute-force lockout with HTTP 423 Locked, JWT creation, and single-use Studio WebSocket ticket generation.
  - [x] Operator Authentication & Password Management REST Resource (`OperatorAuthResource`) supporting `/api/v1/auth/operator/login` and self-service password updates with policy checks.
  - [x] Operator RBAC Administration REST Resource (`AccountAdminResource`) with role-based access control (`ROLE_CLUSTER_ADMIN`), account creation, lifecycle mutation, and non-purging soft-deletion.
  - [x] WORM Audit Service (`WormAuditService`) with sequential SHA-256 cryptographic hash chaining ($H_i$), 64-zero Genesis hash, tamper detection, and verified H2 integration.
  - [x] Database zero-deletion trigger (`WormAuditLogTrigger`) registered in Liquibase enforcing statutory non-deletability and non-modifiability on embedded H2.
- [x] `[P0]` **LCTP WebSocket Broker & Session Reconciliation (`/ws/worker`):** Implement Java 21 `sealed interface` wire records (`GL-0011`) for `SESSION_INIT`, `SESSION_ACK`, `HEARTBEAT`, `HEARTBEAT_ACK`, `DISPATCH_TASK`, `TASK_PROGRESS`, `TASK_RESULT`, `TASK_RESULT_ACK`, `DRAIN_NOTICE`, `ABORT_TASK`, and `PROTOCOL_ERROR`, including in-flight `reconcile_tasks` re-attachment on transient reconnects and 32 MB frame ceiling (`1009`). (Issue #203)
  - [x] Sealed Java 21 LCTP wire records (`LctpFrame`) with polymorphic Jackson discriminator mapping across all 11 protocol frames.
  - [x] Full-duplex WebSocket handler (`LctpWorkerWebSocketHandler`) and handshake interceptor (`LctpHandshakeInterceptor`) enforcing 32 MB buffer ceiling and single-use 30s ticket burn-on-read.
  - [x] Transient reconnect session reconciliation recovering in-flight tasks and committing terminal outcomes with `TASK_RESULT_ACK`.
- [x] `[P0]` **Heartbeat Dead-Man's Switch & Orphan Task Recovery:** Implement the 1000ms Virtual Thread liveness sweeper that closes silent worker sockets after 15,000ms (`Close Code 1011`), transitions nodes to `OFFLINE`, and re-queues idempotent tasks (`attempt_count < max_retries`) or marks them `FAILED` (`ERR_WORKER_LOST`). (Issue #204)
  - [x] 1000ms dead-man's switch sweeper (`WorkerDeadManSweeper`) enforcing 15,000ms heartbeat timeout and socket closure with Close Code 1011.
  - [x] Orphan task recovery engine re-queuing retry-eligible idempotent workloads and transitioning non-idempotent tasks to `ERR_WORKER_LOST`.
- [x] `[P0]` **AES-256-GCM Secret Vault & H2 Encryption at Rest:** Enforce `LEGATUS_MASTER_KEY` HKDF-SHA256 bootstrap (aborting with exit code `78` in `prod` if missing), per-secret AES-256-GCM encryption with 96-bit IVs, plaintext zeroing after `DISPATCH_TASK` serialization, and H2 `CIPHER=AES;FILE_LOCK=FS` persistence. (Issue #205)
  - [x] AES-256-GCM Secret Vault Service (`SecretVaultService`) with HKDF-SHA256 master key derivation, production exit code 78 abort enforcement, tamper-evident authenticated decryption, and memory zeroization.
  - [x] Secret repository persistence integration, zeroizable task dispatch callback, and `SecretAdminResource` (`/api/v1/admin/secrets`) with RBAC enforcement and WORM audit logging.

### 3.2 P1: High Priority (Milestone Core)
- [ ] `[P1]` **Deterministic Bin-Packing Task Scheduler:** Implement the 5-predicate worker eligibility filter and pluggable scheduling strategies (`LEAST_LOADED` default, `BIN_PACK`, `ROUND_ROBIN`, `LOCALITY_FIRST`) with JPA `@Version` optimistic locking retry logic. (Issue #206)
- [ ] `[P1]` **REST Task & Bytecode Gateway (`/api/v1/tasks`, `/api/v1/bytecode/{sha256}`):** Implement task submission (`202 Accepted`), status/metrics querying, two-stage task abort forwarding (`POST /tasks/{id}/abort`), and raw `application/octet-stream` bytecode downloading with `ETag` / `304 Not Modified` support. (Issue #207)
  - [x] Pluggable Bytecode Storage SPI (`BytecodeStorageService`, `LocalFilesystemBytecodeStorage`) with SHA-256 validation, path traversal defense, and 100% test coverage.
  - [ ] REST Controller for Bytecode ingestion & download with ETag caching.
- [ ] `[P1]` **LSTCP Studio Stream Engine (`/ws/studio`):** Implement `STUDIO_SESSION_INIT` (with `viewport_filter` scoping), `STUDIO_SESSION_ACK`, 1000ms `EVENT_CLUSTER_METRIC_TICK` broadcasts, `EVENT_ENROLLMENT_REQUESTED` quarantine push alerts, and scoped `SUBSCRIBE_TASK_LOGS` / `EVENT_TASK_LOG_CHUNK` multiplexing. (Issue #208)
- [ ] `[P1]` **Liquibase Schema & Optimistic Locking Migrations:** Author deterministic Liquibase changelogs for all 6 core tables (`accounts`, `workers`, `functions`, `tasks`, `secrets_vault`, `audit_logs`) with WORM triggers and indexes on `tasks(status, priority)` and `workers(state)`. (Issue #209)

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [ ] `[P2]` **Built-In Cron & Webhook Ingestion Gateways:** Implement configurable cron trigger scheduling (`legatus.features.cron-scheduler`) and third-party webhook ingestion (`/api/v1/webhooks/{function_name}`). (Issue #210)
- [ ] `[P2]` **Task History Retention Sweeper:** Implement background auto-purge job (`legatus.retention.auto-purge`) deleting completed task records older than 168 hours (7 days). (Issue #211)

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [ ] `[P3]` **Optional Prometheus Actuator Exporter:** Configure optional Micrometer `/actuator/prometheus` endpoint (`management.endpoints.prometheus=false` by default) for external scraping. (Issue #212)

---

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | N/A (Java 21 subsystem) | — | [x] N/A |
| **JUnit 5 Suite** | 100% pass across all unit & Spring Boot integration tests (positive + negative per `GL-0009`) | `mvn clean verify -Pprod` | [ ] Pending |
| **Static Analysis Gate** | Zero high-confidence defects in SpotBugs and zero Checkstyle violations (`GL-0011`) | `mvn checkstyle:check spotbugs:check` | [ ] Pending |
| **Documentation** | `server-spec.md`, `api-endpoint.md`, and `02-server.md` 100% synchronized with Jackson DTOs (`GL-0010`) | OpenAPI / DTO schema diff check | [ ] Pending |
| **ADR Governance** | `ADR-0001`, `ADR-0005`, and `ADR-0006` compliance verified in code review | Maintainer audit (`GL-0006`) | [ ] Pending |

---

## 5. Risk Assessment & Mitigation Strategies

* **Risk 1: Off-Heap Memory Exhaustion During Concurrent Argon2id Verifications**
  * *Mitigation:* Pin all Argon2id operations ($64\text{ MB}$ per hash) to a dedicated 4-thread executor (`Executors.newFixedThreadPool(4)`) with a bounded work queue, capping peak Argon2id RAM at $256\text{ MB}$ regardless of virtual thread concurrency (`server-spec.md` Section 4.1).
* **Risk 2: H2 Database Lock Contention Under High-Frequency Telemetry Writes**
  * *Mitigation:* Keep live 5000ms heartbeat CPU/RAM metrics in-memory within the active session registry, writing to the H2 `workers` and `tasks` tables only on state transitions (`ONLINE`, `DRAINING`, `OFFLINE`) and terminal `TASK_RESULT` commits.
