# Polyglot Language Conventions & Invariants Specification

## Document Metadata
* **Guideline ID:** GL-0011
* **Scope:** Universal Codebase (`worker/` [C++20], `server/` & `studio/` [Java 21], `guest/` [Solix])
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Backend Engineers, Runtime Engineers, Compiler Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

The Legatus platform is inherently polyglot, binding together native systems code, JVM orchestration services, and a deterministic guest execution engine:
1. **C++20:** The performance-critical systems layer (`legatusd`, `legatus-runner`, `legatus`), enforcing strict memory safety, non-blocking IPC, and determinism.
2. **Java 21:** The high-concurrency orchestration broker (`server`) and desktop administrative interface (`studio`), managing distributed state and WebSocket client pools.
3. **Solix:** The upstream deterministic guest execution engine, running metered guest tasks within sandboxed memory arenas under Automatic Reference Counting (ARC).

Without rigorous, language-specific engineering invariants, polyglot repositories suffer from memory corruptions, resource leaks, thread starvation, and erratic garbage collection pauses. This specification establishes the binding idioms, memory models, concurrency architectures, and static analysis gates across all three primary languages in the Legatus monorepo.

---

## 2. Normative Invariants & Core Principles

All code authored in the Legatus repository **MUST** conform to the following binding invariants:

### 2.1 C++20 Invariants (`worker/`)
1. **Strict RAII for All Resources:** Every operating system handle, file descriptor, child process (`reproc::process`), socket, IPC context (`nng_socket`), and heap buffer **MUST** be encapsulated within an RAII type. Explicit manual resource cleanup (e.g., manual `close()`, `free()`) is strictly prohibited.
2. **Zero Naked Raw Pointers for Ownership:** Raw pointers (`T*`) **SHALL NOT** represent resource ownership. Heap allocations requiring unique ownership **MUST** use `std::unique_ptr<T>`. Shared ownership (`std::shared_ptr<T>`) **MUST NOT** be used unless multiple asynchronous owners genuinely exist. Non-owning views **MUST** use `std::span<T>` or `std::string_view`.
3. **Mandatory Zeroing of Sensitive Memory:** Memory buffers containing cryptographic secrets, tokens, or guest state **MUST** be sanitized using `explicit_bzero` (POSIX) or `SecureZeroMemory` (Windows). Standard `memset` **MUST NOT** be used.
4. **Zero-Warning Compiler Conformance:** C++ code **MUST** compile with zero warnings under `-Wall -Wextra -Wpedantic` on GCC/Clang and `/W4 /WX` on MSVC. Warnings treated as errors are mandatory.

### 2.2 Java 21 Invariants (`server/`, `studio/`)
1. **Virtual Threads for High Concurrency:** WebSocket client connections, background task pollers, and network I/O **MUST** utilize Java 21 Virtual Threads (Project Loom) via `Executors.newVirtualThreadPerTaskExecutor()`. Code **MUST NOT** exhaust platform OS thread pools with blocking socket operations.
2. **Immutable Data Carrier Records:** All DTOs, wire frame payloads, configuration models, and event messages **MUST** be defined as Java `record` types. Mutable JavaBeans with setters **SHALL NOT** be used for domain transfer objects.
3. **Sealed Class Hierarchies & Pattern Matching:** Protocol message hierarchies and internal state transitions **MUST** use `sealed` interfaces with exhaustive pattern matching `switch` expressions.
4. **Static Analysis Compliance:** Code **MUST** pass SpotBugs and Checkstyle analysis with zero high-confidence defects.

### 2.3 Solix Guest Language Invariants (`solix`)
1. **Deterministic ARC Memory Rules:** Memory reclamation is governed by Automatic Reference Counting (ARC). Objects with circular strong references will never be collected. Back-references and parent pointers **MUST** be explicitly declared as `weak`.
2. **Standard Entry Signature:** Guest programs **MUST** provide the canonical entrypoint:
   ```solix
   int32 main(char[][] args)
   ```
3. **Primitive Non-Nullability:** Primitive types (`int32`, `int64`, `float64`, `bool`) are non-nullable. Memory slots allocated in the guest arena **MUST** be initialized to `default(T)` before execution.

---

## 3. Structural Specification & Schema

### 3.1 C++20 Style & Modern Feature Matrix

| Feature / Domain | Mandatory Standard | Prohibited Anti-Pattern |
| :--- | :--- | :--- |
| **Pointers & Views** | `std::span<T>`, `std::string_view` | `const char*` with separate length, raw buffers |
| **Smart Pointers** | `std::make_unique<T>()` | `new T()`, `malloc()`, naked pointers |
| **Filesystem Operations** | `std::filesystem::path`, `<filesystem>` | POSIX `opendir` / Windows `FindFirstFile` |
| **String Formatting** | `std::format` / `spdlog::format` | `sprintf`, `printf`, `stringstream` |
| **Concepts / Constraints** | `template<typename T> requires ...` | SFINAE with `std::enable_if_t` |
| **Process Management** | `reproc::process` | Raw `fork()` / `execve()` / `CreateProcess` |

