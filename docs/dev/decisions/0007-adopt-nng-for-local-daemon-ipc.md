# ADR-0007: Adopt `nng` (nanomsg-next-gen) for Cross-Platform Local CLI-Daemon IPC

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @worker-lead, @sys-architect
* **Date:** 2026-10-14
* **Technical Story:** Issue #111 / PR #114
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus worker subsystem splits node supervision and interactive operator control into two separate C++20 executables (`worker-spec.md` Section 1.1):
1. **`legatusd` (Supervisor Daemon):** Long-running background service managing outbound TLS 1.3 WebSockets and child `legatus-runner` processes.
2. **`legatus` (Operator CLI):** Interactive command-line utility (`up`, `down`, `drain`, `status`, `log level`, `tasks list`, `tasks kill`).

In accordance with `worker-spec.md`, the `legatus` CLI has zero direct network connectivity to `legatus-server`; it must communicate with the local `legatusd` daemon over a fast, secure, local Inter-Process Communication (IPC) channel. However, local IPC primitives differ drastically between POSIX (`AF_UNIX` Unix Domain Sockets) and Windows (Win32 Named Pipes `\\.\pipe\...` with `OVERLAPPED` I/O and Security Descriptors). Furthermore, raw stream sockets require manual message length-prefix framing to avoid partial JSON reads. We need a lightweight, cross-platform IPC transport library that abstracts Unix Domain Sockets and Windows Named Pipes behind a single message-oriented Request/Reply API.

---

## 2. Decision Drivers (Forces)

1. **Unified Cross-Platform API (`ipc://`):** The IPC layer must use Unix Domain Sockets on Linux/macOS (`ipc:///tmp/legatus_worker.ipc`) and Named Pipes on Windows (`ipc://legatus_worker_ipc`) via a single C/C++ API without `#ifdef _WIN32` socket/pipe state machines.
2. **Atomic Request/Reply Framing (`REQ0` / `REP0`):** Each CLI command (`DAEMON_STATUS`, `DAEMON_DRAIN`, `SET_LOG_LEVEL`, `ABORT_TASK`) follows a deterministic synchronous request/response pattern that must handle message boundaries, timeouts, and retries automatically.
3. **Zero Local TCP Port Collisions:** Local administration must not bind to loopback TCP ports (`127.0.0.1:xxxx`), which can collide with user applications and are accessible to unprivileged local processes without filesystem/pipe ACLs.
4. **Lightweight CMake Integration:** Must compile cleanly from source via CMake `FetchContent` in seconds with zero external dependencies (unlike Boost or gRPC).

---

## 3. Considered Options

* **Option 1:** `nng` (nanomsg-next-gen, `v1.8+`) using `NNG_PROTO_REQ0` / `NNG_PROTO_REP0` over `ipc://`.
* **Option 2:** Raw OS-specific IPC (`sys/un.h` `AF_UNIX` sockets on POSIX + Win32 `CreateNamedPipeW` / `ReadFile` on Windows).
* **Option 3:** Loopback HTTP/gRPC server on `127.0.0.1` (or `ZeroMQ` / `libzmq`).

---

## 4. Evaluation of Considered Options

### Option 1: `nng` (nanomsg-next-gen) `REQ0`/`REP0`
* **Description:** Embed `nng` (the modern, thread-safe C99 rewrite of Scalability Protocols / nanomsg) into `legatusd` (`nng_rep0_open` + `nng_listen`) and `legatus` (`nng_req0_open` + `nng_dial`) using `ipc://` URIs and UTF-8 JSON payloads (`nlohmann/json`).
* **Good:**
  * Transparently maps `ipc:///tmp/legatus_worker.ipc` to `AF_UNIX` on Linux/macOS and `ipc://legatus_worker_ipc` to `\\.\pipe\legatus_worker_ipc` on Windows.
  * Built-in atomic message framing: `nng_send()` and `nng_recv()` deliver complete, length-prefixed buffers without manual byte-stream loops.
  * Native support for Windows `SECURITY_ATTRIBUTES` / SDDL descriptors (`NNG_OPT_IPC_SECURITY_DESCRIPTOR`) and POSIX socket permissions (`NNG_OPT_IPC_PERMISSIONS`).
  * Pure C library with zero external dependencies and fast CMake `FetchContent` builds.
