# ADR-0004: Adopt Solix Virtual Machine for Deterministic Guest Task Execution

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @worker-lead, @runtime-steward
* **Date:** 2026-10-14
* **Technical Story:** Issue #108 / PR #111
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus distributed platform is engineered as an ultra-high-density serverless compute environment. Worker nodes (`legatusd`) must execute untrusted, multi-tenant guest workloads scheduled by `legatus-server`. In serverless environments, guest workloads present unique runtime challenges:
1. **Cold-Start Latency:** Spawning heavy execution sandboxes (e.g., standard Linux containers or full virtual machines) introduces 100ms–2000ms latency overhead, destroying the economics of short-lived, bursty micro-tasks.
2. **Deterministic Resource Metering:** Traditional operating system time-slicing (e.g., CPU shares or wall-clock quotas via `cgroups`) is non-deterministic. A task running on an AMD EPYC CPU completes in fewer milliseconds than on an ARM edge core, creating inconsistent billing and unpredictable execution timeouts. We require instruction-level metering that yields identical consumption counts regardless of underlying host CPU clock speeds.
3. **Memory Determinism & Latency Spikes:** Tracing garbage collectors (JVM, Go, V8) introduce unpredictable "stop-the-world" latency pauses, making precise latency SLAs impossible.
4. **Sandboxed Security Invariants:** Untrusted user code must be strictly isolated, preventing arbitrary host system calls (`open`, `socket`, `fork`, `ptrace`), host filesystem tampering, and unauthorized cluster network access.

We require a lightweight, embeddable virtual machine architecture capable of sub-millisecond cold starts, deterministic ISA-level instruction budgeting, automatic reference counting memory management, and complete host isolation.

---

## 2. Decision Drivers (Forces)

1. **Deterministic ISA Instruction Metering:** Execution resource consumption must be measured in discrete bytecode instructions executed ($I_{\text{consumed}} \le I_{\text{budget}}$), ensuring 100% deterministic, reproducible compute accounting across x86_64, AArch64, and heterogeneous hardware nodes.
2. **Sub-Millisecond Cold Starts:** Spawning a guest execution context must complete in under **1 millisecond** ($< 1\text{ ms}$), enabling thousands of ephemeral tasks to execute per second on a single host.
3. **Deterministic ARC Memory Model:** The guest runtime must manage heap memory using Automatic Reference Counting (ARC) with deterministic destructors, eliminating non-deterministic tracing garbage collection pauses.
4. **Zero Ambient Host Authority:** Guest bytecode must execute in a hermetic sandbox with zero access to native OS syscalls, raw network sockets, or host file paths. Host interactions are permitted solely via explicit, audited host-imported FFI bridges (`solix::SharedLibrary`).
5. **Native C++20 Embeddability:** The VM must integrate seamlessly into the `legatus-runner` child executable via modern C++20 bindings.

---

## 3. Considered Options

* **Option 1:** Solix Virtual Machine (upstream deterministic bytecode engine with ARC memory and ISA metering).
* **Option 2:** WebAssembly (Wasm) Runtimes (e.g., Wasmtime or WasmEdge with WASI).
* **Option 3:** Linux Containers (Docker / containerd / runC) with cgroups v2.
* **Option 4:** MicroVMs (AWS Firecracker / Cloud-Hypervisor).

---

## 4. Evaluation of Considered Options

### Option 1: Solix Virtual Machine
* **Description:** Embed the upstream Solix VM engine directly into `legatus-runner`. The VM executes compiled Solix bytecode (`.sb` / `.solix`), tracks execution instructions atomically via virtual CPU cycle counters, and manages objects using an Automatic Reference Counting (ARC) memory model. When $I_{\text{consumed}} > I_{\text{budget}}$, the VM interrupts execution deterministically and triggers exit code `70` (`EX_SOFTWARE` / Quota Exceeded).
* **Good:**
  * **True Determinism:** Instruction counting is built directly into the VM instruction dispatch loop, providing byte-accurate execution costs regardless of host processor frequency or scheduling jitter.
  * **Ultra-Low Cold Start:** Context instantiation takes under **200 microseconds** ($< 0.2\text{ ms}$), requiring only a small memory arena allocation.
  * **ARC Memory Guarantees:** Objects are freed immediately when their reference count drops to zero; no mark-sweep garbage collection pauses.
  * **Hermetic Sandbox:** Bytecode has no raw syscall instruction; all I/O is routed through strictly controlled, bounded host callback tables.
  * **Modern C++20 Integration:** C++ headers and static libraries integrate smoothly into CMake via `FetchContent` or Git submodules.
* **Bad:**
  * Requires source code to be compiled to Solix bytecode; cannot run arbitrary, un-ported Linux ELF binaries or legacy x86 machine code.
  * Smaller community and tooling ecosystem compared to standard WebAssembly (Wasm).

