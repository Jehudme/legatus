# Milestone 003: Worker Daemon, Operator CLI & Task Runner MVP Implementation

## Document Metadata
* **Milestone ID:** MS-003
* **Target Version:** `v1.0.0-worker-mvp`
* **Status:** ACTIVE
* **Lead Architect:** @worker-lead
* **Target Delivery Date:** 2026-11-15
* **Related ADRs:** ADR-0004, ADR-0005, ADR-0006, ADR-0007, ADR-0008

---

## 1. Executive Summary & Objectives

Deliver the production-grade C++20 worker subsystem comprising three discrete executables (`worker-spec.md` Section 1.1): the background supervisor daemon (`legatusd`), the interactive operator CLI (`legatus`), and the sandboxed guest task runner (`legatus-runner`). This milestone implements zero-config quarantine enrollment with Ed25519 visual fingerprints (`ADR-0005`), outbound TLS 1.3 LCTP WebSocket streaming with exponential jittered backoff (`ADR-0006`), cross-platform local IPC via `nng` (`ADR-0007`), out-of-process child task supervision with 10-second two-stage kill escalation via `reproc++` (`ADR-0008`), and deterministic Solix VM instruction metering and memory arena bounds (`ADR-0004`).

---

## 2. Scope & Subsystems Affected

