# Legatus Worker Daemon & CLI Operations Guide

## Document Metadata
* **Scope:** Worker Daemon & CLI Operations (`docs/guides/05-worker.md`)
* **Status:** Final Implementation Standard
* **Audience:** Systems Administrators, Bare-Metal Operations Personnel, Infrastructure Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/worker-spec.md`](../specs/worker-spec.md)
  * [`docs/specs/protocols/worker-wire.md`](../specs/protocols/worker-wire.md)
  * [`docs/dev/decisions/0007-adopt-nng-for-local-daemon-ipc.md`](../dev/decisions/0007-adopt-nng-for-local-daemon-ipc.md)
  * [`docs/dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md`](../dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md)

---

## 1. Overview & Architecture

The Legatus worker compute node consists of two native C++20 executables:
1. **`legatusd` (Supervisor Daemon):** Long-running background service that manages outbound TLS 1.3 WebSockets (`LCTP`) to `legatus-server`, isolates guest tasks inside ephemeral child processes (`legatus-runner`) via `reproc++`, and listens for local operator commands.
2. **`legatus` (Operator CLI):** Interactive command-line tool used by local administrators to control the daemon (`up`, `down`, `drain`, `status`, `log level`, `tasks`).

In accordance with [`ADR-0007`](../dev/decisions/0007-adopt-nng-for-local-daemon-ipc.md), the `legatus` CLI has zero direct network connectivity to the remote server. All commands are dispatched locally to `legatusd` over an `nng` Request/Reply (`REQ0`/`REP0`) IPC socket:
* **Linux / macOS:** Unix Domain Socket at `/tmp/legatus_worker.ipc` (`0600` permissions).
* **Windows:** Win32 Named Pipe at `ipc://legatus_worker_ipc` with explicit SDDL DACL (`D:(A;;GRGW;;;AU)`).

---

## 2. Configuration Reference (`worker.toml`)

The worker daemon is configured via TOML located at `/etc/legatus/worker.toml`:

```toml
[server]
url = "wss://server.internal.corp:8443/ws/worker"
rest_url = "https://server.internal.corp:8443/api/v1"
tls_ca_cert = "/etc/legatus/tls/ca.crt"
connect_timeout_seconds = 10
reconnect_backoff_initial_ms = 1000
reconnect_backoff_max_ms = 30000

[node]
worker_id = "compute-linux-east-042"
tags = ["gpu=none", "zone=us-east-1a", "tier=standard"]

[capacity]
# Declare allocatable capacity (0 = auto-detect all physical cores)
allocatable_cores = 16
max_memory_bytes = 34359738368 # 32 GB

[logging]
level = "info" # trace, debug, info, warn, error
log_file = "/var/log/legatus/worker.log"

[isolation]
runner_binary = "/opt/legatus/bin/legatus-runner"
bytecode_cache_dir = "/var/cache/legatus/bytecode"
scratch_dir = "/var/run/legatus/scratch"
abort_grace_period_ms = 10000
```

---

## 3. Interactive CLI Command Reference (`legatus`)

All commands use `CLI11` parsing and communicate with the local `legatusd` daemon:

### 3.1 Managing Daemon Lifecycle
```bash
# Start the supervisor daemon in the background
legatus up --config /etc/legatus/worker.toml

# Cleanly stop the daemon (cancels active tasks immediately)
legatus down

# Inspect local daemon health, active session ID, and uptime
legatus status
```

**Example `legatus status` Output:**
```text
Daemon Status:   ONLINE
Session ID:      ses_01HXYZ789ABCDEF
Connected Since: 2026-10-14 16:45:00 UTC (Uptime: 4h 12m)
Remote Broker:   wss://server.internal.corp:8443/ws/worker
Allocated Cores: 4 / 16 (25% utilization)
Active Tasks:    4 running (PIDs: 4812, 4815, 4819, 4822)
Drain Status:    NORMAL (Accepting new tasks)
```

### 3.2 Draining Nodes Before Maintenance
When performing host reboots or kernel upgrades, drain the node to allow in-flight tasks to complete while preventing new task dispatches:

```bash
# Unshelve node from server scheduling ring with a 60-second deadline
legatus drain --timeout 60
```
* The daemon immediately transmits an expedited `DRAIN_NOTICE` frame over the WebSocket.
* The server marks the worker `DRAINING` and stops scheduling new tasks to it.
* In-flight tasks complete naturally. Once active task count reaches 0, the daemon exits cleanly.

### 3.3 Inspecting and Terminating Guest Tasks
```bash
# List all active tasks running locally on this node (optionally format with --json)
legatus tasks list

# Trigger termination of a specific task with optional audit reason
legatus tasks kill tsk_01HXYZ... --reason "Operator manual abort"
```

### 3.4 Dynamically Tuning Log Levels and Viewing Logs
Change the supervisor daemon's log verbosity at runtime without restarting the process:
```bash
# Dynamically switch logging filter level (trace, debug, info, warn, error)
legatus log level debug
legatus log level info

# Display trailing lines from the daemon log file
legatus log show --lines 100
```

---

## 4. Production Service Deployment

### 4.1 Linux Systemd Service (`/etc/systemd/system/legatusd.service`)
```ini
[Unit]
Description=Legatus Worker Supervisor Daemon
After=network.target

[Service]
Type=simple
User=root
ExecStart=/opt/legatus/bin/legatusd --config /etc/legatus/worker.toml
Restart=always
RestartSec=3s
LimitNOFILE=65536
KillMode=process

[Install]
WantedBy=multi-user.target
```

### 4.2 Windows Service Deployment (`sc.exe`)
On Windows hosts, register `legatusd` as a native Windows service running under `NT AUTHORITY\SYSTEM`:
```cmd
sc.exe create legatusd binPath= "C:\Program Files\Legatus\bin\legatusd.exe --config C:\ProgramData\Legatus\worker.toml" start= auto
sc.exe start legatusd
```
*Note:* The Windows Named Pipe listener automatically applies SDDL descriptor `D:(A;;GRGW;;;AU)` so that non-elevated operators running `legatus.exe` in interactive Command Prompts can control the service.

---

## 5. Exit Codes & Diagnostics Reference

When `legatus-runner` child processes terminate, `legatusd` interprets the following exit codes:

| Exit Code | Mnemonic | Description & Root Cause |
| :--- | :--- | :--- |
| `0` | `COMPLETED` | Clean execution. Result JSON written to stdout. |
| `1` | `FAILED` | Guest application threw an unhandled Solix runtime exception. |
| `70` | `EX_SOFTWARE` | Instruction budget exceeded (`ERR_INSTRUCTION_BUDGET_EXCEEDED`). |
| `71` | `EX_OSERR` | Out of Memory (OOM) trap inside guest memory arena. |
| `75` | `EX_TEMPFAIL` | Host scratch filesystem temporary failure. |
| `124` | `TIMEOUT` | Task wall-clock execution deadline expired. |
| `130` | `ABORTED` | Clean cooperative shutdown via Stage 1 `terminate` (`SIGTERM` / `CTRL_BREAK_EVENT`). |
| `137` | `KILLED_FORCIBLY` | Stage 2 unconditional kill (`SIGKILL` / `TerminateProcess`) after 10s grace period expired. |
| `139` | `CRASHED` | Child process segmentation fault (`SIGSEGV`) in native FFI plugin. |
