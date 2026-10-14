# Legatus Worker Subsystem

## Component Metadata
* **Component:** `worker` (`legatusd`, `legatus`, `legatus-runner`)
* **Language & Standard:** C++20 (GCC 13+ / Clang 17+ / MSVC 2022)
* **Build System:** CMake 3.28+ (`worker/CMakeLists.txt`)
* **Primary Libraries:** `reproc++` (process isolation), `nng` (IPC), `IXWebSocket` (TLS 1.3 client), `CLI11` (CLI parser), `libsolix_vm` (guest VM)
* **Normative Specification:** [`docs/specs/worker-spec.md`](../docs/specs/worker-spec.md)
* **Wire Protocol:** [`docs/specs/protocols/worker-wire.md`](../docs/specs/protocols/worker-wire.md)
* **Operations Guide:** [`docs/guides/05-worker.md`](../docs/guides/05-worker.md)

---

## 1. Overview & Architecture

The `worker/` subsystem delivers the high-density distributed compute plane of the Legatus platform. It comprises three native C++20 deliverables:

1. **`legatusd` (Supervisor Daemon):** Long-running background service that maintains an outbound full-duplex TLS 1.3 WebSocket connection (`LCTP`) to `legatus-server`. It manages compute capacity, tracks active runners, and listens for local operator administrative commands over local IPC (`nng`).
2. **`legatus-runner` (Ephemeral Guest Sandbox):** Ephemeral child process invoked by `legatusd` for every scheduled task. It reads the execution envelope via anonymous `stdin` (bypassing Windows command-line length limits), embeds the upstream deterministic **Solix Virtual Machine** (`libsolix_vm.a`), meters discrete instruction execution cycles ($I_{\text{consumed}} \le I_{\text{budget}}$), and reclaims heap memory via Automatic Reference Counting (ARC).
3. **`legatus` (Operator CLI):** Interactive command-line utility used by node operators to inspect and manage the local daemon (`legatus up`, `down`, `drain`, `status`, `log level`, `tasks`). It communicates strictly over local IPC (`ipc://`), requiring zero direct network access to the remote server broker.

---

## 2. Directory Layout

```text
worker/
├── CMakeLists.txt             # Root CMake build configuration
├── CHANGELOG.md               # Release Please component changelog
├── README.md                  # Subsystem overview (This file)
├── include/                   # Public C++20 headers
│   └── legatus/               # Domain interfaces, FSMs, IPC framing, LCTP envelopes
├── src/                       # Subsystem source implementations
│   ├── cli/                   # legatus interactive CLI (CLI11 bindings)
│   ├── daemon/                # legatusd supervisor daemon (IXWebSocket, nng REP0)
│   ├── runner/                # legatus-runner sandbox (Solix VM integration, reproc++)
│   └── common/                # Shared logging, crypto (OpenSSL Ed25519), time utils
└── tests/                     # Automated Catch2 v3 test fixtures
    ├── unit/                  # Fast deterministic unit tests (FSM, IPC framing, jitter)
    └── integration/           # Multi-process sandbox tests (stdin injection, kill escalation)
```

---

## 3. Building & Testing

### 3.1 Configure & Build via CMake
```bash
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel $(nproc)
```

### 3.2 Running the Test Suite (Catch2 v3)
In accordance with [`docs/dev/guidelines/authoring-test.md`](../docs/dev/guidelines/authoring-test.md) (`GL-0009`), tests are executed via `ctest`:
```bash
ctest --test-dir build --output-on-failure
```

### 3.3 Building with Sanitizers (ASan / UBSan)
```bash
cmake -B build-asan -S . -DCMAKE_BUILD_TYPE=Debug -DLEGATUS_ENABLE_SANITIZERS=ON
cmake --build build-asan --parallel $(nproc)
ctest --test-dir build-asan --output-on-failure
```
