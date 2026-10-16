# Milestone 014: Studio Reactive Telemetry, Multicast LSTCP & Inspector Binding Overhaul

## Document Metadata
* **Milestone ID:** MS-014
* **Target Version:** `v1.10.0-studio-reactive-telemetry`
* **Status:** ACTIVE
* **Lead Architect:** @ux-lead & @client-lead
* **Target Delivery Date:** 2027-06-01
* **Related ADRs:** ADR-0002, ADR-0006, ADR-0009

---

## 1. Executive Summary & Objectives

The comprehensive audit identified severe reactive plumbing breakdowns and UI memory leaks within `legatus-studio`:
1. **The Single-Consumer LSTCP Event Registration Flaw:** `LstcpClient` declared event listeners as single `Consumer<T>` fields. When `ExecuteWorkbenchController` initialized, it called `setTaskLifecycleListener(...)`, **overwriting and killing `TelemetryService`'s listener** cluster-wide! As soon as an operator visited Execute Workbench, task updates froze across the entire application.
2. **Static Inspector Labels in Fleet Topology:** Selecting a worker populated inspector labels (`selectedCpuLabel`, `selectedRamLabel`, `selectedLeaseLabel`) using `.setText(...)` inside the selection callback only. Because labels were never bound to the worker's Observable properties, live heartbeats arriving from the worker never updated the inspector card unless the row was clicked again.
3. **Accumulated Listener Memory Leak in Task Explorer:** Selecting a task attached listeners to `task.statusProperty()`, `task.executionWallTimeMsProperty()`, and `task.instructionsConsumedProperty()` without unbinding prior listeners. Navigating tasks rapidly accumulated hundreds of orphaned callbacks, leaking memory and causing UI stutter.
4. **Unbounded Console Lines UI Heap Leak:** `DrawerViewModel` forwarded log lines from `logStreamBuffer` to `consoleLines` indefinitely. Unlike `LogStreamBuffer` which caps at 10,000 entries, `consoleLines` grew without limit, leaking hundreds of megabytes of JVM heap on the JavaFX UI thread.
5. **Duration Inversion & Progress Bar Logic:** Task durations bounced between 1.6s and 1.8s due to missing timestamps, and reset to 0ms upon abort. Duration and instruction progress bars were displayed even when limits were set to infinity.
6. **Suppressed REST Failure Alerts:** When worker deletion or lease revocation returned HTTP 500, `FleetViewModel` only set an internal string that was not bound to any view component, giving the operator the false impression of an unresponsive button.

This milestone rebuilds the Studio reactive telemetry pipeline, implements multicast event subscription, binds all inspector cards to observable properties, and caps UI log memory.

---

## 2. Scope & Subsystems Affected

* **`studio/` (`legatus-studio` - Java 21 LTS / JavaFX 21 / AtlantaFX PrimerDark):**
  * `transport/LstcpClient.java`: Convert all listener fields to multicast `CopyOnWriteArrayList<Consumer<T>>` with `add*Listener` and `remove*Listener` methods.
  * `service/TelemetryService.java`: Consume multicast frames safely; calculate live task durations using `started_at` epoch timestamps; smoothly decrement worker lease countdowns.
  * `viewmodel/DrawerViewModel.java`: Bound `consoleLines` to 10,000 entries with FIFO eviction matching `LogStreamBuffer`.
  * `controllers/FleetTopologyController.java`: Bind inspector card labels dynamically to `selectedWorker` observable properties; display AtlantaFX modal alert on REST errors.
  * `controllers/TaskExplorerController.java`: Unbind previous task listeners before binding a new selection; hide progress bars when budget/duration is infinite; stream STDOUT/STDERR chunks live to inspector console.
  * `viewmodel/FleetViewModel.java` & `TasksViewModel.java`: Expose reactive error notification events for UI dialog display.

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [ ] `[P0]` **LstcpClient Multicast Event Registration:** Replace single `Consumer<T>` fields in `LstcpClient` with `CopyOnWriteArrayList<Consumer<T>>`. Update all callers (`TelemetryService`, `ExecuteWorkbenchController`, `TaskExplorerController`) to use `addTaskLifecycleListener` and `addClusterMetricsListener`, eliminating event listener overwrites.
- [ ] `[P0]` **Dynamic Inspector Property Bindings in Fleet Topology:** Bind `selectedCpuLabel.textProperty()`, `selectedRamLabel.textProperty()`, and `selectedLeaseLabel.textProperty()` directly to `selectedWorker.cpuLoadPctProperty()`, `memoryUsedBytesProperty()`, and `leaseRemainingSecondsProperty()`, ensuring inspector cards update in real time with incoming heartbeats.
- [ ] `[P0]` **Task Explorer Listener Cleanup & Leak Elimination:** In `TaskExplorerController.inspectTask()`, track active `ChangeListener` references for the currently selected task and explicitly call `.removeListener(...)` before binding to a newly selected task.
- [ ] `[P0]` **DrawerViewModel Bounded Memory Eviction:** In `DrawerViewModel`, enforce a strict 10,000-line ceiling on `consoleLines`. When size exceeds 10,000, remove oldest elements in batch (`consoleLines.remove(0, excess)`), preventing JavaFX UI heap leaks.

