# ADR-0008: Adopt `reproc++` for Worker Child Process Isolation & Two-Stage Kill Escalation

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @worker-lead, @runtime-steward
* **Date:** 2026-10-14
* **Technical Story:** Issue #112 / PR #115
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus worker daemon (`legatusd`) supervises the execution of guest Solix bytecode workloads. If guest tasks were executed on threads inside the `legatusd` daemon process itself, a single segmentation fault in a native FFI plugin (`solix::SharedLibrary`), an unhandled memory corruption, or an OS-level abort would crash the entire worker daemon—severing the LCTP WebSocket connection to `legatus-server` and killing all other concurrent tasks running on that host.

To guarantee fault isolation (blast radius of exactly 1 task), every task must run inside an ephemeral child OS process (`legatus-runner`). Furthermore, when a task exceeds its wall-clock timeout or an operator clicks **[Abort Task]** in `legatus-studio` (`ABORT_TASK` wire frame), `legatusd` must deterministically:
1. Deliver a cooperative stop signal so `legatus-runner` can break its VM loop, unwind ARC references, scrub secrets in RAM (`explicit_bzero` / `SecureZeroMemory`), and exit cleanly (`130`).
2. Enforce a strict **10-second grace period**, after which a hung or deadlocked process is unconditionally destroyed by the OS kernel (`137`).
3. Stream the execution envelope into child `stdin` and drain `stdout`/`stderr` pipes without deadlocking or leaking zombie processes across Linux, macOS, and Windows (which lacks `fork()` and POSIX signals).

---

## 2. Decision Drivers (Forces)

1. **Strict OS Process Fault Isolation:** Guest tasks must execute in separate virtual address spaces so crashes (`SIGSEGV`), OOM traps (`71`), or forced kills (`137`) never destabilize `legatusd`.
2. **Cross-Platform Two-Stage Escalation (`terminate` $\to$ `kill`):** Must provide a unified C++20 API that maps cooperative termination (`SIGTERM` on POSIX; `CTRL_BREAK_EVENT` via `CREATE_NEW_PROCESS_GROUP` on Windows) and hard kills (`SIGKILL` on POSIX; `TerminateProcess` on Windows) with a 10,000ms timeout.
3. **Stdin Envelope Streaming & Deadlock-Free Pipe Draining:** Must support writing arbitrarily large JSON execution envelopes (> 32 KB, bypassing Windows `CreateProcessW` `argv` limits) to child `stdin` and concurrently draining `stdout`/`stderr` via anonymous pipes.
4. **Deterministic RAII & Zero Zombie Leaks:** Process descriptors and pipe handles must be wrapped in RAII types (`GL-0011`) and reaped cleanly under ASan/UBSan.

---

## 3. Considered Options

* **Option 1:** `reproc++` (`v14.2+`, Cross-Platform C/C++ Process Library) with out-of-process `legatus-runner` child executables.
* **Option 2:** Raw OS Process Primitives (`fork()`/`execve()`/`waitpid()`/`kill()` on POSIX + `CreateProcessW()`/`GenerateConsoleCtrlEvent()`/`TerminateProcess()` on Windows).
* **Option 3:** `Boost.Process` (`v1` / `v2`) or In-Process Worker Threads (`std::jthread`).

---

## 4. Evaluation of Considered Options

### Option 1: `reproc++` (`v14.2+`) Out-of-Process Supervision
* **Description:** Spawn each task as a standalone `legatus-runner` child process using `reproc::process`. Stream the JSON execution envelope over `process.write()` (`stdin`) and close the input stream (`reproc::stream::in`), drain output via `reproc::drain()`, and handle cancellation/timeouts via `reproc::stop_actions` (`{reproc::stop::terminate, 10000ms}` followed by `{reproc::stop::kill, infinite}`).
* **Good:**
  * Built-in, declarative two-stage escalation (`reproc::stop::terminate` $\to$ wait $\to$ `reproc::stop::kill`) that works identically across Linux, macOS, and Windows.
  * Clean C++ RAII class (`reproc::process`) whose destructor automatically stops and reaps the child process if an exception occurs (`GL-0011`).
  * `reproc::drain` multiplexes `stdout` and `stderr` pipes concurrently, preventing child processes from blocking on full OS pipe buffers.
  * Tiny, zero-dependency C/C++ library that fetches and compiles in seconds via CMake `FetchContent`.
