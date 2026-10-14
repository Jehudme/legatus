# ADR-0002: Adopt OpenJFX 21 (JavaFX) & AtlantaFX for Legatus Studio Desktop Cockpit

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @studio-lead, @ux-lead
* **Date:** 2026-10-14
* **Technical Story:** Issue #106 / PR #109
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

Operators of the Legatus distributed compute platform require an interactive desktop cockpit (`legatus-studio`) to monitor real-time cluster health, inspect active worker fleet topologies, review and attest quarantined worker enrollment requests, deploy Solix bytecode artifacts, and stream execution `stdout`/`stderr` logs in real time (`docs/specs/studio-spec.md`).

Historically, administrative dashboards in cloud platforms have defaulted to browser-based web applications or Electron-based desktop wrappers. However:
1. **Telemetry Streaming Overhead & UI Freezes:** Legatus clusters generate high-frequency telemetry events (worker state changes, capacity fluctuations, instruction execution ticks). In JavaScript/Web engines, rapid DOM mutations trigger severe layout thrashing, garbage collection pauses, and main-thread freezes.
2. **Excessive Resource Footprint:** Electron applications bundle full Chromium and Node.js runtimes, frequently demanding $500\text{ MB} - 1.5\text{ GB}$ of RAM solely to display operational metrics. Operators running administrative cockpits on local workstations or edge maintenance laptops cannot tolerate this overhead.
3. **OS Security & Keyring Integration:** Secure storage of administrative EdDSA JWTs and cluster profile secrets requires direct integration with native OS credential stores (`libsecret` on Linux, `Keychain` on macOS, and `DPAPI` on Windows), which is fragile in browser sandboxes.

We need a high-performance, cross-platform native desktop UI framework capable of rendering telemetry at 60 Hz with minimal resource utilization and zero web browser bloat.

---

## 2. Decision Drivers (Forces)

1. **60Hz Telemetry Rendering & UI Thread Safety:** The UI toolkit must maintain a strict 60 FPS ($16.6\text{ ms}$) rendering budget even under burst cluster activity, using hardware-accelerated scene graphs and thread-safe batching mechanisms (`Platform.runLater()`).
2. **Bounded Desktop Resource Consumption:** The application must operate within a lean memory budget (< 150 MB baseline RAM) without spawning Chromium rendering subprocesses.
3. **Cross-Platform Native Distribution:** Must build self-contained native installers (`.deb`, `.rpm`, `.dmg`, `.msi`) bundling an optimized runtime image via `jpackage`, requiring zero pre-installed Java runtimes on operator machines.
4. **Shared Type Semantics with Backend:** Must natively consume Java 21 `record` types and Jackson annotations defined in `server`, eliminating redundant API schema definitions.
5. **Modern Enterprise Design System:** Must support high-contrast dark/light themes tailored for operational NOCs (Network Operations Centers) out of the box.

---

## 3. Considered Options

* **Option 1:** OpenJFX 21 (JavaFX) + AtlantaFX Theme + Project Loom Virtual Threads + `jpackage`.
* **Option 2:** Electron (Node.js + Chromium + TypeScript + React).
* **Option 3:** Tauri (Rust core + Webview OS renderer + React / Svelte).
* **Option 4:** C++20 / Qt 6 (Widgets or QML).

---

## 4. Evaluation of Considered Options

### Option 1: OpenJFX 21 & AtlantaFX
* **Description:** Build the desktop cockpit on OpenJFX 21 using modern JavaFX controls styled with the AtlantaFX CSS design system (`PrimerDark` / `NordDark`). All background HTTP/2 REST calls and WebSocket streaming execute on Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), with UI mutations coalesced and pushed to the JavaFX Application Thread via `Platform.runLater()`. Packaged natively via `jpackage`.
* **Good:**
  * **Deterministic 60Hz Telemetry Coalescing:** Lock-free ring buffers coalesce incoming telemetry spikes into 16.6ms UI property updates, preventing UI thread starvation.
  * **Memory Efficiency:** Typically operates between 90 MB and 160 MB of heap/resident RAM, a fraction of Electron’s memory footprint.
  * **100% Code & Model Sharing:** Directly shares Java 21 `record` DTOs, Jackson serializers, and cryptographic validation routines with `legatus-server`.
  * **Modern Aesthetics:** AtlantaFX delivers polished, responsive dark-mode styling with zero custom CSS boilerplate.
  * **Zero External Dependencies:** Built-in `java.net.http.HttpClient` handles TLS 1.3 WebSockets and HTTP/2 requests natively without third-party libraries.