### 3.2 P1: High Priority (Milestone Core)
- [ ] `[P1]` **Live Task Duration & Infinite Budget Progress Meter:** Compute live duration for running tasks from `started_at` received over LSTCP. If `instruction_budget` or `timeout_ms` is 0 or unlimited, hide the progress bar completely; otherwise, display accurate fractional completion percentage.
- [ ] `[P1]` **Live Lease Remaining Decrementer:** Implement a 1-second pulse in `TelemetryService` that decrements `leaseRemainingSeconds` for all active workers continuously, updating table rows and inspector cards smoothly.
- [ ] `[P1]` **Live STDOUT/STDERR Stream in Task Explorer Inspector:** Wire incoming `LOG_CHUNK` LSTCP frames directly to the embedded terminal area in the Task Live Inspector card, enabling real-time inspection of active task outputs.
- [ ] `[P1]` **User-Facing REST Failure Alert Dialogs:** In `FleetTopologyController` and `TasksViewModel`, display an AtlantaFX modal error dialog whenever a REST action (drain, revoke, delete, abort) fails, presenting the server error message to the operator.

### 3.3 P2: Medium Priority (Ergonomics & Hardening)
- [ ] `[P2]` **Exponential Jitter on LSTCP Reconnection:** Update `LstcpClient` reconnection loop to use randomized exponential backoff (1s, 2s, 4s, 8s, up to 30s max with $\pm 20\%$ jitter) to prevent thundering herd storms on server restart.
- [ ] `[P2]` **Observable List Concurrency Guard:** Wrap background model reads from `CompletableFuture` pipelines in read locks or defensive copies to eliminate transient `ConcurrentModificationException` during high-churn bursts.

---

## 4. Test Strategy Overhaul & Verification Matrix

Previously, Studio tests mocked services with instant `CompletableFuture.completedFuture(...)` and never tested real FXML bindings or multi-controller listener behavior. This milestone introduces real multi-subscriber and TestFX UI binding tests:

### 4.1 Test Refactoring & New Test Deliverables

| Component Under Test | Flawed Old Test Practice | Refactored / New Test Suite | Verification Scenario |
| :--- | :--- | :--- | :--- |
| **LstcpClient Multicast** | Single listener set and called in isolation | `LstcpClientMulticastTest.java` | Registers 3 independent consumers (`TelemetryService`, `ExecuteController`, `TaskExplorer`). Fires `TASK_LIFECYCLE` frame. Asserts all 3 consumers receive the frame without drop. |
| **Fleet Inspector Dynamic Binding** | Tested static `.setText()` once during mock click | `FleetTopologyBindingTest.java` | Selects worker row. Modifies worker's `cpuLoadPctProperty` and `memoryUsedBytesProperty`. Asserts inspector labels update automatically without row re-selection. |
| **Task Explorer Listener Cleanup** | Row selection tested once; leaks unobserved | `TaskExplorerListenerLeakTest.java` | Simulates selecting 100 different tasks consecutively. Verifies listener count on each task model returns to 0 when deselected, confirming zero memory leaks. |
| **Drawer Console Bounded Heap** | Appended 10 lines in test; never verified ceiling | `DrawerViewModelMemoryTest.java` | Streams 50,000 log lines through `DrawerViewModel`. Asserts `consoleLines.size()` never exceeds 10,000, and oldest lines are evicted in FIFO order. |
| **REST Error Alert Display** | Unbound status string verified; error modal untested | `FleetActionAlertTest.java` | Mocks HTTP 500 error on worker deletion. Verifies error alert modal is triggered and displays backend error reason. |

---

## 5. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Studio Test Suite** | 100% pass across all unit and binding tests | `./mvnw test` in `studio/` | [ ] Pending |
| **Zero UI Memory Leaks** | Heap remains constant across 10,000 log lines and 500 task inspections | VisualVM / JConsole profile | [ ] Pending |
| **Multi-Subscriber Integrity** | Navigating to Execute Workbench never freezes TelemetryService | `LstcpClientMulticastTest` | [ ] Pending |
| **Living Specs Updated** | `studio-spec.md` updated with multicast and binding contracts | Documentation audit | [ ] Pending |

---

## 6. Risk Assessment & Mitigation Strategies

* **Risk 1: JavaFX Thread Violations during High-Frequency Telemetry:** Updating thousands of UI labels simultaneously at 60Hz can cause UI stutter or `CalledNotOnFxThread` exceptions.
  * *Mitigation:* `TelemetryCoalescer` batches metric updates into 16ms animation frames and applies all mutations via `FxAsync.runOnFxThread()`.
* **Risk 2: Stale Bindings on Worker Disconnect:** If a worker is deleted while selected in the inspector, bound properties could reference garbage-collected models.
  * *Mitigation:* Clear inspector bindings and display empty state placeholder whenever `selectedWorker` is removed from `allWorkers`.