* **Bad:**
  * Spawning an OS process incurs a $\approx 1.5\text{--}3\text{ ms}$ kernel overhead compared to sub-microsecond in-process threads (still 100x faster than Docker containers).

### Option 2: Raw OS Process & Pipe Syscalls
* **Description:** Write custom `#ifdef _WIN32` vs POSIX process spawning, pipe creation, `poll()`/`WaitForMultipleObjects()` loops, and signal escalation timers.
* **Good:**
  * Zero external library dependencies.
* **Bad:**
  * Over 1,200 lines of subtle, error-prone platform code.
  * High risk of file-descriptor leaks across `fork()`, pipe buffer deadlocks when child writes > 64 KB to `stderr`, and zombie processes if `waitpid()` races with `SIGCHLD`.

### Option 3: `Boost.Process` or In-Process `std::jthread`
* **Description:** Use `boost::process::child` for subprocesses, or run `solix::RuntimeContext` on `std::jthread` inside `legatusd`.
* **Good:**
  * In-process threads have near-zero spawn latency.
* **Bad:**
  * **In-Process Threads Violate Cluster Safety:** A C++ thread cannot be safely force-killed (`pthread_cancel` / `TerminateThread` leaks locks, heap arenas, and file descriptors, corrupting the daemon process), and a single segfault in a native plugin crashes `legatusd`.
  * **`Boost.Process` Bloat:** Pulls in massive Boost headers and `Boost.Asio`/`Boost.Filesystem` dependencies, slowing CI builds and violating minimal-dependency goals.

---

## 5. Decision Outcome

Chosen Option: **Option 1: `reproc++` (`v14.2+`) Out-of-Process Supervision**, because it isolates every guest task inside a private OS virtual memory space, provides a declarative cross-platform 2-stage termination sequence (`terminate` $\to$ 10s $\to$ `kill`), prevents pipe deadlocks, and complies with `GL-0011` RAII invariants.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Complete fault isolation: a crashing (`SIGSEGV`, exit `139`), out-of-memory (exit `71`), or hung (exit `137`) `legatus-runner` never affects `legatusd` or neighboring tasks.
* Deterministic two-stage cancellation (`ABORT_TASK` and `legatus tasks kill`) gives guest tasks 10 seconds to unwind ARC memory and scrub secrets before the kernel destroys the process.
* Passing the execution envelope over `stdin` (`worker-spec.md` Section 5.2) completely avoids the 32,767-character command-line limit on Windows `CreateProcessW` and hides secrets from `ps aux` / `/proc/<pid>/cmdline`.

### 6.2 Negative Consequences (Trade-offs)
* **Windows Process Group Requirement:** To allow `reproc::stop::terminate` (`CTRL_BREAK_EVENT`) to target a child process on Windows without killing `legatusd` itself, the supervisor must spawn `legatus-runner` with `CREATE_NEW_PROCESS_GROUP` (`worker-spec.md` Section 6.1).
* **Dedicated Supervisor Wait Thread:** Draining pipes and waiting on child exit requires one lightweight C++ supervisor thread per active concurrent task (bounded by `allocatable_cores`).

### 6.3 Neutral Consequences
* All terminal exit codes (`0`, `1`, `71`, `75`, `124`, `130`, `137`, `139`) are normalized and mapped to LCTP `TASK_RESULT` status enums in `worker-spec.md` Section 6.2.

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Governed by `docs/specs/worker-spec.md` (Sections 5 & 6), `docs/specs/protocols/worker-wire.md` (Section 4.7 & 4.10), and `GL-0011` (Section 5).
* **Test Verification:**
  * Catch2 suite `worker/tests/unit/test_task_supervision.cpp` verifying exit codes `0`, `1`, `71`, `75`, and `stdin` envelope injection.
  * Catch2 suite `worker/tests/unit/test_cancellation_escalation.cpp` verifying both Stage 1 cooperative stop (`ABORTED`, code `130` in $<10\text{s}$) and Stage 2 hard kill (`KILLED_FORCIBLY`, code `137` when a mock task ignores `SIGTERM`).
* **Sanitizer Gate:** Zero leaked file descriptors, pipes, or heap allocations across 1,000 task spawn/kill cycles under Clang AddressSanitizer (ASan) and UndefinedBehaviorSanitizer (UBSan) (`GL-0009`).