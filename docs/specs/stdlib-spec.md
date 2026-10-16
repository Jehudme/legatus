# Legatus Sandboxed Standard Library (legatus-stdlib) Specification

## Document Metadata
* **Subsystem:** `stdlib` (`legatus-stdlib`)
* **Version:** `v1.2.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Systems Software Engineers, Security Auditors, Guest Application Developers
* **Upstream Language Runtime:** Solix VM v1.1.1+ / C++20 / OpenSSL 3.0+ / libcurl 8.0+ / Google RE2
* **Related Specifications & Decisions:**
  * `docs/specs/worker-spec.md`
  * `docs/dev/decisions/0009-worker-sandboxed-standard-library.md`
  * `docs/dev/decisions/0004-adopt-solix-for-worker-runner.md`
  * `docs/dev/decisions/0008-adopt-reproc-for-worker-task-isolation.md`

---

## 1. System Architecture & Sandbox Model

The Legatus Sandboxed Standard Library (`legatus-stdlib`) provides the deterministic execution environment for untrusted guest tasks running inside `legatus-runner` child processes.

```
+-----------------------------------------------------------------------------------+
|                        LEGATUS-RUNNER PROCESS (GUEST TASK)                         |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  |                          Solix Virtual Machine (VM)                         |  |
|  |                                                                             |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  |   |    solix.core      |  |  solix.collections   |  |     solix.math     |  |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  |   |    solix.time      |  |     solix.crypto     |  |  solix.exceptions  |  |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  |   |  legatus.net.http  |  |     legatus.json     |  |  legatus.runtime   |  |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  |   |  legatus.crypto.*  |  |  legatus.validation  |  |  legatus.encoding  |  |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  |   |    legatus.uuid    |  |  legatus.storage.kv  |  |    legatus.env     |  |  |
|  |   +--------------------+  +----------------------+  +--------------------+  |  |
|  +---------------------------------------|-------------------------------------+  |
|                                          | Native FFI Calls                       |
|  +---------------------------------------v-------------------------------------+  |
|  |              Static Native Standard Library (legatus_stdlib_native)          |  |
|  |                                                                             |  |
|  |   [SSRF Pre-Filter] ----> [libcurl]                                         |  |
|  |   [OpenSSL 3.0 EVP] ----> [HMAC / AES-256-GCM / Ed25519]                    |  |
|  |   [Google RE2]      ----> [Linear-Time O(N) Regex Matcher]                  |  |
|  |   [nlohmann_json]   ----> [Guarded JSON DOM Parser (Depth <= 32)]           |  |
|  |   [zlib / deflate]  ----> [Bomb-Protected Inflate (10x ratio, 16MB cap)]    |  |
|  |   [TaskContext]     ----> [Decrypted In-Memory Secrets / Zeroizer]          |  |
|  +---------------------------------------|-------------------------------------+  |
|                                          |                                        |
+------------------------------------------|----------------------------------------+
                                           |
                                   PROHIBITED & STRIPPED:
                                ❌ solix.system.Process (fork/exec)
                                ❌ solix.io.filesystem (File/Dir)
                                ❌ solix.io.FileStream (fopen/disk)
                                ❌ Host OS getenv() leak