* `worker/daemon/` (`legatusd`): Quarantine enrollment client, two-tier ticket exchange, outbound `IXWebSocket` LCTP state machine, jittered heartbeat engine, `nng` `REP0` local IPC server, and `reproc++` child process supervisor.
* `worker/cli/` (`legatus`): `CLI11` subcommands (`up`, `down`, `drain`, `status`, `log level`, `log show`, `tasks list`, `tasks kill`) communicating exclusively over `nng` `REQ0` local IPC.
* `worker/runner/` (`legatus-runner`): Ephemeral child process reading the JSON execution envelope from `stdin`, enforcing $128\text{ MB}$ default memory arena ceilings and branch/call opcode instruction budgets on `solix_core`, and scrubbing secret buffers via `explicit_bzero` / `SecureZeroMemory`.
* `worker/tests/`: Hermetic Catch2 v3 unit and integration test suites using `TempDir` fixtures and in-process `ix::WebSocketServer` mock brokers (`GL-0009`).

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [ ] `[P0]` **Out-of-Process Supervision & Two-Stage Kill Escalation (`reproc++`):** Spawn `legatus-runner` child processes via `reproc::process` (with `CREATE_NEW_PROCESS_GROUP` on Windows), stream execution envelopes over `stdin` (`EOF`), drain `stdout`/`stderr` pipes without deadlocks, and enforce Stage 1 cooperative stop (`SIGTERM` / `CTRL_BREAK_EVENT`, 10,000ms grace period) followed by Stage 2 hard kill (`SIGKILL` / `TerminateProcess`). (Issue #301)
- [x] `[P0]` **Solix VM Instruction Metering & Memory Arena Ceilings (`legatus-runner`):** Link `solix_core`, configure stack ($8\text{ MB}$) and heap ($M_{\text{max}} - 8\text{ MB}$) arenas, meter instruction consumption on branch/call opcodes (`JUMP`, `JUMP_IF_FALSE`, `JUMP_IF_TRUE`, `CALL`, `CALL_VIRTUAL`, `CALL_INTERFACE`), and deterministically exit with code `75` (`EX_TEMPFAIL`) on budget exhaustion or code `71` (`EX_OSERR`) on OOM. (Issue #302)
- [x] `[P0]` **Mandatory Secret Buffer Zeroing (`GL-0011`):** Scrub all RAM buffers containing the `stdin` execution envelope and decrypted task secrets using `explicit_bzero` (POSIX) or `SecureZeroMemory` (Windows) in an RAII scope guard prior to runner exit. (Issue #303)
- [ ] `[P0]` **Heartbeat Dead-Man's Switch & Exponential Jittered Backoff:** Emit `HEARTBEAT` frames every $5000\text{ ms} \pm 500\text{ ms}$, abort the WebSocket if no `HEARTBEAT_ACK` arrives within $15000\text{ ms}$, and reconnect using $t_{\text{backoff}} = \min(30, 2^{\text{attempt}}) + \text{UniformRandom}(0, 1)$ with `SESSION_INIT` task reconciliation. (Issue #304)

### 3.2 P1: High Priority (Milestone Core)
- [ ] `[P1]` **Quarantine Node Enrollment & Visual Fingerprint (`OpenSSL 3.0`):** Generate Ed25519 keypairs via `EVP_PKEY_ED25519`, compute $\text{SHA256}(\text{public\_key})$ hex fingerprints and 3-word visual codes (`Tiger-Galaxy-77`), poll `/api/v1/workers/register/{id}/status` with jitter, and atomically write `worker.toml` via `toml++`. (Issue #305)
- [ ] `[P1]` **Cross-Platform Local IPC Bus (`nng` `REQ0`/`REP0`):** Implement RAII-wrapped `nng` IPC server in `legatusd` and client in `legatus` over `ipc:///tmp/legatus_worker.ipc` (POSIX) and `ipc://legatus_worker_ipc` (Windows with SDDL `D:(A;;GRGW;;;AU)`), supporting all 6 IPC actions (`DAEMON_STATUS`, `DAEMON_DOWN`, `DAEMON_DRAIN`, `SET_LOG_LEVEL`, `GET_LOG_CONFIG`, `ABORT_TASK`). (Issue #306)
- [ ] `[P1]` **Complete Operator CLI (`CLI11`):** Implement `legatus up`, `down`, `drain` (emitting immediate `DRAIN_NOTICE` wire frame), `status [--json]`, `log level`, `log show`, `tasks list`, and `tasks kill <id>`. (Issue #307)
- [ ] `[P1]` **Cross-Platform Path & Host Telemetry Collector:** Resolve config, state, and log paths dynamically across Linux, macOS, and Windows, and collect live host CPU load % and free/used RAM bytes for `HEARTBEAT` frames. (Issue #308)

### 3.3 P2: Medium Priority (Ergonomics & Polish)
- [ ] `[P2]` **Local Bytecode Deduplication Cache:** Cache downloaded Solix bytecode binaries by `<sha256>.slxb` in the worker state directory and fetch missing binaries via `GET /api/v1/bytecode/{sha256}`. (Issue #309)
- [ ] `[P2]` **Rotating Log Sinks & ANSI CLI Tables:** Configure `spdlog` rotating file sinks (`50 MB` max size, `5` backup files) and format `legatus status` / `legatus tasks list` with clean terminal tables. (Issue #310)

### 3.4 P3: Low Priority (Opportunistic Cleanups)
- [ ] `[P3]` **CMake Precompiled Headers & CPack Packaging:** Configure precompiled headers (`pch.hpp`) to accelerate Catch2 compilation and add CPack rules for `.deb`, `.rpm`, `.pkg`, and `.msi` worker installers. (Issue #311)

---

## 4. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | 100% pass across all unit & `ix::WebSocketServer` integration suites (positive + negative per `GL-0009`) | `ctest --test-dir build --output-on-failure` | [ ] Pending |
| **JUnit 5 Suite** | N/A (C++20 subsystem) | — | [x] N/A |
| **Sanitizer Gate** | Zero memory leaks, zero fd/pipe leaks, and zero UB across 1,000 task spawn/kill cycles | Clang 18 `-fsanitize=address,undefined` | [ ] Pending |
| **Documentation** | `worker-spec.md`, `worker-wire.md`, and `05-worker.md` verified against `CLI11` flags (`GL-0010`) | `scripts/verify-cli-docs.sh` | [ ] Pending |
| **ADR Governance** | `ADR-0004`, `ADR-0005`, `ADR-0006`, `ADR-0007`, and `ADR-0008` compliance verified | Maintainer audit (`GL-0006`) | [ ] Pending |

---

## 5. Risk Assessment & Mitigation Strategies

* **Risk 1: Zombie Child Processes or Pipe Deadlocks When Guest Tasks Flood `stdout`/`stderr`**
  * *Mitigation:* Wrap `reproc::process` in a strict RAII session class (`GL-0011` Section 5) and drain `stdout` and `stderr` concurrently via `reproc::drain` before invoking `process.wait()`.
* **Risk 2: Windows Service Named Pipe Access Denied (`ERROR_ACCESS_DENIED`) for Non-Admin CLI Users**
  * *Mitigation:* Configure `NNG_OPT_IPC_SECURITY_DESCRIPTOR` with explicit SDDL `D:(A;;GRGW;;;AU)` when initializing the `nng` `REP0` listener on Windows (`worker-spec.md` Section 3.1).
