# Subsystem Specification Invariants & Schema

## Document Metadata
* **Guideline ID:** GL-0005
* **Scope:** Architectural Subsystem Specifications (`docs/specs/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Protocol Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

Subsystem specifications (`docs/specs/*-spec.md`) serve as the fundamental blueprints defining the functional boundaries, invariants, interfaces, and operational contracts of Legatus software components (including `worker-spec.md`, `server-spec.md`, and `studio-spec.md`). A recurring pathology in systems engineering is the conflation of *specifications* with *code implementations*. When engineers paste raw C++ or Java source code into specifications, the specification immediately rots, drifts from the compiler, creates redundant maintenance overhead, and obscures core architectural guarantees with low-level syntax boilerplate.

This specification establishes the **Specification vs. Code Separation Invariant**. Subsystem specifications **MUST** define contracts, finite state machines, mathematical constraints, memory models, and component data flows in an implementation-agnostic manner. Specifications govern *what* guarantees a subsystem must uphold, while the compiler and version-controlled source tree govern *how* those guarantees are implemented.

---

## 2. Normative Invariants & Core Principles

All subsystem specifications within `docs/specs/` **MUST** strictly adhere to the following normative invariants:

1. **The Separation Invariant (Zero Multi-Line Implementation Code):** Subsystem specifications **MUST NOT** contain raw multi-line C++, Java, or build script implementation code (e.g., functions, loop bodies, `CMakeLists.txt` snippets, class definitions). Implementation code belongs exclusively in source repositories.
2. **Abstract Contract Representation:** Specifications **SHALL** express requirements using structural schema tables, state transition matrices, sequence diagrams, ASCII architectural topologies, mathematical equations, and formal BNF grammars.
3. **Strict Interface Boundaries:** Specifications **MUST** clearly delineate subsystem inputs, outputs, side-effects, resource limits, and error conditions without relying on implementation-specific private methods.
4. **Platform-Agnostic Behavioral Invariants:** Specifications **MUST** define system behavior across all supported host operating systems (Linux, macOS, Windows) by defining invariant contracts rather than OS-specific branching logic.
5. **Deterministic Lifecycle & Memory Invariants:** If a subsystem manages memory arenas, process lifecycles, or thread pools, the specification **MUST** formalize the bounds, ownership semantics, allocation ceilings, and teardown escalation phases mathematically.
6. **Normative Authority:** As established in GL-0000, Subsystem Specifications outrank code implementations, developer guides, and ADRs. Code that deviates from a specification is non-compliant and **MUST** be brought into alignment.

---

## 3. Structural Specification & Schema

### 3.1 What to Use Instead of Implementation Code

| Architectural Dimension | Prohibited Representation | Mandatory Normative Alternative |
| :--- | :--- | :--- |
| **Data Structures & Payloads** | Pasted C++ `struct` or Java POJO | JSON Schema (Draft 2020-12) or Tabular Field Specification |
| **Lifecycle & Protocols** | Multi-branch `switch` statement | ASCII / Mermaid Finite State Machine (FSM) Diagram |
| **Component Interactions** | Nested function calls and logic | Sequence Diagram detailing message flow and timing |
| **Resource & Quota Limits** | Hardcoded `#define` or constants | Mathematical Bounds Table with upper/lower limits |
| **Build Configurations** | 100-line `CMakeLists.txt` / POM | Dependency Matrix Table (role, library, minimum version) |
| **Execution Flow** | Procedural code blocks | Deterministic Step-by-Step Contractual Algorithm |

### 3.2 Canonical Subsystem Specification Layout

Every document in `docs/specs/` **MUST** structure its content into the following sections:

1. **Title & Metadata:** Subsystem name, semantic version, status, audience, upstream runtimes, and related specifications.
2. **System Architecture & Binary Topology:** Component hierarchy, processes, IPC mechanisms, and ASCII block diagrams.
3. **Technology Stack & Component Responsibilities:** Dependency matrix defining external libraries, versions, and functional mandates.
4. **Filesystem & Operating System Boundaries:** Path resolution rules, permissions, directory hierarchy across POSIX and Windows.
5. **Contractual State Machines & Data Flows:** Detailed FSMs and message flow contracts.
6. **Resource Ceilings & Execution Sandboxing:** Memory models, CPU instruction quotas, timeout escalation, and security boundaries.
7. **Failure Modes, Recovery Semantics & Error Codes:** Deterministic exit codes, trap signals, and recovery workflows.

---

## 4. Production-Ready Template

Engineers authoring subsystem specifications **MUST** utilize the following template:

```markdown
# Legatus [Subsystem Name] Specification

## Document Metadata
* **Subsystem:** `[subsystem-identifier]` (e.g., `worker`, `server`, `studio`)
* **Version:** `v1.0.0-FINAL`
* **Status:** Final Implementation Specification
* **Target Audience:** Systems Software Engineers, Architecture Reviewers
* **Upstream Language Runtime:** [e.g., Solix v1.0.0+ / Java 21 / C++20]
* **Related Specifications:**
  * `docs/specs/protocols/[protocol-name].md`
  * `docs/dev/decisions/XXXX-[decision].md`

---

## 1. System Architecture & Binary Topology
<!-- High-level architectural diagram and role of each binary target. NO SOURCE CODE. -->
```
+-------------------------------------------------------------------+
|                            HOST SYSTEM                            |
|  +-------------------+                      +------------------+  |
|  |     Component A   | <==================> |    Component B   |  |
|  +-------------------+      IPC Mechanism   +------------------+  |
+-------------------------------------------------------------------+
```

### 1.1 Binary Targets & Roles
* **`binary-one`**: [Primary responsibilities and operational mode]
* **`binary-two`**: [Primary responsibilities and operational mode]

## 2. Technology Stack & Component Responsibilities
<!-- Abstract dependency matrix. DO NOT paste CMakeLists.txt or pom.xml. -->

| Component Role | Selected Dependency | Minimum Version | Functional Mandate |
| :--- | :--- | :--- | :--- |
| **Process Management**| `reproc++` | `v14.2+` | Subprocess spawning and two-stage escalation. |
| **Local IPC** | `nng` | `v1.8+` | Cross-platform REP0/REQ0 message bus. |

## 3. Filesystem & Path Standards
<!-- Abstract directory resolution rules across operating systems. -->

| Directory Role | Linux / macOS Standard | Windows Standard |
| :--- | :--- | :--- |
| **Config Directory** | `/etc/legatus/` or `~/.config/legatus/` | `%APPDATA%\Legatus\` |
| **State Directory** | `/var/lib/legatus/` | `%LOCALAPPDATA%\Legatus\` |

## 4. Contractual State Machines & Lifecycle
<!-- Formal FSM. Define states, transitions, triggers, and timing invariants. -->

## 5. Resource Ceilings & Sandboxing Model
<!-- Formal memory bounds, instruction quotas, and execution sandboxing rules. -->

## 6. Failure Modes, Traps & Exit Codes
<!-- Comprehensive mapping of exit codes and failure remediation. -->

| Exit Code | Mnemonic | Trigger Condition | System Action |
| :--- | :--- | :--- | :--- |
| `0` | `EX_OK` | Normal successful completion | Output parsed; resources freed. |
| `75` | `EX_TEMPFAIL` | Instruction budget exhausted | Task trapped; error logged. |

## 7. Verification & Compliance Checklist
- [ ] Document contains zero multi-line implementation code blocks.
- [ ] All external dependencies are tabulated with explicit version constraints.
- [ ] Cross-platform behavior is defined for Linux, macOS, and Windows.
- [ ] Memory and resource bounds are expressed with mathematical upper limits.
```

---

## 5. Concrete Production Example

### Specifying Task Isolation in `legatus-runner` Without Code

The following excerpt demonstrates how process isolation and instruction metering are specified authoritatively without pasting C++ code:

```markdown
### 4.2 Guest Task Sandboxing & Instruction Metering Contract

The `legatus-runner` process isolates untrusted guest Solix bytecode execution within an ephemeral process boundary. The supervisor (`legatusd`) enforces strict resource quotas by configuring the runner environment prior to launching the child process.

#### 1. Memory Arena Bounds
* **Maximum Virtual Memory (VIRT):** $M_{\text{max}} = 134,217,728\text{ bytes}$ ($128\text{ MB}$).
* **Stack Arena Limit:** $M_{\text{stack}} = 8,388,608\text{ bytes}$ ($8\text{ MB}$).
* **Heap Arena Limit:** $M_{\text{heap}} = M_{\text{max}} - M_{\text{stack}} = 125,829,120\text{ bytes}$.
* **Violation Invariant:** Any allocation request exceeding $M_{\text{max}}$ **MUST** immediately trigger a `TRAP_OUT_OF_MEMORY` event, terminating the runner with exit code `71` (`EX_OSERR`).

#### 2. Instruction Metering & Budgeting
* **Quota Allocation:** Each task dispatch specifies an integer instruction budget $I_{\text{budget}} \in [10^3, 10^9]$.
* **Meter Verification:** The execution engine decrements an internal instruction counter on each basic block entry.
* **Exhaustion Semantics:** When remaining instructions reach $0$, the engine **MUST NOT** crash or invoke `std::abort()`. The runner **MUST**:
  1. Halt execution immediately.
  2. Flush the executed opcode telemetry to the standard output pipe.
  3. Emit terminal status `QUOTA_EXHAUSTED`.
  4. Terminate process with exit code `75` (`EX_TEMPFAIL`).

#### 3. Two-Stage Termination Escalation Matrix

```
       +-----------------------+
       |   Task Exceeds Wall   |
       |     Clock Timeout     |
       +-----------+-----------+
                   |
                   v
       +-----------------------+
       | Stage 1: Graceful Stop| ===> POSIX: SIGTERM / Windows: CTRL_BREAK_EVENT
       | (Wait Window: 500ms)  |
       +-----------+-----------+
                   |
         +---------+---------+
         | Child Exited?     |
         +---------+---------+
         | NO                | YES
         v                   v
+------------------+  +--------------------+
| Stage 2: Forced  |  | Process Reclaimed  |
| Kill (SIGKILL /  |  | Exit Recorded      |
| TerminateProcess)|  +--------------------+
+------------------+
```
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Pasting Full C++ Socket Implementation
* **Flawed Practice:** Pasting a 120-line C++ function using `IXWebSocket` with connection loops, lambdas, and mutexes into `docs/specs/worker-spec.md`.
* **Failure Mode:** When the codebase refactors from raw callbacks to an asynchronous event queue, the specification is not updated. Future engineers assume the spec is broken or abandon it entirely, rendering architectural documentation useless.
* **Corrected Approach:** Document the protocol lifecycle, connection states, and framing invariants using FSM diagrams and sequence flows. Keep the C++ code in `worker/daemon/src/transport.cpp`.

### Anti-Pattern 2: Embedding 200 Lines of `CMakeLists.txt`
* **Flawed Practice:** Inserting full CMake build logic or Maven `pom.xml` configurations into architectural specs to show dependencies.
* **Failure Mode:** Build flags, compiler warning configurations, and transient patch versions change weekly. The spec immediately rots and clutters architectural analysis with build tool noise.
* **Corrected Approach:** Tabulate dependencies in a clean, versioned "Technology Stack" table specifying the role, library name, and minimum required standard.

### Anti-Pattern 3: Vague, Non-Binding Prose Instead of Mathematical Bounds
* **Flawed Practice:** Writing: "The runner should not use too much memory and should stop if it runs too long."
* **Failure Mode:** Implementations vary wildly across developers. One developer sets a 10-second timeout; another sets 60 seconds. Compute nodes crash from unbounded heap usage because no explicit byte ceiling was mandated.
* **Corrected Approach:** Specify exact numerical thresholds, mathematical formulae, and deterministic exit codes.

---

## 7. Verification & Compliance Checklist

Authors and reviewers of documents in `docs/specs/` **MUST** verify the following audit criteria:

- [ ] **Zero Implementation Code:** No multi-line C++, Java, or build script code blocks exist in the specification.
- [ ] **Contractual Representation:** All interfaces and data exchanges are defined using JSON Schema, tables, or BNF grammar.
- [ ] **State Machine Formalism:** Component lifecycles are diagrammed using formal FSM state models.
- [ ] **Mathematical Bounds:** Memory, CPU, and timing limits are defined with exact numbers or formulae.
- [ ] **Cross-Platform Uniformity:** System behavior is defined across Linux, macOS, and Windows.
- [ ] **Error & Exit Matrix:** Deterministic exit codes and failure recovery procedures are tabulated.
- [ ] **Separation of Concerns:** Document defines *what* invariants are enforced, leaving *how* to source code.
