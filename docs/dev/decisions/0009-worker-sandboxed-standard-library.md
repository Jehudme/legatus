# ADR-0009: Sandboxed Standard Library Architecture & Pure In-Memory Security Model

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @worker-lead, @runtime-steward
* **Date:** 2026-10-15
* **Technical Story:** Issue #124 / PR #128
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus distributed serverless runtime executes untrusted guest workloads within isolated child processes hosting the embedded Solix Virtual Machine (`ADR-0004`, `ADR-0008`). While the core VM enforces instruction budgeting and deterministic ARC memory reclamation, guest bytecode requires standard library capabilities (string manipulation, collections, math, JSON serialization, HTTP egress, and cryptographic verification) to execute serverless functions.

Upstream `solixlib` was engineered for standalone desktop and CLI applications, binding native host capabilities such as arbitrary process execution (`solix.system.Process`), host filesystem access (`solix.io.filesystem`), and raw host environment variables (`solix.system.Environment`). In a multi-tenant distributed cloud runtime:
1. Permitting arbitrary host process execution allows guest code to escape the sandbox and compromise the host worker node.
2. Permitting arbitrary host filesystem reads or writes exposes host configuration files, leases, authentication keys, and other tenant data.
3. Permitting raw host environment variable access leaks cluster secrets, API credentials, and infrastructure metadata.
4. Allowing unguarded network egress enables Server-Side Request Forgery (SSRF) attacks against cloud metadata endpoints (`169.254.169.254`) and internal cluster services.

We require a strictly sandboxed, pure in-memory standard library architecture (`legatus-stdlib`) that provides rich serverless capabilities while enforcing zero ambient host authority and zero disk access.

---

## 2. Decision Drivers (Forces)

1. **Zero Ambient Host Authority & Zero Disk Access:** Untrusted guest bytecode must have zero capability to spawn host processes or read/write to the host disk. Execution is purely in-memory.
2. **Safe Reuse of Proven Code:** Proven, pure-memory algorithms and data structures from `solixlib` (`core`, `collections`, `math`, `time`, `crypto`, `exceptions`, `io.streams`) must be preserved without rewriting.
3. **SSRF-Guarded Egress:** Serverless web requests (`legatus.net.http`) must be strictly guarded against loopback, private RFC 1918, and cloud metadata destinations.
4. **Denial-of-Service Immunization:** Data structures, parsers (`legatus.json`), regular expressions (`legatus.validation.regex`), and decompression pipelines (`legatus.encoding`) must be hardened against ReDoS, zip bombs, and unbounded memory consumption.
5. **Static Embeddability in `legatus-runner`:** Native FFI bindings must be compiled statically into `legatus-runner`, eliminating runtime dependencies on host filesystem shared objects.

---

## 3. Considered Options

* **Option 1:** Allow upstream `solixlib` unmodified with OS-level seccomp/AppArmor/sandbox isolation.
* **Option 2:** Virtualize the filesystem via an in-memory Virtual File System (VFS) and pseudo-process tables.
* **Option 3:** Pure In-Memory Sandbox Model with Hard Sycall Stripping, Strict Bytecode Verification, and Purpose-Built Serverless Native Extensions (`legatus-stdlib`).

---

## 4. Evaluation of Considered Options

### Option 1: Allow upstream solixlib with OS-level sandboxing
* **Good:** No changes required to standard library sources or native bindings.
* **Bad:** OS-level sandbox mechanisms (seccomp-bpf, Landlock, AppArmor) vary drastically across Linux distributions, macOS, and Windows. A single misconfigured filter permits host compromise. Does not solve SSRF or ReDoS.

### Option 2: Virtual File System (VFS) & pseudo-process emulation
* **Good:** Preserves filesystem API compatibility (`solix.io.filesystem`).
* **Bad:** Introduces substantial memory overhead, synchronization complexity, and false expectations for guest developers. The platform explicit requirement is **zero disk writes and pure in-memory compute**.

### Option 3: Pure In-Memory Sandbox Model & Purpose-Built Serverless Extensions (Chosen)
* **Good:**
  * **Absolute Host Isolation:** `solix.system.Process` and `solix.io.filesystem` are permanently banned at both server bytecode validation and runner native registration stages.
  * **Static Native Linkage:** All native standard library implementations are linked statically into `legatus-runner`. No dynamic library loading from host disk is required.
  * **SSRF-Guarded Egress:** DNS pre-resolution blocks private and cloud metadata addresses before any network socket is opened.
  * **Deterministic Capabilities:** Decrypted task secrets and task parameters are injected strictly in-memory into `TaskContext` without touching disk or environment variables.
* **Bad:**
  * Guest programs written for standalone Solix that rely on disk files must be adapted to use in-memory streams or task parameters.

---

## 5. Decision Outcome

**Chosen Option:** **Option 3 (Pure In-Memory Sandbox Model & Purpose-Built Serverless Extensions).**

### Architectural Invariants:
1. **Permanent Exclusion of Disk & Process Natives:**
   * `solix.system.Process` and `solix.io.filesystem` (and `solix.io.FileStream`) are excluded from `legatus-stdlib`.
   * Server-side bytecode verification pass (`BytecodeSecurityValidator`) inspects constant pool strings during function upload and immediately rejects any reference to banned packages.
   * `legatus-runner` registers only sandboxed native symbols; unmapped calls fail deterministically.
2. **SSRF Guardrail Policy:**
   * `legatus.net.http` validates DNS resolutions against loopback (`127.0.0.0/8`, `::1`), RFC 1918 (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`), RFC 4193 (`fc00::/7`), and cloud metadata (`169.254.0.0/16`).
   * Timeouts default to 5,000ms connect and 15,000ms read; maximum response body capped at 10MB.
3. **ReDoS & Decompression Bomb Protection:**
   * `legatus.validation.regex` evaluates regular expressions with linear-time $O(N)$ guarantees (RE2).
   * `legatus.encoding` enforces a $10\times$ maximum expansion ratio and 16MB absolute size limit.
4. **Hermetic Task Context:**
   * `legatus.runtime.TaskContext` exposes task ID, instruction budget, remaining instructions, and decrypted cluster secrets in-memory. Memory is zeroed on task completion via `SecretZeroizer`.

---

## 6. Pros and Cons of the Selected Option

### Positive Consequences
* Complete elimination of host breakout vectors via filesystem or process syscalls.
* Deterministic, zero-trust guest compute environment across Linux, macOS, and Windows.
* Pure in-memory execution guarantees maximum performance with zero disk I/O latency.
* Enterprise-grade SSRF and DoS protections built directly into the standard library.

### Negative Consequences
* Code written for standalone Solix using local disk files must be refactored to use in-memory streams or HTTP egress.
* Additional native dependencies (`libcurl`, `re2`) linked into `legatus-runner`.

---

## 7. Links & References

* **Subsystem Specification:** `docs/specs/worker-spec.md`
* **Governing Guideline:** `docs/dev/guidelines/authoring-decisions.md` (`GL-0006`)
* **Milestone Contract:** `docs/dev/milestones/006-worker-sandboxed-standard-library.md` (`MS-006`)
* **Related ADRs:** [`ADR-0004`](0004-adopt-solix-for-worker-runner.md), [`ADR-0008`](0008-adopt-reproc-for-worker-task-isolation.md)
