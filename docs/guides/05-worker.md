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

## 2. Configuration & State Storage

In the server-centric control architecture (MS-007), worker configuration is authoritative on `legatus-server` and synchronized dynamically over LCTP (`CONFIG_SYNC_REQ` / `CONFIG_UPDATE_PUSH`). Compute daemons no longer rely on local static configuration files.

The local daemon persists only its unique identity and authentication credentials in secure local storage:
* **Linux / macOS:** `/var/lib/legatus/credentials.json` (or `~/.legatus/credentials.json` for non-root users, `0600` permissions).
* **Windows:** `%LOCALAPPDATA%\Legatus\credentials.json`.

```json
{
  "worker_id": "018f3a9b-1234-7000-8000-abcdef012345",
  "secret_token": "legatus_sec_...",
  "server_url": "wss://server.internal.corp:8443/ws/worker"
}
```

Upon connection, the daemon pulls its runtime profile (allocatable cores, memory limits, task timeout, telemetry interval, runner paths) dynamically from the server. Updates made in Legatus Studio are applied on the fly via `CONFIG_UPDATE_PUSH` frames without process restarts.

---

## 3. Interactive CLI Command Reference (`legatus`)

The worker CLI surface is stripped down to minimal daemon lifecycle commands:

### 3.1 Managing Daemon Lifecycle
```bash
# Start the supervisor daemon and connect to the cluster server
legatus up --server wss://server.internal.corp:8443/ws/worker

# Start with saved credentials / default server
legatus up

# Cleanly stop the daemon
legatus down

# Force stop the daemon immediately
legatus down --force

# Inspect local daemon health, active session ID, uptime, and dynamic config
legatus status

# Machine-readable status
legatus status --json
```

**Example `legatus status` Output:**
```text
Daemon Status:   ONLINE
Worker ID:       018f3a9b-1234-7000-8000-abcdef012345
Server URL:      wss://server.internal.corp:8443/ws/worker
Connected Since: 2026-10-14 16:45:00 UTC (Uptime: 4h 12m)
Allocated Cores: 4 / 16 (25.0% CPU)
Active Tasks:    4 running
Execution State: ACTIVE
Config Version:  2
```

### 3.2 Remote State & Task Governance
All operational controls—including node renaming, active/inactive suspension toggle, auto-renew lease switch, and threshold governance—are administered centrally through **Legatus Studio** or the server REST API (`/api/v1/admin/workers/...`), avoiding the need for manual SSH or local CLI access on individual compute hosts.

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
