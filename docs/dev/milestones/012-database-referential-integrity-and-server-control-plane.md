# Milestone 012: Database Referential Integrity, Server Control Plane & Persistence Hardening

## Document Metadata
* **Milestone ID:** MS-012
* **Target Version:** `v1.8.0-server-integrity`
* **Status:** CLOSED
* **Lead Architect:** @runtime-architect & @server-lead
* **Target Delivery Date:** 2027-05-01
* **Related ADRs:** ADR-0001, ADR-0002, ADR-0005, ADR-0007, ADR-0010

---

## 1. Executive Summary & Objectives

The comprehensive system audit surfaced critical referential integrity failures, scheduling math errors, and persistence vulnerabilities within `legatus-server`:
1. **Foreign Key Deletion Crash:** When an operator attempts to delete a worker that has historical tasks associated with it, Hibernate and the database engine throw an unhandled `DataIntegrityViolationException` (HTTP 500) due to missing `ON DELETE SET NULL` constraints in Liquibase.
2. **Worker Revocation Socket Leak:** Revoking a worker lease mutates the database state to `REVOKED` but leaves the active WebSocket connection open in `WorkerSessionRegistry`, allowing revoked workers to continue sending heartbeats.
3. **Threshold Calculation & Memory Hysteresis Inversion:** Worker session registration fails to set `totalMemoryBytes` in the database, causing heartbeat telemetry to fall back to 1 GB. Used host memory (e.g. 10.5 GB) is divided by 1 GB = 1050% load, trapping the worker in permanent breach state.
4. **Core Predicate Conflation in Eligibility Filter:** `WorkerEligibilityFilter` evaluates `allocatableCores - activeTaskCount < 1` instead of honoring `worker.getMaxConcurrentTasks()` (16), capping 16-task workers at 4 tasks on 4-core hardware.
5. **Dynamic Cron Job Persistence Vulnerability:** Dynamic cron triggers registered at runtime exist only in a `ConcurrentHashMap` with no JPA entity or Liquibase table, causing all operator-configured schedules to be permanently wiped upon server restart.
6. **Retention Sweeper Null CompletedAt Leak:** `ComputeTaskRepository.deleteCompletedTasksOlderThan` uses `WHERE t.completedAt < :cutoff`, which silently ignores tasks aborted or failed before `completedAt` was set (`NULL < :cutoff` is unknown), permanently leaking database rows.
7. **Lifecycle Frame Timestamp Omission:** `EventTaskLifecycleFrame` omits `started_at` and `created_at`, forcing Studio to guess task execution durations.

This milestone resolves all server-side architectural defects while completely refactoring the server test suite to eliminate the "Mockist Fallacy" and verify real database cascades and high-concurrency scheduling.

---

## 2. Scope & Subsystems Affected

* **`server/` (`legatus-server` - Java 21 LTS / Spring Boot 4 / Netty / JPA / Liquibase):**
  * `config/liquibase/changelog/`: Add migration adding `ON DELETE SET NULL` to `fk_tasks__assigned_worker_id` and `fk_tasks__function_id`, composite indexes on hot columns (`tasks.status`, `assigned_worker_id`, `created_at`), and a new `legatus_cron_triggers` table.
  * `domain/` & `repository/`: Create `CronJobTrigger` JPA entity and repository; fix `ComputeTaskRepository.deleteCompletedTasksOlderThan` SQL query to check `(t.completedAt < :cutoff OR (t.completedAt IS NULL AND t.createdAt < :cutoff))`.
  * `service/WorkerEnrollmentService.java`: Gracefully unlink historical tasks prior to worker deletion, and explicitly terminate active WebSocket sessions in `WorkerSessionRegistry` upon revocation.
  * `service/scheduler/WorkerEligibilityFilter.java`: Evaluate capacity against `worker.getMaxConcurrentTasks()`, using allocatable cores only when concurrency is unspecified.
  * `service/scheduler/CronSchedulerService.java`: Persist dynamic cron triggers to the database; reload active jobs on startup.
  * `web/websocket/lctp/LctpWorkerWebSocketHandler.java`: Populate `totalMemoryBytes` from `maxMemoryBytes` or host metrics; fix RAM percentage calculation; handle terminal status transitions safely.
  * `service/dto/lstcp/EventTaskLifecycleFrame.java`: Add `started_at` and `created_at` timestamp fields.
  * `security/BytecodeSecurityValidator.java` & `TaskGatewayService.java`: Sanitize JSON string interpolation and enforce bounded capacity on `TicketBrokerService`.

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Liquibase Foreign Key Cascade & Worker Deletion Fix:** Update Liquibase migrations with `ON DELETE SET NULL` on `fk_tasks__assigned_worker_id` and `fk_tasks__function_id`. Update `WorkerEnrollmentService.deleteWorker()` to unlink task references within a transaction so deletion succeeds cleanly.
- [x] `[P0]` **Worker Revocation Socket Severance:** Update `WorkerEnrollmentService.revokeWorker()` to look up the active session in `WorkerSessionRegistry` and close the WebSocket with code `1008` (Policy Violation) / `4403` (Lease Revoked).
- [x] `[P0]` **Threshold Math & RAM Saturation Normalization:** In `LctpWorkerWebSocketHandler`, store `maxMemoryBytes` as `totalMemoryBytes` if missing, divide host used RAM by actual total RAM, and reset hysteresis flags in `ThresholdScheduler` when RAM drops below target threshold.
- [x] `[P0]` **16-Task Concurrency Quota Enforcement:** Refactor `WorkerEligibilityFilter` Predicate 3 to check `activeTaskCount < worker.getMaxConcurrentTasks()`, enabling workers to execute up to 16 concurrent tasks regardless of physical core count.

