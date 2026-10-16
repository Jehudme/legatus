# ADR-0010: Server-Centric Worker Control Plane, Dynamic Configuration & Threshold Auto-Balancing

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @worker-lead, @control-plane-lead
* **Date:** 2026-10-16
* **Technical Story:** Issue #132 / PR #135
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

In the initial architecture of the Legatus platform, worker compute daemons (`legatusd`) relied on local, static TOML configuration files (`/etc/legatus/worker.toml`) to define concurrency ceilings, memory limits, and telemetry intervals. Altering worker operating parameters required manual operator intervention via SSH or local configuration management tools, followed by daemon restarts that disrupted in-flight guest tasks.

Furthermore:
1. **Unbalanced Dispatch & Thread Exhaustion:** Workers were selected purely based on coarse capacity, without server-enforced soft thresholds, allowing worker nodes to become saturated near 100% CPU/RAM before the scheduler redirected traffic.
2. **Inaccurate CPU Telemetry:** Telemetry reported raw CPU tick deltas without multi-core normalization, causing telemetry drift between the worker host and Studio displays.
3. **Decentralized Node Governance:** Operators could not assign human-readable friendly names, dynamically suspend/resume workers (`ACTIVE` vs `INACTIVE`), or govern automatic lease renewals from the central dashboard.
4. **Metric Skew from Inactive Nodes:** Offline or disabled worker nodes were included in cluster aggregates, skewing overall cluster CPU and memory utilization statistics.
5. **Lack of Hysteresis & Overload Indicators:** Saturated clusters lacked clear visual indicators distinguishing between temporary threshold breaches (handling spikes) and fatal queue saturation where requests had to be queued.

A unified, server-centric worker control plane is required to centralize all operational authority onto `legatus-server`, strip the worker CLI down to minimal connection primitives, and enable dynamic threshold scheduling with anti-flapping hysteresis.

---

## 2. Decision Drivers (Forces)

1. **Centralized Authority on Server:** Worker configuration must reside authoritative in the server database; workers should cache only their cryptographic identity (`worker_id.key`) and authentication lease token (`lease.jwt`).
2. **Dynamic Non-Disruptive Updates:** Concurrency limits, log levels, and memory quotas must be applied live via LCTP (`CONFIG_UPDATE_PUSH`) without restarting `legatusd`.
3. **Resilient Startup Loop:** If the server is temporarily unreachable upon worker boot, `legatusd` must not exit fatally; it must retry with exponential backoff and jitter.
4. **Accurate Normalized Telemetry:** Multi-core CPU utilization must be normalized to $[0.0, 100.0]\%$ across all operating systems (Linux, Windows, macOS).
5. **Threshold-Based Scheduling with Hysteresis:** The scheduler must route around saturated workers using Target CPU %, Target RAM %, and Max Concurrent Functions with Exponential Moving Average (EMA, $\alpha=0.3$) and $\pm 5\%$ hysteresis to eliminate flapping.
6. **Explicit Telemetry Warning and Error States:** Surface `THRESHOLD_BREACH_WARNING` when thresholds must be exceeded to meet demand, and `CLUSTER_QUEUE_SATURATED_ERROR` when queueing is unavoidable (queueing strictly as the absolute last resort).
7. **Clean Lifecycle & Metric Isolation:** Inactive/disabled workers must be excluded from global cluster CPU/RAM calculations, and node deletion must follow a graceful 4-step revocation cascade.

---

## 3. Considered Options

### Option 1: Decentralized Static TOML on Nodes with SIGHUP Reloads
* **Good:** Simple file-based worker setup.
* **Bad:** High operational overhead, impossible to manage dynamically from Legatus Studio, prone to configuration drift across fleet nodes.