### 3.2 Java 21 Concurrency & Type Matrix

| Domain | Mandatory Standard | Prohibited Anti-Pattern |
| :--- | :--- | :--- |
| **Threading Model** | Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) | Fixed platform thread pools for blocking I/O |
| **Data Models** | `public record TaskResultRecord(...) {}` | Mutable classes with `@Data` / getters & setters |
| **Type Hierarchies** | `public sealed interface Frame permits ...` | Loose interface inheritance with `instanceof` casts |
| **Branching Logic** | Pattern matching `switch` over sealed types | Cascading `if-else` chains |
| **Time Handling** | `java.time.Instant`, `java.time.Duration` | `java.util.Date`, `System.currentTimeMillis()` for logic |

### 3.3 Solix Memory & Runtime Architecture

```
+-------------------------------------------------------------+
|               SOLIX GUEST TASK MEMORY ARENA                 |
|                                                             |
|  +---------------------------+  +------------------------+  |
|  |     Stack Arena (8MB)     |  |   Heap Arena (120MB)   |  |
|  |  - Activation frames      |  |  - Objects & Arrays    |  |
|  |  - Primitive local slots  |  |  - ARC Reference Count |  |
|  |  - Default-zeroed slots   |  |  - Weak back-pointers  |  |
|  +---------------------------+  +------------------------+  |
+-------------------------------------------------------------+
```

---

## 4. Production-Ready Templates

### 4.1 C++20 Component Template

```cpp
#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <span>
#include <filesystem>

namespace legatus::worker {

class ProcessSupervisor final {
public:
    explicit ProcessSupervisor(std::filesystem::path runner_binary_path);
    ~ProcessSupervisor() noexcept;

    // Disallow copy construction and copy assignment (Strict RAII)
    ProcessSupervisor(const ProcessSupervisor&) = delete;
    ProcessSupervisor& operator=(const ProcessSupervisor&) = delete;

    // Allow move semantics
    ProcessSupervisor(ProcessSupervisor&&) noexcept = default;
    ProcessSupervisor& operator=(ProcessSupervisor&&) noexcept = default;

    [[nodiscard]] int execute_task(std::span<const std::string> arguments);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace legatus::worker
```

### 4.2 Java 21 Record & Service Template

```java
package dev.legatus.server.broker;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public sealed interface WireFrame permits HeartbeatFrame, TaskDispatchFrame {}

public record HeartbeatFrame(
    UUID id,
    Instant timestamp,
    String workerId,
    long executedTasks
) implements WireFrame {
    public HeartbeatFrame {
        Objects.requireNonNull(id, "Frame ID cannot be null");
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        Objects.requireNonNull(workerId, "Worker ID cannot be null");
        if (executedTasks < 0) {
            throw new IllegalArgumentException("Executed tasks cannot be negative");
        }
    }
}

public record TaskDispatchFrame(
    UUID id,
    Instant timestamp,
    String taskId,
    String bytecodeUri
) implements WireFrame {}
```

### 4.3 Solix Guest Task Template

```solix
module guest.computation;

class Node {
    public int32 id;
    public weak Node parent;
    public Node child;

    public static Node create(int32 id, weak Node parent) {
        Node n = new Node();
        n.id = id;
        n.parent = parent;
        return n;
    }
}

public int32 main(char[][] args) {
    if (args.length < 1) {
        return 1;
    }
    Node root = Node.create(100, null);
    root.child = Node.create(101, root);
    return 0;
}
```

---

## 5. Concrete Production Example

### C++20: RAII Process Escalation with `reproc++`