### 3.2 P1: High Priority (Milestone Core)
- [x] `[P1]` **Persistent Dynamic Cron Storage:** Implement `CronJobTrigger` entity, Liquibase table `legatus_cron_triggers`, and repository. Update `CronSchedulerService` to save dynamic triggers to the database and reload them on application startup.
- [x] `[P1]` **Retention Sweeper Null CompletedAt Purge:** Fix `ComputeTaskRepository.deleteCompletedTasksOlderThan` to purge all terminal tasks older than the cutoff regardless of whether `completedAt` is null.
- [x] `[P1]` **Lifecycle Frame Timestamp Enrichment:** Add `started_at` and `created_at` (epoch milliseconds) to `EventTaskLifecycleFrame.Payload` and propagate them via `StudioStreamBroadcaster`.
- [x] `[P1]` **Database Indexes on Hot Telemetry Columns:** Add Liquibase indexes on `tasks(status, priority_weight, created_at)`, `tasks(assigned_worker_worker_id)`, and `audit_logs(timestamp)`.

### 3.3 P2: Medium Priority (Ergonomics & Hardening)
- [x] `[P2]` **WORM Audit String Injection Protection:** Replace string concatenation in `TaskGatewayService` audit events with structured Jackson JSON serialization.
- [x] `[P2]` **Ticket Broker Bounded Eviction Buffer:** Bound `TicketBrokerService` token map to 100,000 entries with LRU rejection to prevent memory exhaustion under DDoS token requests.
- [x] `[P2]` **WebSocket Ping/Pong Heartbeat Control Frames:** Send standard WebSocket ping frames every 30 seconds to prevent middlebox timeout disconnects.

---

## 4. Test Strategy Overhaul & Verification Matrix

The previous test suite passed with 100% green builds because it actively asserted the buggy behaviors or tested against mocked, empty databases. This milestone establishes a non-mocked integration test standard for the server:

### 4.1 Test Refactoring & New Test Deliverables

| Component Under Test | Flawed Old Test Practice | Refactored / New Test Suite | Verification Scenario |
| :--- | :--- | :--- | :--- |
| **Worker Deletion** | Mock worker with 0 assigned tasks; concealed SQL FK crash | `DatabaseReferentialIntegrityIT.java` | Creates worker with 10 completed, 2 failed, and 1 aborted tasks. Deletes worker. Asserts worker is deleted and all 13 tasks retain history with `assignedWorker = null`. |
| **Worker Eligibility Filter** | `WorkerEligibilityFilterTest.java` asserted that 4 active tasks rejects 5th task | `WorkerEligibilityFilterTest.java` (Refactored) | Asserts that a worker with 4 cores and `maxConcurrentTasks = 16` accepts tasks 1 through 16, and rejects task 17. |
| **RAM Saturation & Threshold** | Synthetic frame with 0 memory; defaulted to 1GB | `ThresholdSchedulerIT.java` | Simulates 16 GB worker sending 10.5 GB RAM used. Asserts calculated load is ~65.6%, and worker is NOT flagged as breached. |
| **Worker Revocation** | Database status updated; socket left open unverified | `WorkerRevocationSessionIT.java` | Establishes real WebSocket session. Calls `revokeWorker()`. Asserts WebSocket session receives close code and is severed within 200ms. |
| **Dynamic Cron Persistence** | Tested in-memory map register/unregister only | `CronSchedulerPersistenceIT.java` | Registers dynamic cron job via REST. Simulates server restart by reinitializing context. Asserts cron job is reloaded from DB and triggers on schedule. |
| **Retention Sweeper** | Only tested tasks with non-null `completedAt` | `TaskHistoryRetentionSweeperIT.java` | Seeds database with terminal tasks where `completedAt` is NULL. Runs sweeper. Asserts tasks older than cutoff are successfully purged. |

---

## 5. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **JUnit 5 Suite** | 100% pass across all Spring Boot unit & integration tests | `./mvnw clean test verify` | [x] Completed |
| **Database Cascades** | Clean worker/function deletion with historical tasks | `DatabaseReferentialIntegrityIT` | [x] Completed |
| **Zero Regressions** | All 15 existing Spring Boot ITs remain green | `./mvnw test -Dtest=*IT` | [x] Completed |
| **Living Specs Updated** | `server-spec.md` updated with cron persistence & cascade rules | Documentation audit | [x] Completed |

---

## 6. Risk Assessment & Mitigation Strategies

* **Risk 1: Database Migration Lock on Large Production Tables:** Adding `ON DELETE SET NULL` foreign key constraints on existing task tables can lock rows during migration.
  * *Mitigation:* Use Liquibase pre-conditions and non-blocking index creation; test migration against SQLite/H2 and PostgreSQL testcontainers.
* **Risk 2: Optimistic Locking Collisions under Burst Dispatch:** High-frequency status updates across 16 concurrent tasks could trigger `OptimisticLockException`.
  * *Mitigation:* Implement exponential backoff jitter in `TaskSchedulerService` retry loops instead of linear thread sleeps.