### Option 2: Server-Centric Worker Control Plane & Dynamic Configuration Sync (Chosen)
* **Good:**
  * **Minimalist Worker CLI:** Worker commands restricted to `legatus up [--server <url>]`, `legatus down`, and `legatus status`.
  * **Zero Local Config Drift:** The server stores and versions all runtime profiles. On connection, the worker pulls `CONFIG_SYNC_RESP` and receives live `CONFIG_UPDATE_PUSH` frames.
  * **Threshold Auto-Balancing:** Operators set Target CPU %, Target RAM %, and Max Functions per node. Saturated nodes are deprioritized with EMA smoothing and hysteresis.
  * **Clear Health Feedback:** Amber warnings appear when thresholds are breached, and red error badges appear when queueing occurs.
  * **Isolated Metrics:** Inactive workers do not distort global cluster health.
* **Bad:** Requires bidirectional LCTP protocol handling for configuration payloads and state synchronization.

---

## 4. Decision Outcome

**Chosen Option:** **Option 2 (Server-Centric Worker Control Plane & Dynamic Configuration Sync).**

### Architectural Specifications:
1. **Worker CLI Footprint:**
   * Stripped down to `legatus up`, `legatus down`, and `legatus status`.
   * Local storage limited to cryptographic node key (`worker_id.key`) and authentication lease (`lease.jwt`).
2. **Dynamic Configuration Protocol (LCTP):**
   * `CONFIG_SYNC_REQ` / `CONFIG_SYNC_RESP`: Worker downloads its configuration profile on connect.
   * `CONFIG_UPDATE_PUSH`: Server pushes configuration updates on the fly without restarts.
   * Resilient boot: If the server is offline, the worker enters an exponential backoff loop ($1\text{s} \to 2\text{s} \to 4\text{s} \dots \text{max } 30\text{s}$).
3. **Normalized Multi-Core Telemetry:**
   * Linux (`/proc/stat`), Windows (`GetSystemTimes`), macOS (`host_processor_info`) compute smoothed percentage:
     $$\text{cpu\_usage\_pct} = \frac{\Delta \text{BusyTicks}}{\Delta \text{TotalTicks}} \times 100.0$$
4. **ThresholdScheduler & Anti-Flapping:**
   * Tracks Target CPU %, Target RAM %, and Max Concurrent Functions per worker.
   * Uses Exponential Moving Average ($\alpha=0.3$) and $\pm 5\%$ hysteresis.
   * Telemetry flags:
     * `THRESHOLD_BREACH_WARNING` (amber): Auto-balancer must exceed soft thresholds.
     * `CLUSTER_QUEUE_SATURATED_ERROR` (red): Cluster capacity is fully exhausted; requests are forced to queue.
5. **Lifecycle & Governance:**
   * Worker friendly name mutation API (`PUT /api/v1/admin/workers/{id}/name`).
   * Active/Inactive switch (`POST /api/v1/admin/workers/{id}/toggle-active`), replacing drain.
   * Inactive workers excluded from global cluster CPU/RAM calculations in `ClusterMetricsService`.
   * Graceful 4-step worker revocation and deletion cascade (`DELETE /api/v1/admin/workers/{id}`).
   * Dynamic quarantine auto-accept mode for administrators (`POST /api/v1/admin/enrollment/auto-accept`).

---

## 5. Pros and Cons of the Selected Option

### Positive Consequences
* Operators enjoy zero-touch worker nodes: changing parameters in Studio immediately updates daemons cluster-wide.
* Auto-balancer prevents thermal throttling and thread exhaustion on heavily loaded nodes.
* Clear visual distinction between load spikes (threshold warnings) and real capacity exhaustion (queue errors).
* Clean separation of node lifecycle state and cluster telemetry calculations.

### Negative Consequences
* Server must maintain state synchronization and push updates reliably over LCTP WebSockets.
* Workers must handle intermittent network disconnection gracefully during configuration synchronization.

---

## 6. Links & References
* **Milestone:** `docs/dev/milestones/007-server-centric-worker-control-and-balancing.md` (`MS-007`)
* **Subsystem Specification:** `docs/specs/server-spec.md`, `docs/specs/worker-spec.md`
* **Related ADRs:** [`ADR-0005`](0005-node-enrollment-and-attestation.md), [`ADR-0006`](0006-worker-reconnect-and-polling-cadence.md), [`ADR-0007`](0007-adopt-nng-for-local-daemon-ipc.md)
