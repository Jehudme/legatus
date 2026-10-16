# Milestone 013: Worker Process Isolation, Protocol Pipe Separation & Signal Lifecycle

## Document Metadata
* **Milestone ID:** MS-013
* **Target Version:** `v1.9.0-worker-isolation`
* **Status:** COMPLETED
* **Lead Architect:** @runtime-architect & @worker-lead
* **Target Delivery Date:** 2027-05-15
* **Related ADRs:** ADR-0003, ADR-0004, ADR-0007, ADR-0008, ADR-0010

---

## 1. Executive Summary & Objectives

The comprehensive audit identified critical subprocess isolation flaws, signal handling mismatches, and security vulnerabilities within `legatusd` and `legatus-runner`:
1. **Runner Stdout Protocol JSON Clobbering:** The runner writes its final execution JSON to `std::cout`, which is shared with guest Solix bytecode `console.print()` and `TaskContext.emit_progress()`. Any user print preceding the JSON output breaks `nlohmann::json::parse(stdout_str)`, discarding execution metrics and causing tasks to falsely report software error 70.
2. **Linux Abort Exit Code 143 Mapped to FAILED:** Linux sends `SIGTERM` (signal 15), producing exit code `128 + 15 = 143`. `lctp_client.cpp` only mapped exit code 130 to `ABORTED`, mapping 143 to `FAILED`. This overwrites server task state from `ABORTING` to `FAILED` instead of `ABORTED`.
3. **Bytecode Cache Disconnect & Missing HTTP Fallback:** `BytecodeCache.cpp` implements disk storage and HTTP fetching, but `lctp_client.cpp` ignores it and uses an ephemeral RAM map. When bytecode deduplication is enabled and the payload is omitted, restarting the worker causes it to run tasks with empty bytecode—crashing immediately with exit code 70 rather than fetching bytecode via HTTP.
4. **Missing Wall-Clock Timeout Enforcement:** `process_supervisor.cpp` calls `proc->wait(reproc::infinite)`, ignoring `constraints.timeout_ms`. Infinite loops run forever and permanently exhaust worker cores.
5. **SSRF DNS Rebinding Vulnerability:** `http_client.cpp` validates the host IP via `getaddrinfo()`, but `libcurl` resolves DNS a second time independently during `curl_easy_perform()`. An attacker can rebind DNS to `169.254.169.254`, bypassing SSRF protection.
6. **Reproc Stream Sink Sleep Deadlock:** The rate limiter calls `sleep_for` inside the pipe drain callback, blocking the thread and filling the 64KB OS pipe buffer, deadlocking the child process.

This milestone guarantees rock-solid child process isolation, clean pipe separation, accurate signal mapping, and strict stdlib sandboxing, backed by Catch2 subprocess integration tests under AddressSanitizer and UndefinedBehaviorSanitizer.

---

## 2. Scope & Subsystems Affected

* **`worker/` (`legatusd`, `legatus-runner`, `legatus_common` - C++20):**
  * `runner/runner_engine.cpp`: Output protocol JSON to a dedicated descriptor or file (e.g. `--result-file` or FD 3), isolating guest stdout/stderr entirely to user logs.
  * `runner/main.cpp`: Install `SIGTERM` / `SIGINT` signal handlers to break Solix VM execution loop, unwind ARC memory, scrub secrets, and exit with code 130.
  * `daemon/process_supervisor.cpp`: Enforce `timeout_ms` using `reproc::stop::terminate` after timeout; eliminate blocking sleeps in drain callbacks; parse result JSON from the dedicated result file/descriptor.
  * `daemon/lctp_client.cpp`: Wire `BytecodeCache::get_or_fetch` into `handle_dispatch_task`; map exit codes 130 and 143 (SIGTERM) to `ABORTED`.
  * `stdlib/http_client.cpp`: Pin the validated IP address to the libcurl request using `CURLOPT_RESOLVE`, eliminating DNS rebinding TOCTOU attacks.
  * `stdlib/regex_engine.cpp`: Implement thread-safe LRU compilation cache for `re2::RE2` regex instances.
  * `stdlib/crypto_extended.cpp`: Upgrade legacy OpenSSL 1.x `HMAC()` to OpenSSL 3.0 `EVP_MAC`; scrub sensitive stack key buffers with `OPENSSL_cleanse`.

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Runner Protocol JSON Pipe Separation:** Modify `legatus-runner` to write the JSON execution envelope to a dedicated result file passed via `--result-file <path>` (or dedicated FD 3). Update `process_supervisor.cpp` to read execution metrics from this file, keeping stdout exclusively for guest log streaming.
- [x] `[P0]` **Cooperative SIGTERM Handler & Exit Code 143/130 Mapping:** Install POSIX `SIGTERM`/`SIGINT` (and Windows `CTRL_BREAK_EVENT`) signal handlers in `legatus-runner` to break VM loops and exit with code 130. In `lctp_client.cpp`, map both exit code 130 and exit code 143 (`128 + 15`) to terminal status `ABORTED`.
- [x] `[P0]` **BytecodeCache Integration & HTTP GET Fallback:** Replace ephemeral `bytecode_cache_` in `lctp_client.cpp` with `BytecodeCache::get_or_fetch`. When payload is omitted, check local disk cache, and if absent, fetch via HTTP `GET /api/v1/bytecode/{sha256}` before launching the runner.
- [x] `[P0]` **Wall-Clock Timeout Enforcement:** In `process_supervisor.cpp`, replace `reproc::infinite` with `reproc::milliseconds(timeout_ms)`. If timeout expires, escalate termination (`reproc::stop::terminate` followed by `reproc::stop::kill` after 10s grace) and report exit code 124 (`TIMED_OUT`).