### Option 2: WebAssembly Runtimes (Wasmtime / WasmEdge)
* **Description:** Execute WebAssembly binaries using Wasmtime (Rust-based) or WasmEdge (C++-based) with WASI (WebAssembly System Interface).
* **Good:**
  * Broad multi-language compiler support (Rust, C/C++, Go, AssemblyScript compile to Wasm).
  * Fast instantiation times (~1–5 ms).
* **Bad:**
  * **Fuel / Metering Overhead:** Wasm instruction metering ("fuel" injection) introduces significant runtime overhead (up to 20–30% execution slowdown) as extra check instructions are inserted into JIT-compiled basic blocks.
  * **Memory Management Diversity:** Most high-level languages compiled to Wasm bundle their own tracing garbage collectors inside the Wasm linear memory, re-introducing GC latency spikes.
  * **WASI Complexity:** WASI preview interfaces introduce substantial API surface area that requires complex capability virtualization to sandbox safely.

### Option 3: Linux Containers (Docker / containerd / runC)
* **Description:** Package each guest task as a minimal OCI container image running under containerd/runC isolated by Linux namespaces, seccomp, and cgroups v2.
* **Good:**
  * Supports arbitrary standard Linux binaries and programming languages without recompilation.
* **Bad:**
  * **High Cold-Start Latency:** Container creation and namespace setup takes 150ms–500ms per task, destroying serverless micro-task performance.
  * **High Memory Overhead:** Baseline container footprint is tens of megabytes of RAM per instance.
  * **POSIX / Linux Only:** Incompatible with Windows and macOS bare-metal worker nodes (`worker-spec.md` Section 1.1).
  * **Non-Deterministic Resource Metering:** Linux cgroups only provide coarse time-slicing, not instruction-accurate execution counts.

### Option 4: MicroVMs (AWS Firecracker / Cloud-Hypervisor)
* **Description:** Launch ephemeral hardware-virtualized KVM microVMs with minimal Linux kernels for each serverless task.
* **Good:**
  * Unmatched security isolation at the hardware hypervisor boundary.
* **Bad:**
  * Cold start latency is ~30–50 ms.
  * Requires bare-metal Linux hosts with `/dev/kvm` hardware virtualization extensions (cannot run inside standard cloud VMs without nested virtualization, and cannot run on Windows/macOS workers).
  * Massive architectural overkill for lightweight stateless microservices.

---

## 5. Decision Outcome

Chosen Option: **Option 1: Solix Virtual Machine**.

Solix is purpose-built for the core requirements of Legatus: deterministic, architecture-independent instruction metering, sub-millisecond cold starts, zero garbage collection jitter via ARC, and hermetic sandbox execution. When wrapped inside ephemeral `legatus-runner` child processes managed via `reproc++` (`ADR-0008`), it delivers maximum density and rock-solid fault isolation.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Worker nodes can achieve unprecedented density, launching hundreds of concurrent tasks per node with sub-millisecond cold starts.
* Task billing and quota enforcement are 100% deterministic: running the same bytecode with the same input JSON will consume the exact same instruction count on every machine across the cluster.
* Memory is cleaned up deterministically via ARC destructors without memory spikes or garbage collection freezes.
* Native C++20 integration in `legatus-runner` ensures zero foreign function overhead and full compatibility with sanitizers (ASan, UBSan).

### 6.2 Negative Consequences (Trade-offs & Technical Debt)
* **Toolchain Specialization:** Serverless functions deployed to Legatus must be compiled to Solix bytecode artifacts (`.solix`), requiring developers to use the Solix toolchain or supported language frontends.
* **No Direct Syscall Access:** Tasks that require raw network sockets or direct filesystem manipulation must utilize explicit host FFI extensions registered by the operator.

### 6.3 Neutral Consequences
* Bytecode binaries are stored in `legatus-server`'s encrypted database and distributed to workers with SHA-256 integrity verification (`worker-wire.md` Section 3.2).

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Formally codified in [`docs/specs/worker-spec.md`](../../specs/worker-spec.md) (Sections 1.1, 5.1, and 6.1) and [`docs/specs/protocols/worker-wire.md`](../../specs/protocols/worker-wire.md).
* **Code Implementation:** Governed by `worker/src/runner/main.cpp` linking `libsolix_vm.a`.
* **Automated Verification:**
  * Catch2 unit test `worker/tests/unit/test_solix_metering.cpp` verifying that infinite loops trigger deterministic exit code `70` when $I_{\text{consumed}} > I_{\text{budget}}$.
  * Catch2 unit test `worker/tests/unit/test_solix_arc_memory.cpp` asserting that memory is completely reclaimed upon task exit without memory leaks under ASan/LeakSanitizer.
  * Benchmark test verifying context instantiation cold start $< 1.0\text{ ms}$.
