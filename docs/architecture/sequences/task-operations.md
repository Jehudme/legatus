# Task Execution, Metering & Abort Sequences

## Document Metadata
* **Scope:** End-to-End Task Scheduling, Stdin Streaming, Sandboxing & Settlement (`docs/architecture/sequences/task-operations.md`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Distributed Systems Architects, Runtime Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/worker-spec.md`](../../specs/worker-spec.md)
  * [`docs/specs/server-spec.md`](../../specs/server-spec.md)
  * [`docs/specs/protocols/worker-wire.md`](../../specs/protocols/worker-wire.md)
  * [`docs/specs/protocols/api-endpoint.md`](../../specs/protocols/api-endpoint.md)
  * [`docs/dev/decisions/0004-adopt-solix-for-worker-runner.md`](../../dev/decisions/0004-adopt-solix-for-worker-runner.md)
  * [`docs/dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md`](../../dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md)

---

## 1. Executive Summary & Purpose

The execution of guest serverless workloads represents the core compute mandate of the Legatus platform. Tasks transition through a distributed lifecycle encompassing HTTP client submission, server capacity bin-packing, Layer 7 WebSocket dispatch, anonymous `stdin` stream injection (bypassing Windows command-line character ceilings), deterministic instruction metering inside the upstream **Solix Virtual Machine**, ledger-safe two-phase result acknowledgment (`TASK_RESULT` $\to$ `TASK_RESULT_ACK`), and two-stage cooperative kill escalation.

This document formally specifies the end-to-end task operational flows, sequence interactions, and failure-containment boundaries across `server`, `legatusd`, and `legatus-runner`.

---

## 2. Task Submission, Capacity Scheduling & WebSocket Dispatch

```mermaid
sequenceDiagram
    autonumber
    participant Client as REST Client / Webhook
    participant REST as REST Gateway (/api/v1/tasks)
    participant DB as Embedded H2 Database
    participant Sched as Task Scheduler Engine
    participant Broker as LCTP WebSocket Broker
    participant W as Worker Daemon (legatusd)

    Client->>REST: POST /api/v1/tasks (function_id, input_json, constraints)
    REST->>REST: Validate Constraints (1000 <= instruction_budget <= 1000000000)
    REST->>DB: INSERT INTO tasks (task_id, status = 'QUEUED', priority, attempt_count = 0)
    REST-->>Client: HTTP 202 Accepted {"task_id": "tsk_01HXYZ...", "status": "QUEUED"}
    REST->>Sched: Signal Task Available (Virtual Thread Wakeup)
    Sched->>DB: SELECT * FROM workers WHERE state = 'ONLINE' AND draining = false
    Sched->>Sched: Evaluate Bin-Packing Predicates (C_avail >= 1, M_avail >= M_req, I_max >= I_budget)
    Sched->>Sched: Select Candidate Worker w via LEAST_LOADED Algorithm
    Sched->>DB: UPDATE tasks SET status = 'DISPATCHED', assigned_worker_id = :w.id
    Sched->>DB: SELECT cipher_text, gcm_iv FROM secrets_vault WHERE scope = :task_id
    Sched->>Sched: Decrypt Secrets in RAM via AES-256-GCM Engine
    Sched->>Broker: Send DISPATCH_TASK Frame (task_id, function_id, bytecode, input_json, secrets)
    Broker->>W: Transmit LCTP DISPATCH_TASK Frame over WSS
    Sched->>Sched: Zero decrypted secret plaintext in memory (explicit_bzero)
    W-->>Broker: WebSocket TCP ACK
```

---

## 3. Worker Sandbox Spawning & Stdin Envelope Streaming

To guarantee cross-platform reliability on Windows (which caps command-line strings at 32,767 characters), `legatusd` never passes execution envelopes via CLI arguments. Envelopes are streamed into the child runner via an anonymous `stdin` pipe:

```mermaid
sequenceDiagram
    autonumber
    participant Broker as Server LCTP Broker
    participant W as Worker Supervisor (legatusd)
    participant Disk as Local Bytecode Cache
    participant HTTP as Server Bytecode API (/api/v1/bytecode)
    participant Reproc as reproc++ Supervisor
    participant R as Guest Runner (legatus-runner)
    participant Solix as Solix VM Engine

    Broker->>W: LCTP DISPATCH_TASK (task_id, bytecode_sha256, bytecode_url, input_json)
    W->>Disk: Check cache for /var/cache/legatus/bytecode/{sha256}.solix
    alt Bytecode Cache Miss
        W->>HTTP: GET /api/v1/bytecode/{sha256} (Binary Stream)
        HTTP-->>W: HTTP 200 OK (application/octet-stream)
        W->>W: Verify SHA-256 Digest against dispatch declaration
        W->>Disk: Atomically write bytecode file (chmod 0400)
    end
    W->>Reproc: reproc_run_async(["legatus-runner", "--task-id", task_id])
    Note over Reproc: Windows: Flag CREATE_NEW_PROCESS_GROUP active<br/>POSIX: Anonymous pipes created for stdin/stdout/stderr
    Reproc->>R: OS Process Executed (Separate Address Space)
    W->>R: Stream JSON Execution Envelope into child STDIN pipe
    Note over W,R: Envelope contains entry_point, input_json, secrets, memory_limit, budget
    W->>R: Close STDIN Pipe (Send EOF)
    R->>R: Read STDIN until EOF -> Parse JSON into memory
    R->>Solix: Initialize VM Context Arena & Load Bytecode Binary
    R->>Solix: Invoke Entry Point Function with input arguments
```

---

## 4. Execution, Metering, Result Commit & Ledger Acknowledgment

The worker and server enforce a two-phase ledger commit protocol to ensure task completion metrics are safely persisted before local runner artifacts are purged:

```mermaid
sequenceDiagram
    autonumber
    participant Solix as Solix VM Engine
    participant R as Guest Runner (legatus-runner)
    participant W as Worker Supervisor (legatusd)
    participant Broker as Server LCTP Broker
    participant DB as Embedded H2 Database
    participant Studio as Studio Stream Engine

    loop Guest Instruction Loop
        Solix->>Solix: Execute Bytecode Instruction -> Increment Instruction Counter
        alt Instruction Counter > instruction_budget
            Solix->>R: Trap: ERR_INSTRUCTION_BUDGET_EXCEEDED -> Trigger Exit Code 70
        end
        opt Guest prints logs to stdout/stderr
            R->>W: Flush stdout/stderr chunk over anonymous pipe
            W->>Broker: Transmit LCTP TASK_LOG_CHUNK Frame
            Broker->>Studio: Broadcast EVENT_TASK_LOG_CHUNK
        end
    end
    Solix-->>R: Execution Success (Exit 0) -> Write output JSON to stdout
    R->>R: Reclaim ARC references -> Zero memory secrets -> Process Exit (0)
    W->>W: reproc::drain() pipes -> Harvest exit code, wall_clock_ms, peak_rss
    W->>Broker: Transmit LCTP TASK_RESULT Frame (task_id, exit_code: 0, instructions_consumed, output_json)
    Broker->>DB: BEGIN TRANSACTION
    Broker->>DB: UPDATE tasks SET status = 'COMPLETED', exit_code = 0, instructions_consumed = :inst, output_json = :out, completed_at = now
    Broker->>DB: UPDATE workers SET allocatable_cores = allocatable_cores + 1 WHERE worker_id = :w.id
    Broker->>DB: COMMIT TRANSACTION
    Broker->>Studio: Broadcast EVENT_TASK_TERMINATED (task_id, status: COMPLETED)
    Broker->>W: Transmit LCTP TASK_RESULT_ACK Frame (task_id)
    W->>W: Safely purge runner scratch directory and close process descriptors
```

---

## 5. Two-Stage Abort & Cooperative Termination Escalation

When an operator clicks **[Abort Task]** in `legatus-studio` or issues `legatus tasks kill`, the platform executes a deterministic two-stage kill escalation protocol:

```mermaid
sequenceDiagram
    autonumber
    participant Op as Human Operator / API
    participant REST as REST Gateway (/api/v1/tasks/{id}/abort)
    participant Broker as Server LCTP Broker
    participant W as Worker Supervisor (legatusd)
    participant Reproc as reproc++ Manager
    participant R as Guest Runner (legatus-runner)
    participant Solix as Solix VM Engine

    Op->>REST: POST /api/v1/tasks/{task_id}/abort (reason: "Operator Abort")
    REST->>Broker: Route Abort Signal to assigned worker session
    Broker->>W: Transmit LCTP ABORT_TASK Frame (task_id, grace_period_ms: 10000)
    W->>Reproc: reproc_stop(REPROC_TERMINATE)
    Note over Reproc,R: POSIX: Delivers SIGTERM<br/>Windows: Generates CTRL_BREAK_EVENT (CREATE_NEW_PROCESS_GROUP)
    W->>W: Arm 10,000ms Grace Period Countdown Timer
    alt Case A: Runner Exits Cleanly within Grace Period (< 10s)
        R->>Solix: Interrupt execution loop
        Solix->>Solix: Unwind call stack & release ARC memory references
        R->>R: Scrub secrets in RAM (explicit_bzero) -> Exit with Code 130 (ABORTED)
        W->>W: Disarm Grace Timer -> Harvest exit code 130
        W->>Broker: Transmit LCTP TASK_RESULT (task_id, exit_code: 130, error: "ERR_TASK_ABORTED")
    else Case B: Runner Deadlocks or Ignores Signal (Timer Expires)
        W->>W: 10,000ms Grace Period Expires!
        W->>Reproc: reproc_stop(REPROC_KILL)
        Note over Reproc,R: POSIX: Delivers SIGKILL<br/>Windows: Calls TerminateProcess()
        R-->>W: OS Kernel forcibly destroys child process (Exit Code 137 / 0x80000003)
        W->>Broker: Transmit LCTP TASK_RESULT (task_id, exit_code: 137, error: "ERR_TASK_KILLED_FORCIBLY")
    end
    Broker->>Broker: Transmit TASK_RESULT_ACK to Worker
```

---

## 6. Verification & Compliance Checklist

- [ ] **Zero Implementation Code:** Contains zero raw C++ or Java implementation blocks (`GL-0005`).
- [ ] **Stdin Envelope Invariant:** Documents streaming execution envelopes via anonymous `stdin` pipes, eliminating Windows 32KB CLI limits.
- [ ] **Two-Phase Ledger Safety:** Details the mandatory `TASK_RESULT` $\to$ `TASK_RESULT_ACK` round-trip confirmation before worker artifact cleanup.
- [ ] **Escalation Rigor:** Specifies the 10,000ms grace period countdown transitioning from `terminate` (`130`) to unconditional `kill` (`137`).
- [ ] **Automated Test Coverage:** Verified via `test_runner_stdin_injection.cpp`, `test_runner_two_stage_kill.cpp`, and `TaskSchedulerIT.java`.