```

### 1.1 The Pure In-Memory Compute Invariant
Under the platform architecture, guest tasks run with **zero disk writes and zero disk reads**.
1. All host filesystem APIs (`solix.io.filesystem`, `solix.io.FileStream`, `FileMode`) are permanently omitted from runner native registration and actively rejected by server-side bytecode validation.
2. All subprocess spawning capabilities (`solix.system.Process`) are permanently omitted and rejected.
3. Decrypted secrets and task input parameters are injected strictly in-memory into `TaskContext`.
4. Ephemeral caching (`legatus.storage.kv`) and streams (`solix.io.MemoryStream`) reside entirely in process memory.

---

## 2. Technology Stack & Sublibrary Manifest

| Module Namespace | Origin | Safety Tier | Underlying Engine | Security & Resource Guardrails |
| :--- | :--- | :--- | :--- | :--- |
| `solix.core` | `solixlib` | Inherited Safe | Solix VM Native | Memory safety, Automatic Reference Counting (ARC). |
| `solix.collections` | `solixlib` | Inherited Safe | Pure Guest `.slx` | Bounded memory collection growth within task heap. |
| `solix.math` | `solixlib` | Inherited Safe | C++ `<cmath>` | Deterministic IEEE 754 arithmetic operations. |
| `solix.time` | `solixlib` | Inherited Safe | C++ `std::chrono` | Monotonic clock and calendar arithmetic. |
| `solix.crypto` | `solixlib` | Inherited Safe | OpenSSL 3.0 EVP | SHA-256, SHA-512, MD5, Base64, Hex codecs. |
| `solix.exceptions` | `solixlib` | Inherited Safe | Solix VM Native | Typed exception hierarchies and stack traces. |
| `solix.io.streams` | `solixlib` | Inherited Safe | Pure Guest `.slx` | `MemoryStream`, `StreamReader`, `StreamWriter`. Strictly in-memory. |
| `legatus.net.http` | Legatus | New Serverless | `libcurl` | SSRF pre-filtering, redirect prevention, 10MB response ceiling. |
| `legatus.json` | Legatus | New Serverless | `nlohmann_json` | Max recursion depth: 32, max size: 8MB. |
| `legatus.runtime` | Legatus | New Serverless | Native C++20 | Secret zeroing on teardown, instruction consumption telemetry. |
| `legatus.crypto.extended` | Legatus | New Serverless | OpenSSL 3.0 EVP | Constant-time HMAC, AES-256-GCM authenticated cipher, Ed25519 verify. |
| `legatus.validation.regex`| Legatus | New Serverless | Google `re2` | Linear time $O(N)$ matching, ReDoS catastrophic backtracking immune. |
| `legatus.encoding` | Legatus | New Serverless | `zlib` | RFC 3986 URL codec, zip bomb protection ($10\times$ ratio, 16MB cap). |
| `legatus.uuid` | Legatus | New Serverless | OpenSSL `RAND` | RFC 4122 version 4 cryptographically secure UUID generator. |
| `legatus.storage.kv` | Legatus | New Serverless | Native C++20 | Ephemeral in-memory key-value store, per-key TTL, 16MB quota. |
| `legatus.env` | Legatus | New Serverless | Native C++20 | Hermetic environment variables; zero leak of host OS environment. |
| `legatus.concurrency` | Legatus | New Serverless | Pure Guest `.slx` | Lightweight `Future<T>` deferred asynchronous coordination. |

---

## 3. Security Guardrails & Boundary Specifications

### 3.1 Server-Side Bytecode Verification (`BytecodeSecurityValidator`)
Before any function bytecode is cataloged or dispatched to workers, the server parses the constant pool table:
* If any string matches banned prefixes (`solix.system.Process`, `solix.io.filesystem`, `solix.io.FileStream`, `solix.io.FileMode`), the server immediately rejects the bytecode with HTTP 422 (`BytecodeSecurityException`).
* Audits are logged to the immutable WORM audit ledger (`BYTECODE_SECURITY_REJECTED`).

### 3.2 SSRF Egress Filter (`legatus.net.http`)
Before executing any network request via `libcurl`:
1. The hostname is resolved using `getaddrinfo` with IPv4/IPv6 address validation.
2. Sockets targeting the following ranges are blocked:
   * **IPv4 Loopback:** `127.0.0.0/8`
   * **IPv6 Loopback:** `::1`
   * **RFC 1918 Private Ranges:** `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`
   * **IPv4 Link-Local / Cloud Metadata:** `169.254.0.0/16` (specifically `169.254.169.254`)
   * **IPv6 Unique Local / Metadata:** `fc00::/7`, `fd00::/8`, `fe80::/10`
   * **IPv4-Mapped IPv6:** `::ffff:0:0/96` mapping back to banned ranges
3. Redirection following (`CURLOPT_FOLLOWLOCATION`) is permanently disabled to prevent redirect bypass attacks.
4. Response body stream is truncated and capped at 10,485,760 bytes (10MB).

### 3.3 Decompression Bomb Guard (`legatus.encoding`)
Decompression streams (`Compression.inflate`) monitor output size incrementally:
* Max expansion ratio: $10\times$ the compressed input length (with a 64KB initial buffer floor).
* Absolute uncompressed output ceiling: 16,777,216 bytes (16MB).
* If either boundary is breached, decompression aborts immediately with an error.

### 3.4 Regular Expression Evaluation (`legatus.validation.regex`)
Backed exclusively by Google's `re2`:
* Strictly linear-time $O(N)$ execution in relation to input string length.
* Exponential backtracking syntax (such as backreferences or lookaround assertions) is rejected at pattern compile time, guaranteeing zero risk of ReDoS denial of service.

---

## 4. Resource Ceilings & Deterministic Quotas

| Parameter | Default Value | Hard Maximum | Enforcement Point |
| :--- | :--- | :--- | :--- |
| **Max Instruction Budget** | 10,000,000 instructions | 1,000,000,000 instructions | Solix VM instruction metering loop |
| **Max HTTP Request Timeout** | 15,000 ms | 60,000 ms | `libcurl` `CURLOPT_TIMEOUT_MS` |
| **Max HTTP Response Body** | 10 MB | 10 MB | `http_client.cpp` write callback |
| **Max JSON Recursion Depth**| 32 levels | 32 levels | `json_engine.cpp` depth counter |
| **Max EphemeralKV Footprint**| 16 MB | 16 MB | `kv_cache.cpp` memory accumulator |
| **Max Decompressed Payload**| 16 MB | 16 MB | `encoding_engine.cpp` stream counter |
| **Max Task Memory Limit** | 64 MB | 2,048 MB | Linux cgroups v2 / Process memory monitor |

---

## 5. Living References
* Milestone Plan: `docs/dev/milestones/006-worker-sandboxed-standard-library.md`
* Architectural Decision: `docs/dev/decisions/0009-worker-sandboxed-standard-library.md`
* Worker Subsystem Specification: `docs/specs/worker-spec.md`