* **Bad:**
  * C-style handle API (`nng_socket`, `nng_aio`) requires a thin C++20 RAII wrapper class (`GL-0011` Section 2.1).

### Option 2: Raw OS-Specific Sockets & Win32 Named Pipes
* **Description:** Write separate POSIX `socket(AF_UNIX, SOCK_STREAM, 0)` and Windows `CreateNamedPipeW` / `ConnectNamedPipe` implementations with custom 4-byte big-endian length prefixes.
* **Good:**
  * Zero third-party library dependencies.
* **Bad:**
  * High maintenance burden (~800 lines of platform-specific asynchronous I/O and framing code).
  * Win32 Named Pipe `OVERLAPPED` I/O and broken-pipe state recovery are notoriously error-prone and difficult to keep in parity with POSIX `poll()`.

### Option 3: Loopback TCP HTTP Server or `ZeroMQ` (`libzmq`)
* **Description:** Bind an HTTP server on `127.0.0.1:9099` or use `libzmq`.
* **Good:**
  * Familiar HTTP semantics.
* **Bad:**
  * Loopback TCP ports risk port conflicts on busy hosts and allow any local user/container sharing the network namespace to send unauthenticated requests unless token files are managed.
  * `libzmq` has legacy C++ build quirks on Windows compared to `nng` (which was specifically created by the original authors to modernize nanomsg/ZeroMQ).

---

## 5. Decision Outcome

Chosen Option: **Option 1: `nng` (nanomsg-next-gen) `REQ0`/`REP0` over `ipc://`**, because it provides a unified, zero-dependency, message-framed IPC layer across Linux, macOS, and Windows while supporting native OS permission controls on sockets and Named Pipes.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* `legatus` CLI and `legatusd` exchange structured JSON actions (`DAEMON_STATUS`, `DAEMON_DOWN`, `DAEMON_DRAIN`, `SET_LOG_LEVEL`, `GET_LOG_CONFIG`, `ABORT_TASK`) with sub-millisecond local latency.
* Zero local TCP ports are opened by `legatusd`.
* Identical C++20 IPC client/server code compiles and runs across Linux, macOS, and Windows.

### 6.2 Negative Consequences (Trade-offs)
* **Windows Service Pipe Permissions:** When `legatusd` runs as `NT AUTHORITY\SYSTEM` on Windows, the default Named Pipe DACL blocks non-elevated CLI users; therefore, as specified in `worker-spec.md` Section 3.1, the daemon **MUST** configure the explicit SDDL string `D:(A;;GRGW;;;AU)` on the `nng` listener.
* **Stale Socket File Cleanup on POSIX:** If `legatusd` is killed with `SIGKILL` on Linux/macOS, `/tmp/legatus_worker.ipc` may remain on disk; `legatusd` must unlink any stale socket file after verifying no active process holds the lock on startup.

### 6.3 Neutral Consequences
* All `nng_socket` and buffer allocations must be wrapped in RAII classes (`nng_close`, `nng_free`) in accordance with `GL-0011`.

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Governed by `docs/specs/worker-spec.md` (Sections 1.1, 2, 3.1, and 4.1) and `GL-0011`.
* **Test Verification:** Catch2 test suite `worker/tests/unit/test_ipc_commands.cpp` spawning an in-process `nng` `REP0` server on an isolated `TempDir` IPC endpoint and verifying all 6 IPC action schemas (both positive execution and negative malformed JSON / timeout handling per `GL-0009`).
* **Sanitizer Gate:** Verified leak-free under Clang AddressSanitizer (ASan) and UndefinedBehaviorSanitizer (UBSan).