### 3.2 P1: High Priority (Milestone Core)
- [x] `[P1]` **SSRF DNS Rebinding TOCTOU Elimination:** In `http_client.cpp`, capture the resolved IPv4/IPv6 address during `validate_ssrf_host` and construct a `CURLOPT_RESOLVE` host-to-IP pinning list (`"host:port:ip"`), forcing `libcurl` to connect exclusively to the verified IP without re-querying DNS.
- [x] `[P1]` **Reproc Sink Sleep Deadlock Elimination:** Remove blocking `std::this_thread::sleep_for` calls from the stream sink callback in `process_supervisor.cpp`. Implement token-bucket rate limiting that drops or throttles without blocking the OS pipe draining thread.
- [x] `[P1]` **OpenSSL 3.0 EVP_MAC & Secret Scrubbing:** Migrate `crypto_extended.cpp` from deprecated OpenSSL 1.x `HMAC()` to `EVP_MAC_CTX` / `EVP_MAC_fetch`. Enforce `OPENSSL_cleanse()` on all cryptographic key and digest buffers on the stack.
- [x] `[P1]` **RE2 Regex Compilation Cache:** Introduce a thread-safe LRU cache (capacity 256) in `regex_engine.cpp` to avoid recompiling regular expression patterns on every function call.

### 3.3 P2: Medium Priority (Ergonomics & Hardening)
- [x] `[P2]` **Runner-to-Daemon IPC Progress Channel:** Connect `TaskContext.emit_progress()` to local daemon IPC so `TASK_PROGRESS` frames report real instructions executed and current memory usage.
- [x] `[P2]` **Crash Core Dump Suppression in Sandboxed Runner:** Configure `setrlimit(RLIMIT_CORE, 0)` in `legatus-runner` to prevent guest segfaults from generating multi-gigabyte core dumps on the host.

---

## 4. Test Strategy Overhaul & Verification Matrix

The worker currently has zero subprocess integration tests in `worker/tests/integration/`. Catch2 tests only tested isolated unit functions without spawning processes or draining real stdout. This milestone creates the Subprocess Pipe Isolation Integration Suite:

### 4.1 Test Refactoring & New Test Deliverables

| Component Under Test | Flawed Old Test Practice | Refactored / New Test Suite | Verification Scenario |
| :--- | :--- | :--- | :--- |
| **Runner Protocol Pipe Separation** | Task printed no output; returned exit code 0 | `test_runner_isolation.cpp` (`Section: Guest Stdout Flood`) | Spawns task that prints 5MB of random logs via `console.print()`. Drains stdout into log chunks. Asserts supervisor successfully parses result JSON without syntax errors. |
| **Task Abort Signal Escalation** | Test never checked exit code; only asserted active count == 0 | `test_runner_isolation.cpp` (`Section: Signal Abort`) | Spawns infinite loop task. Calls `supervisor.abort_task()`. Verifies process exits within 500ms and supervisor returns exit code 143 or 130, mapped to `ABORTED`. |
| **Timeout Enforcement** | Supervisor wait called with `reproc::infinite`; no timeout test | `test_runner_isolation.cpp` (`Section: Execution Timeout`) | Launches task with `timeout_ms = 1000` executing infinite loop. Verifies supervisor terminates process at 1000ms with exit code 124 (`TIMED_OUT`). |
| **Bytecode HTTP Fallback** | Ignored deduplication; ran with empty payload | `test_bytecode_cache_fallback.cpp` | Starts in-process mock HTTP server. Dispatches task with empty base64 payload and valid SHA-256. Verifies worker fetches bytecode via HTTP GET and executes task successfully. |
| **SSRF DNS Rebinding** | Tested static localhost IP string check | `test_stdlib_http_ssrf.cpp` | Simulates DNS rebinding host resolving to public IP then switching to `169.254.169.254`. Verifies `CURLOPT_RESOLVE` pinning prevents connection to metadata IP. |

---

## 5. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Catch2 v3 Suite** | 100% pass across all unit and new integration tests | `ctest --output-on-failure` | [x] Completed |
| **Sanitizer Gate** | 0 memory leaks, 0 use-after-free, 0 UB errors across 100 task spawn/abort cycles | Clang 18 `-fsanitize=address,undefined` | [x] Completed |
| **Process Isolation** | Crashing guest bytecode never destabilizes daemon | `test_runner_isolation.cpp` | [x] Completed |
| **Living Specs Updated** | `worker-spec.md` and `worker-wire.md` updated with result file & exit codes | Documentation audit | [x] Completed |

---

## 6. Risk Assessment & Mitigation Strategies

* **Risk 1: Windows Process Group Escalation Differences:** On Windows, `reproc::stop::terminate` uses `CTRL_BREAK_EVENT`, which requires child processes to be spawned with `CREATE_NEW_PROCESS_GROUP`.
  * *Mitigation:* Ensure `options.process_group = true` is passed to `reproc::process::start` on Windows, and verify with CI Windows runner.
* **Risk 2: File Descriptor Exhaustion under High Concurrency:** Spawning 16 concurrent runners with dedicated result files and pipes could exceed OS `RLIMIT_NOFILE`.
  * *Mitigation:* Explicitly close pipe descriptors in parent process immediately after spawn, and delete temporary result files via RAII destructors.
