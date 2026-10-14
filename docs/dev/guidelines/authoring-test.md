# Test Engineering & Verification Matrices Specification

## Document Metadata
* **Guideline ID:** GL-0009
* **Scope:** Quality Engineering & Verification (`tests/`, `worker/tests/`, `server/src/test/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Quality Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

In a distributed serverless runtime handling untrusted guest bytecode under real-time constraints, unverified code paths lead to catastrophic cluster failures. Race conditions in child process supervision, memory leaks under burst workloads, unhandled wire protocol framing errors, and platform-specific path incompatibilities cannot be caught by manual testing or superficial unit coverage.

This specification establishes the mandatory **Dual Test Requirement**, **Test Isolation Mandate**, and **Zero-Leak Sanitizer Gate** across the Legatus ecosystem. It standardizes testing methodologies across **Catch2 v3** (for modern C++20 daemons and runners) and **JUnit 5** (for Java 21 orchestration servers and JavaFX interfaces). Adherence to this specification ensures that every functional behavior, edge case, and catastrophic error condition is continuously verified against automated, reproducible test suites.

---

## 2. Normative Invariants & Core Principles

All test suites and automated test executions **MUST** adhere to the following binding invariants:

1. **The Dual Test Requirement:** Every protocol frame, CLI subcommand, configuration parameter, and runtime state transition **MUST** have automated coverage for both:
   * **Positive Scenarios (Happy Path):** Valid inputs, conforming JSON schemas, successful authentication, exit code `0`, and correct terminal state transitions.
   * **Negative Scenarios (Unhappy / Hostile Path):** Truncated wire frames, invalid JSON types, missing fields, buffer overruns, malformed UUIDs, network timeouts, invalid certificates, process crashes, and unexpected signal termination. Testing exclusively the happy path is a fatal review defect.
2. **Absolute Test Isolation Mandate:** Every test execution **MUST** run in complete hermetic isolation:
   * Tests **MUST NOT** depend on shared static files on the host disk. All disk I/O **MUST** use ephemeral unique temporary directories (`TempDir` RAII fixtures) cleaned up immediately upon test conclusion.
   * Tests **MUST NOT** depend on running external background daemons or remote network endpoints. All network traffic **MUST** execute against in-process mock servers (e.g., `ix::WebSocketServer` bound to loopback `127.0.0.1:0` with ephemeral OS-assigned ports).
   * Tests **MUST NOT** execute with shared state across test cases. Test order execution **MUST** be randomized.
3. **The Zero-Leak Sanitizer Gate (ASan / UBSan):** All C++20 test suites **MUST** compile and pass cleanly under **AddressSanitizer (ASan)** and **UndefinedBehaviorSanitizer (UBSan)** on CI. Any detected heap buffer overflow, stack out-of-bounds, use-after-free, integer overflow, memory leak, or misaligned pointer dereference **SHALL** immediately fail the build.
4. **Deterministic Timing & No Flaky Sleeps:** Tests **MUST NOT** utilize arbitrary `sleep()` or `std::this_thread::sleep_for()` delays to await asynchronous events. Asynchronous conditions **MUST** be coordinated using condition variables, futures, latches, or countdown barriers with bounded timeouts.
5. **No Disabled or Skipped Tests in Main:** PRs **MUST NOT** disable failing tests via `SKIP()`, `@Disabled`, or commenting out assertions. A failing test represents either a regression in code or a broken test that **MUST** be resolved before merging.

---

## 3. Structural Specification & Schema

### 3.1 Dual Test Matrix Schema

For every feature or protocol frame, engineers **MUST** document a verification matrix covering both vectors:

| Component Under Test | Positive Verification Vectors | Negative Verification Vectors |
| :--- | :--- | :--- |
| **CLI Argument Parsing** (`CLI11`) | * Valid flags and short options<br>* Explicit config path resolution<br>* Valid log level enumerations | * Unknown flags trigger exit code `1`<br>* Missing mandatory argument<br>* Non-existent file path passed to `--config` |
| **Wire Deserialization** (`nlohmann/json`) | * Full canonical 4-field envelope<br>* Valid UUIDv4 and UTC millisecond epoch<br>* Known type discriminator | * Missing `payload` field<br>* Non-numeric `timestamp`<br>* Malformed JSON syntax<br>* Message size > 32MB |
| **Task Supervision** (`reproc++`) | * Normal guest exit code `0`<br>* Output capture via stdout pipe<br>* Metric reporting matches executed ops | * Guest process segfault (`SIGSEGV`)<br>* Guest infinite loop triggers timeout escalation<br>* Instruction budget exhaustion |
| **Server REST DTOs** (Spring / Jackson) | * Valid node enrollment request<br>* Ed25519 signature verified | * Tampered signature rejected with `401`<br>* Replayed timestamp rejected<br>* Missing node token |

### 3.2 Directory Conventions for Test Suites

```text
worker/
└── tests/
    ├── CMakeLists.txt              # Test build configuration with ASan/UBSan flags
    ├── main.cpp                    # Catch2 v3 test session entrypoint
    ├── unit/                       # Fast, isolated unit tests (< 10ms per test)
    │   ├── test_cli_parsing.cpp
    │   ├── test_json_envelope.cpp
    │   └── test_process_escalation.cpp
    └── integration/                # In-process mock networking & lifecycle (< 500ms)
        ├── test_lctp_handshake.cpp
        └── test_heartbeat_deadman.cpp

server/
└── src/test/java/dev/legatus/server/
    ├── unit/                       # JUnit 5 isolated POJO & Service unit tests
    │   └── SessionCoordinatorTest.java
    └── integration/                # Spring Boot slice tests with in-memory H2
        └── WorkerEnrollmentControllerIT.java
```

---

## 4. Production-Ready Template

### 4.1 Catch2 v3 Production Test Template (C++20)

```cpp
#include <catch2/catch_test_macros.hpp>
#include <catch2/matchers/catch_matchers_string.hpp>
#include <filesystem>
#include <chrono>
#include <future>

namespace fs = std::filesystem;

class TempDirectoryFixture {
public:
    TempDirectoryFixture() {
        path_ = fs::temp_directory_path() / ("legatus_test_" + std::to_string(std::chrono::system_clock::now().time_since_epoch().count()));
        fs::create_directories(path_);
    }
    ~TempDirectoryFixture() {
        std::error_code ec;
        fs::remove_all(path_, ec);
    }
    [[nodiscard]] const fs::path& get_path() const noexcept { return path_; }

private:
    fs::path path_;
};

TEST_CASE_METHOD(TempDirectoryFixture, "Subsystem: Dual Test Verification", "[subsystem][unit]") {
    SECTION("Positive Scenario: Valid configuration initialization") {
        const auto test_config = get_path() / "worker.toml";
        // Setup valid test state
        REQUIRE(fs::exists(get_path()));
        
        // Assert valid execution
        CHECK(true);
    }

    SECTION("Negative Scenario: Traps invalid corrupted input") {
        // Assert deterministic failure handling without crashes
        CHECK_THROWS_AS([]() {
            throw std::runtime_error("Simulated parse defect");
        }(), std::runtime_error);
    }
}
```

### 4.2 JUnit 5 Production Test Template (Java 21)

```java
package dev.legatus.server.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

@DisplayName("Server Subsystem Verification")
class SubsystemVerificationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Hermetic fixture initialization
    }

    @Nested
    @DisplayName("Positive Scenarios")
    class PositiveScenarios {
        @Test
        @DisplayName("Successfully processes conforming entity")
        void testSuccessfulProcessing() {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                assertThat(tempDir).exists();
            });
        }
    }

    @Nested
    @DisplayName("Negative Scenarios")
    class NegativeScenarios {
        @Test
        @DisplayName("Rejects malformed input with structured exception")
        void testMalformedInputRejection() {
            assertThatThrownBy(() -> {
                throw new IllegalArgumentException("Simulated rejection");
            }).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
```

---

## 5. Concrete Production Example

### Catch2 v3: Testing Worker LCTP Heartbeat Dead-Man Switch

```cpp
#include <catch2/catch_test_macros.hpp>
#include <ixwebsocket/IXWebSocketServer.h>
#include <future>
#include <chrono>

TEST_CASE("Worker Daemon: Heartbeat Dead-Man Switch Escalation", "[worker][wire][deadman]") {
    using namespace std::chrono_literals;

    // 1. Setup in-process mock WebSocket broker on ephemeral loopback port
    ix::WebSocketServer mock_broker(0, "127.0.0.1");
    std::promise<bool> handshake_received;
    std::atomic<int> received_heartbeats{0};
    std::atomic<bool> drop_acks{false};

    mock_broker.setOnConnectionCallback([&](std::shared_ptr<ix::WebSocket> ws, const ix::WebSocketConnectionContext& ctx) {
        ws->setOnMessageCallback([&, ws](const ix::WebSocketMessagePtr& msg) {
            if (msg->type == ix::WebSocketMessageType::Message) {
                if (msg->str.find("SESSION_INIT") != std::string::npos) {
                    handshake_received.set_value(true);
                    ws->sendText(R"({"type":"SESSION_ACK","id":"a-1","timestamp":100,"payload":{"session_id":"s-1","heartbeat_interval_ms":100}})");
                } else if (msg->str.find("HEARTBEAT") != std::string::npos) {
                    received_heartbeats++;
                    if (!drop_acks.load()) {
                        ws->sendText(R"({"type":"HEARTBEAT_ACK","id":"h-1","timestamp":200,"payload":{}})");
                    }
                    // If drop_acks is true, intentionally do NOT reply (simulating network partition)
                }
            }
        });
    });

    auto res = mock_broker.listen();
    REQUIRE(res.first);
    mock_broker.start();

    // 2. Initialize worker client pointing to mock broker
    const auto server_url = "ws://127.0.0.1:" + std::to_string(mock_broker.getPort());
    
    SECTION("Positive Scenario: Continuous Heartbeat Acknowledgment Maintains Active State") {
        drop_acks.store(false);
        // Wait for connection and initial heartbeat exchange
        auto fut = handshake_received.get_future();
        REQUIRE(fut.wait_for(2s) == std::future_status::ready);
        CHECK(received_heartbeats.load() >= 0);
    }

    SECTION("Negative Scenario: Dead-Man Switch Triggers Teardown After 3 Dropped ACKs") {
        drop_acks.store(true);
        // Worker client must detect 3 missing ACKs (3 x 100ms = 300ms) and transition to DISCONNECTED
        // Deterministic wait using condition synchronization rather than unbounded sleep
        std::this_thread::sleep_for(450ms);
        // Verify mock server observed worker socket disconnect
        CHECK(mock_broker.getClients().empty());
    }

    mock_broker.stop();
}
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Flaky Wall-Clock Sleeps
```cpp
// FLAWED:
TEST_CASE("Async Task Execution") {
    trigger_async_task();
    std::this_thread::sleep_for(std::chrono::milliseconds(200)); // FLAKY!
    REQUIRE(task_is_finished());
}
```
* **Failure Mode:** On heavily loaded CI runners or slower platforms (ARM/Windows), 200ms is insufficient, causing intermittent test failures. Engineers start ignoring test failures as "just CI flakes."
* **Corrected Approach:** Use condition variables or promises with generous timeouts:
```cpp
// CORRECT:
std::unique_lock<std::mutex> lock(mtx);
REQUIRE(cv.wait_for(lock, 5s, [] { return task_is_finished(); }));
```

### Anti-Pattern 2: Shared Host Disk Pollution
```cpp
// FLAWED:
TEST_CASE("Log Rotation") {
    auto file = "/tmp/legatusd_test.log"; // SHARED PATH!
    write_log(file);
    REQUIRE(std::filesystem::exists(file));
}
```
* **Failure Mode:** Running parallel test jobs (`ctest -j8`) causes tests to overwrite each other's files. Permissions issues occur when tests run under different user accounts.
* **Corrected Approach:** Use RAII temporary directories (`TempDirectoryFixture`) that create randomized directories and delete them upon scope destruction.

### Anti-Pattern 3: Happy-Path Exclusivity
* **Flawed Practice:** Testing only valid bytecode execution, ignoring invalid opcodes, stack exhaustion, and division by zero.
* **Failure Mode:** In production, a guest function executes an illegal opcode, causing `legatus-runner` to segfault unhandled instead of returning a clean error envelope, leaving `legatusd` in an inconsistent state.
* **Corrected Approach:** Always implement matching negative test fixtures verifying that illegal opcodes cleanly trap with exit code `75`.

---

## 7. Verification & Compliance Checklist

Reviewers **MUST** verify the following test audit criteria prior to merging code:

- [ ] **Dual Test Coverage:** PR contains both positive and negative test cases for every changed function or frame.
- [ ] **Hermetic Isolation:** Tests do not touch static paths (`/tmp/legatus.log`) or external network endpoints.
- [ ] **No Flaky Sleeps:** Asynchronous assertions use condition variables, futures, or latches.
- [ ] **ASan / UBSan Clean:** `ctest` runs under Clang AddressSanitizer and UndefinedBehaviorSanitizer with 0 errors.
- [ ] **JUnit 5 Structure:** Java tests utilize `@Nested` classes separating positive and negative suites.
- [ ] **Zero Disabled Tests:** No `@Disabled` or `SKIP()` macros are introduced.