```cpp
#include <reproc++/reproc.hpp>
#include <reproc++/drain.hpp>
#include <spdlog/spdlog.h>
#include <vector>
#include <string>
#include <chrono>

namespace legatus::worker {

class RunnerExecutionSession {
public:
    RunnerExecutionSession() = default;
    ~RunnerExecutionSession() {
        if (process_.is_running()) {
            spdlog::warn("RunnerExecutionSession destroyed while child is running; escalating kill");
            // Mandatory two-stage escalation: SIGTERM -> wait 500ms -> SIGKILL
            process_.stop({
                reproc::stop::terminate, reproc::milliseconds(500),
                reproc::stop::kill, reproc::milliseconds(100)
            });
        }
    }

    int execute(const std::vector<std::string>& cmd_args) {
        reproc::options options;
        options.stop = {
            reproc::stop::terminate, reproc::milliseconds(500),
            reproc::stop::kill, reproc::milliseconds(100)
        };

        std::error_code ec = process_.start(cmd_args, options);
        if (ec) {
            spdlog::error("Failed to spawn legatus-runner: {}", ec.message());
            return -1;
        }

        std::string stdout_buffer;
        reproc::sink::string sink(stdout_buffer);
        ec = reproc::drain(process_, sink, reproc::sink::null);
        if (ec) {
            spdlog::error("Error draining runner streams: {}", ec.message());
        }

        auto [status, wait_ec] = process_.wait(reproc::milliseconds(5000));
        return status;
    }

private:
    reproc::process process_;
};

} // namespace legatus::worker
```

### Java 21: Loom Virtual Thread Connection Dispatcher

```java
package dev.legatus.server.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskBrokerDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TaskBrokerDispatcher.class);
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public void dispatchFrame(WireFrame frame) {
        virtualExecutor.submit(() -> {
            switch (frame) {
                case HeartbeatFrame hb -> processHeartbeat(hb);
                case TaskDispatchFrame td -> processTaskDispatch(td);
            }
        });
    }

    private void processHeartbeat(HeartbeatFrame hb) {
        log.info("Received heartbeat from worker: {} at {}", hb.workerId(), hb.timestamp());
    }

    private void processTaskDispatch(TaskDispatchFrame td) {
        log.info("Dispatching task: {} with bytecode: {}", td.taskId(), td.bytecodeUri());
    }
}
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Naked Pointers & Manual Memory Management in C++
```cpp
// FLAWED:
void execute_guest() {
    char* buffer = (char*)malloc(1024 * 1024);
    FILE* fp = fopen("/tmp/task.bin", "rb");
    if (!fp) {
        return; // MEMORY LEAK! buffer is never freed.
    }
    // ...
    free(buffer);
    fclose(fp);
}
```
* **Failure Mode:** Any early return, exception, or error branch leaks heap memory and file descriptors. In compute workers processing millions of jobs, daemon memory explodes within hours, causing host OOM-kill.
* **Corrected Approach:** Use RAII wrappers:
```cpp
// CORRECT:
void execute_guest() {
    auto buffer = std::make_unique<std::array<std::byte, 1024 * 1024>>();
    std::ifstream file("/tmp/task.bin", std::ios::binary);
    if (!file) return; // Clean RAII destruction!
}
```

### Anti-Pattern 2: Pinning Platform Threads on I/O in Java 21
```java
// FLAWED:
@Bean
public Executor platformThreadPool() {
    return Executors.newFixedThreadPool(16); // Only 16 threads for thousands of websockets!
}
```
* **Failure Mode:** Blocking calls (database queries, network requests) exhaust the pool of 16 platform threads. Subsequent incoming WebSocket connections are rejected, causing broker livelock.
* **Corrected Approach:** Use `Executors.newVirtualThreadPerTaskExecutor()` which scales seamlessly to hundreds of thousands of concurrent virtual threads without thread pool exhaustion.

### Anti-Pattern 3: Circular Strong References in Solix Guest Code
```solix
// FLAWED:
class Parent {
    public Child child;
}
class Child {
    public Parent parent; // STRONG REF CYCLE! Memory leak under ARC!
}
```
* **Failure Mode:** ARC reference count never drops to zero. Child tasks leak memory until the runner's $128\text{ MB}$ virtual memory arena is exhausted, causing `TRAP_OUT_OF_MEMORY`.
* **Corrected Approach:** Declare back-references with `weak`:
```solix
// CORRECT:
class Child {
    public weak Parent parent; // Weak reference breaks the cycle.
}
```

---

## 7. Verification & Compliance Checklist

Reviewers and static analysis gates **MUST** enforce the following criteria:

- [ ] **C++ RAII Strictness:** Zero instances of `malloc`, `free`, raw `new`, or raw `delete`.
- [ ] **C++ Warnings as Errors:** Compiles cleanly with `-Wall -Wextra -Wpedantic` (GCC/Clang) and `/W4 /WX` (MSVC).
- [ ] **Secure Memory Zeroing:** Cryptographic and sensitive buffers wiped using `explicit_bzero` or `SecureZeroMemory`.
- [ ] **Java Virtual Threads:** High-concurrency tasks execute on Project Loom virtual threads.
- [ ] **Java Immutability:** DTOs are declared as Java 21 `record` types with null-checks.
- [ ] **Sealed Hierarchies:** Protocol frame types use sealed interfaces with pattern matching switches.
- [ ] **Solix ARC Safety:** Back-references in Solix classes are explicitly marked `weak`.
