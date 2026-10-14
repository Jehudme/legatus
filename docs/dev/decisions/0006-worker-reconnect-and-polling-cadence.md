# ADR-0006: Exponential Jittered Backoff, Heartbeat Dead-Man's Switch & Session Reconciliation

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @worker-lead
* **Date:** 2026-10-14
* **Technical Story:** Issue #110 / PR #113
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

In a distributed cluster where hundreds or thousands of worker daemons (`legatusd`) maintain persistent outbound TLS 1.3 WebSocket connections (`LCTP`) to a centralized orchestration broker (`legatus-server`), two critical distributed systems failure modes arise:

1. **The Reconnect Thundering Herd:** When `legatus-server` restarts for a rolling upgrade or recovers from a transient network partition, all disconnected workers attempt to re-authenticate (`POST /api/v1/auth/worker`) and upgrade their WebSockets simultaneously. If workers use fixed retry intervals or un-jittered timers, synchronized connection waves saturate the server's TLS handshake queues and Argon2id verification pool.
2. **Half-Open Connections & Split-Brain Task Execution:** TCP sockets over NAT gateways or cloud firewalls frequently suffer silent "blackhole" drops without delivering a TCP `FIN` or `RST`. Without an application-layer dead-man's switch, the server continues dispatching tasks to dead sockets, or conversely, re-queues a task that a transiently disconnected worker is still actively executing.

We must define deterministic timing equations, jitter invariants, dead-man's switch thresholds, and in-flight task reconciliation rules across LCTP.

---

## 2. Decision Drivers (Forces)

1. **Thundering Herd Elimination:** All periodic client transmissions (heartbeats, quarantine status polls, and reconnect loops) must desynchronize automatically via uniform random jitter (`GL-0007`).
2. **Fast Failure Detection (15-Second Ceiling):** Silent node crashes or severed network links must be detected by both client and broker within $15\text{ seconds}$ so orphaned tasks can be re-queued promptly.
3. **Zero Duplicate Execution on Transient Blips:** If a worker experiences a brief 3-second network blip and reconnects before the 15-second dead-man's switch expires, running child tasks (`legatus-runner`) must be seamlessly re-attached to the new session without being killed or duplicated.
4. **Immediate Maintenance Withdrawal:** When an operator runs `legatus drain`, the worker must withdraw from the scheduling pool immediately without waiting for the next heartbeat tick.

---

## 3. Considered Options

* **Option 1:** Application-Layer Jittered Heartbeats ($5000\text{ ms} \pm 500\text{ ms}$) + $15000\text{ ms}$ Bidirectional Dead-Man's Switch + Exponential Full-Jitter Reconnect Backoff + `SESSION_INIT` Task Reconciliation.
* **Option 2:** Rely exclusively on OS-level TCP Keepalive probes and static 5-second reconnect sleep loops.
* **Option 3:** Stateless HTTP Long-Polling for task dispatch and health reporting instead of persistent WebSockets.

---

## 4. Evaluation of Considered Options

### Option 1: Jittered LCTP Heartbeats + 15s Dead-Man's Switch + Exponential Backoff & Reconciliation
* **Description:** Workers emit `HEARTBEAT` frames every $5000\text{ ms} \pm 500\text{ ms}$ (uniform random jitter) containing live host CPU/RAM metrics and active task progress; the server replies with `HEARTBEAT_ACK` within $2000\text{ ms}$. Both endpoints enforce a $15000\text{ ms}$ ($3 \times t_{\text{interval}}$) dead-man's window. Disconnected workers reconnect using exponential backoff with full jitter:
  $$t_{\text{backoff}} = \min\left(30, \; 2^{\text{attempt}}\right) + \text{UniformRandom}(0, 1) \quad [\text{seconds}]$$
  Upon reconnecting within the 15s window, `SESSION_INIT` includes `previous_session_id` and `reconcile_tasks`, allowing the broker to re-link active tasks.
* **Good:**
  * Uniform random jitter spreads heartbeat and reconnect traffic evenly across time, completely preventing synchronized thundering herds after broker restarts.
  * Piggybacking host CPU/RAM load and per-task instruction counters onto the 5s `HEARTBEAT` frame keeps the server's bin-packing scheduler continuously updated without extra RPCs.
  * Session reconciliation prevents transient network blips from aborting long-running Solix tasks.
* **Bad:**
  * Requires maintaining timer state machines and reconciliation logic in both `legatusd` and `legatus-server`.

### Option 2: OS TCP Keepalive + Fixed 5-Second Reconnect Interval
* **Description:** Rely on kernel TCP keepalive packets for liveness and `std::this_thread::sleep_for(5s)` on disconnect.
* **Good:**
  * Zero application-layer framing code.
* **Bad:**
  * OS TCP keepalives default to 2 hours on Linux and do not traverse L7 reverse proxies or detect hung application threads.
  * Fixed 5-second reconnect intervals synchronize all workers into destructive retry waves during an outage (`GL-0007` Anti-Pattern 3).

### Option 3: Stateless HTTP Short/Long Polling
* **Description:** Workers poll `GET /api/v1/tasks/poll` every few seconds over HTTPS.
* **Good:**
  * Stateless on the server.
* **Bad:**
  * High TLS/HTTP header overhead and poor dispatch latency compared to full-duplex persistent WebSockets.
  * Prevents sub-millisecond push delivery of `DISPATCH_TASK` and `ABORT_TASK` signals.

---

## 5. Decision Outcome

Chosen Option: **Option 1: Jittered LCTP Heartbeats ($5000\text{ ms} \pm 500\text{ ms}$) + $15000\text{ ms}$ Dead-Man's Switch + Exponential Jittered Backoff + Session Reconciliation**, because it satisfies `GL-0007` protocol resilience invariants, guarantees bounded 15-second fault detection, and protects in-flight workloads during transient network drops.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Broker restarts with 1,000+ workers recover smoothly as reconnect attempts disperse across the exponential jitter window.
* Dead or partitioned workers are evicted deterministically after $15000\text{ ms}$, and their idempotent tasks are automatically re-queued to healthy nodes.
* Expedited `DRAIN_NOTICE` frames (`worker-wire.md` Section 4.9) bypass the 5000ms timer so draining nodes stop receiving tasks immediately.

### 6.2 Negative Consequences (Trade-offs)
* **Background Bandwidth Overhead:** Every idle worker exchanges a small JSON `HEARTBEAT` / `HEARTBEAT_ACK` pair (~250 bytes) every ~5 seconds ($\approx 100\text{ bytes/sec}$ per node).
* **Clock Monotonicity Requirement:** Timeout calculations must use monotonic clocks (`std::chrono::steady_clock` in C++20, `System.nanoTime()` in Java 21) rather than wall-clock time to avoid false timeouts during NTP adjustments (`GL-0011`).

### 6.3 Neutral Consequences
* Quarantine enrollment polling (`GET /api/v1/workers/register/{id}/status`) uses a $3000\text{ ms} \pm 500\text{ ms}$ jittered cadence.

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Governed by `docs/specs/protocols/worker-wire.md` (Sections 2, 4.1, 4.3, and 5) and `docs/dev/guidelines/authoring-protocols.md` (`GL-0007`).
* **Test Verification:**
  * Catch2 unit test `worker/tests/unit/test_heartbeat_jitter.cpp` verifying statistical variance of intervals.
  * Catch2 integration test `worker/tests/integration/test_heartbeat_deadman.cpp` using `ix::WebSocketServer` to simulate dropped ACKs and verify teardown after 3 missed cycles (`GL-0009`).