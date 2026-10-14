# Worker Daemon Lifecycle & LCTP Connection Sequences

## Document Metadata
* **Scope:** Worker Daemon Bootstrapping, LCTP Handshake, Heartbeat & Fault Recovery (`docs/architecture/sequences/worker-operations.md`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Worker Daemon Implementers, Distributed Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/worker-spec.md`](../../specs/worker-spec.md)
  * [`docs/specs/protocols/worker-wire.md`](../../specs/protocols/worker-wire.md)
  * [`docs/specs/server-spec.md`](../../specs/server-spec.md)
  * [`docs/dev/decisions/0005-node-enrollment-and-attestation.md`](../../dev/decisions/0005-node-enrollment-and-attestation.md)
  * [`docs/dev/decisions/0006-worker-reconnect-and-polling-cadence.md`](../../dev/decisions/0006-worker-reconnect-and-polling-cadence.md)
  * [`docs/dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md`](../../dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md)

---

## 1. Executive Summary & Purpose

The **Legatus Worker Supervisor Daemon** (`legatusd`) manages local compute capacity, supervises sandboxed child task processes (`legatus-runner`), and maintains a persistent, full-duplex WebSocket connection (`LCTP`) to the centralized cluster broker (`legatus-server`).

Operating compute nodes across heterogeneous, untrusted network environments demands robust fault recovery invariants:
1. **Zero-Configuration Bootstrapping:** Initial node onboarding requires zero pre-shared static credentials.
2. **Session Reconciliation:** Transient network partitions must not trigger split-brain task duplication or terminate long-running guest jobs.
3. **Thundering Herd Mitigation:** All periodic transmissions (quarantine polling, heartbeats, and reconnect loops) must incorporate uniform randomized jitter ($t \pm \Delta$).
4. **Graceful Node Evacuation:** When hosts require scheduled kernel patches or reboots, nodes must withdraw from scheduling immediately via expedited drain notifications.

This document formally specifies the autonomous operational sequences, handshakes, and failure-recovery protocols executed by `legatusd`.

---

## 2. First Boot, Quarantine Registration & Polling Loop

When `legatusd` launches on a newly provisioned host with an unconfigured `worker.toml`, it automatically generates a local cryptographic identity and submits a registration request:

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker Daemon (legatusd)
    participant Disk as Local Storage (worker.toml)
    participant Console as Host Operator Console
    participant REST as Server REST Gateway (/api/v1/workers)
    participant DB as Server Embedded H2 Database

    W->>Disk: Inspect worker.toml
    Note over W,Disk: Static secret_token is absent -> Enter ENROLLING State
    W->>W: Generate 256-bit Ed25519 Keypair via OpenSSL 3.0
    W->>W: Compute SHA-256 Digest of Public Key -> Format Hex Fingerprint
    W->>W: Derive Deterministic 3-Word Visual Code (Tiger-Galaxy-77)
    W->>Console: Print: "Node Quarantined. Fingerprint: 4E:8A:BC:31:09:FF:7D:12 Code: Tiger-Galaxy-77"
    W->>REST: POST /api/v1/workers/register (worker_id, ed25519_pubkey, hardware_profile)
    REST->>DB: INSERT INTO workers (state = 'PENDING_APPROVAL')
    REST-->>W: HTTP 202 Accepted {"status": "PENDING_APPROVAL", "poll_interval_ms": 5000}
    loop Quarantine Status Poll Loop (Cadence: 5000ms ± 500ms Jitter)
        W->>W: Sleep: t_poll = 5000ms + UniformRandom(-500, +500)ms
        W->>REST: GET /api/v1/workers/register/status?worker_id=compute-042
        alt Node Still Pending
            REST-->>W: HTTP 202 Accepted {"status": "PENDING_APPROVAL"}
        else Operator Approved Node
            REST-->>W: HTTP 200 OK {"status": "APPROVED", "secret_token": "sec_tok_01H..."}
            W->>Disk: Atomically write secret_token & keypair to worker.toml (chmod 0600)
            W->>Console: Print: "Node attestation successful. Entering OPERATIONAL state."
            Note over W: Break loop -> Proceed to LCTP Connection Handshake
        end
    end
```

---

## 3. LCTP Connection Handshake & In-Flight Session Reconciliation

When establishing a WebSocket connection to `legatus-server`, `legatusd` submits active local runner processes in `SESSION_INIT` to seamlessly reconcile state after network drops:

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker Daemon (legatusd)
    participant REST as Server REST Gateway (/api/v1/auth)
    participant WS as Server Worker Broker (/ws/worker)
    participant DB as Server Embedded H2 Database
    participant R as Local Guest Runners (reproc++)

    W->>REST: POST /api/v1/auth/worker (worker_id, secret_token)
    REST-->>W: HTTP 200 OK {"ticket": "ws_tkt_01HXYZ...", "ttl_seconds": 30}
    W->>WS: GET /ws/worker?ticket=ws_tkt_01HXYZ... (Upgrade: websocket)
    WS-->>W: HTTP 101 Switching Protocols
    W->>R: Inspect active child processes in local process table
    Note over W: Discovers active task "tsk-101" (PID 4821) running locally
    W->>WS: Transmit LCTP SESSION_INIT Frame
    Note over W,WS: Frame includes:<br/>previous_session_id: "ses-old-77",<br/>reconcile_tasks: [{task_id: "tsk-101", runner_pid: 4821, wall_time_ms: 1240}]
    WS->>DB: Check task records for "tsk-101"
    alt Task "tsk-101" Still in EXECUTING State on Server
        WS->>DB: Re-link task "tsk-101" to new WebSocket session
        WS-->>W: Transmit LCTP SESSION_ACK Frame
        Note over WS,W: reconciled_task_ids: ["tsk-101"],<br/>discarded_task_ids: [],<br/>heartbeat_interval_ms: 5000
        W->>W: Resume supervising task "tsk-101" without interruption
    else Task "tsk-101" Was Already Re-Dispatched to Another Node
        WS-->>W: Transmit LCTP SESSION_ACK Frame
        Note over WS,W: reconciled_task_ids: [],<br/>discarded_task_ids: ["tsk-101"]
        W->>R: Forcibly abort redundant local runner for "tsk-101" (reproc_stop)
    end
```

---

## 4. Heartbeat Dead-Man's Switch & Randomized Jitter Cadence

Compute nodes continuously transmit liveness telemetry to reset the server's 15-second dead-man's switch:

```mermaid
sequenceDiagram
    autonumber
    participant W as Worker Daemon (legatusd)
    participant Timer as Uniform Random Jitter Clock
    participant Broker as Server Worker Broker (/ws/worker)
    participant DB as Server Embedded H2 Database

    loop Periodic Liveness Loop
        Timer->>W: Wakeup: t_interval = 5000ms + UniformRandom(-500, +500)ms
        W->>W: Sample Host Telemetry (CPU Utilization %, Free RAM bytes, Active Task Count)
        W->>Broker: Transmit LCTP HEARTBEAT Frame (worker_id, cpu_pct: 42.1, free_ram_bytes: 8589934592)
        Broker->>DB: UPDATE workers SET last_seen_at = now WHERE worker_id = :w.id
        Note over Broker: Resets 15000ms Dead-Man Timer for Node
        Broker-->>W: LCTP TCP ACK
    end
```

---

## 5. Graceful Node Evacuation & Expedited Drain Notice

When an administrator marks a node for maintenance or triggers `legatus drain`, `legatusd` immediately unshelves itself from the server's scheduling candidate pool:

```mermaid
sequenceDiagram
    autonumber
    participant Op as Human Operator
    participant CLI as legatus CLI
    participant W as Worker Daemon (legatusd)
    participant Broker as Server Worker Broker (/ws/worker)
    participant DB as Server Embedded H2 Database
    participant R as Active Child Runner (tsk-999)

    Op->>CLI: Execute: legatus drain
    CLI->>W: IPC Command: DAEMON_DRAIN
    W->>W: Set Invariant: node_draining = true (Reject local task dispatches)
    W->>Broker: Transmit LCTP DRAIN_NOTICE Frame (expedited notification)
    Broker->>DB: UPDATE workers SET draining = true WHERE worker_id = :w.id
    Note over Broker: Server immediately unshelves worker from scheduling ring
    loop Await In-Flight Tasks Completion
        R->>W: Child runner finishes task "tsk-999" (Exit Code 0)
        W->>Broker: Transmit LCTP TASK_RESULT (tsk-999)
        Broker-->>W: Transmit LCTP TASK_RESULT_ACK
    end
    Note over W: Active Task Count reaches exactly 0
    W->>Broker: Close WebSocket (RFC 6455 Close Code 1000 - Normal Closure)
    W->>CLI: Return IPC Status: "Drained successfully. Zero active tasks."
    W->>W: Process Clean Exit (0)
```

---

## 6. Verification & Compliance Checklist

- [ ] **Zero Implementation Code:** Contains zero raw C++ implementation code blocks (`GL-0005`).
- [ ] **In-Flight Session Reconciliation:** Formally specifies the `reconcile_tasks` handshake preventing duplicate execution across network drops (`ADR-0006`).
- [ ] **Uniform Randomized Jitter:** Mandates uniform jitter ($5000\text{ ms} \pm 500\text{ ms}$) on polling and heartbeats to eliminate thundering herd storms.
- [ ] **Expedited Drain Notice:** Details immediate scheduling unshelving via `DRAIN_NOTICE` frames.
- [ ] **Automated Test Coverage:** Verified via `test_worker_fsm.cpp`, `test_heartbeat_jitter.cpp`, and `WorkerReconciliationIT.java`.