* **Bad:**
  * JavaFX scene graph requires strict discipline regarding thread confinement; accidentally performing I/O on the JavaFX Application Thread freezes the UI (mitigated by Project Loom virtual thread offloading).
  * Packaging requires `jpackage` / `Moditect` build setup in GitHub Actions to cross-compile OS-specific native bundles.

### Option 2: Electron (Chromium + Node.js)
* **Description:** Wrap a React/Tailwind web application in an Electron desktop shell.
* **Good:**
  * Massive frontend ecosystem, rich component libraries, and rapid UI prototyping.
* **Bad:**
  * Enormous binary size (> 120 MB installer) and unacceptable memory footprint ($600\text{ MB} - 1.5\text{ GB}$).
  * Severe layout thrashing and garbage collection latency spikes when processing high-frequency WebSocket streams (> 50 msgs/sec).
  * Requires maintaining a completely separate TypeScript codebase and build toolchain (Node, npm, Vite, Webpack).

### Option 3: Tauri (Rust + Webview)
* **Description:** Rust-based desktop shell that uses the operating system's native Webview (WebKitGTK on Linux, WebKit on macOS, WebView2 on Windows) to render a web application.
* **Good:**
  * Lightweight binary size and lower RAM usage than Electron (~60–100 MB).
* **Bad:**
  * OS Webview fragmentation: Inconsistent CSS and rendering behavior between WebKitGTK (Linux) and WebView2 (Windows).
  * Linux WebKitGTK performance lags behind Chromium when handling dense SVG/canvas telemetry graphs.
  * Polyglot impedance mismatch: Requires maintaining Rust IPC bridges and TypeScript UI components alongside Java server code.

### Option 4: C++20 / Qt 6 (QML)
* **Description:** Native desktop application authored in modern C++20 using Qt 6 QML.
* **Good:**
  * Outstanding hardware acceleration, raw CPU performance, and minimal memory usage.
* **Bad:**
  * Restrictive commercial licensing (GPL v3 or expensive commercial licenses).
  * High development friction for form-heavy administrative screens compared to JavaFX or web frontends.
  * Incompatible with the Java 21 domain model and shared tooling.

---

## 5. Decision Outcome

Chosen Option: **Option 1: OpenJFX 21 (JavaFX) & AtlantaFX**.

OpenJFX 21 provides the optimal architecture for Legatus Studio: native desktop performance, smooth 60 FPS telemetry rendering via hardware-accelerated scene graphs, lean memory consumption, and seamless domain record sharing with the Java 21 server backend.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Legatus Studio executes as a responsive native desktop application with sub-second launch times and low memory consumption (< 150 MB).
* High-frequency telemetry streams (`EVENT_CLUSTER_METRIC_TICK`, `EVENT_TASK_LOG_CHUNK`) are buffered in memory and batched to the scene graph at 60 Hz without UI stutter.
* Single-binary native installers (`.deb`, `.rpm`, `.dmg`, `.msi`) are generated deterministically in CI via `jpackage`, bundling a trimmed JRE that requires no pre-installed dependencies on client workstations.
* Operators can visually attest quarantined nodes using the 3-word visual confirmation modal with complete cryptographic fidelity (`Tiger-Galaxy-77`).

### 6.2 Negative Consequences (Trade-offs & Technical Debt)
* **Thread Confinement Discipline:** Developers must strictly isolate all network and disk I/O on Project Loom Virtual Threads; touching UI nodes from background threads throws `IllegalStateException`.
* **Platform Packaging Complexity:** Native installer generation requires dedicated GitHub Actions runners for Linux, macOS, and Windows to run `jpackage`.

### 6.3 Neutral Consequences
* User preferences and connection profiles are stored in standard OS configuration directories (`~/.config/legatus/studio.json` on Linux, `%APPDATA%\Legatus` on Windows) with credentials encrypted via OS keychain APIs.

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Formally codified in [`docs/specs/studio-spec.md`](../../specs/studio-spec.md) and [`docs/specs/protocols/studio-wire.md`](../../specs/protocols/studio-wire.md).
* **Code Implementation:** Governed by `studio/pom.xml` and package structure `com.legatus.studio.*`.
* **Automated Verification:**
  * UI automated tests executed headlessly via `TestFX` and OpenJFX Monocle in CI (`pipeline-verify.yml`).
  * 60Hz frame coalescing verified via `TelemetryCoalescerTest.java` (asserting batch dispatch intervals $\ge 16.6\text{ ms}$).
  * Log viewer memory ceilings verified via `TaskLogRingBufferTest.java` (asserting strict 10,000-line / 8 MB eviction).
