# Legatus Subsystem & Protocol Specifications Index

## Document Metadata
* **Scope:** Architectural Subsystem & Network Protocol Contracts (`docs/specs/`)
* **Status:** Final Implementation Standard
* **Governing Guideline:** [`docs/dev/guidelines/authoring-specs.md`](../dev/guidelines/authoring-specs.md) (GL-0005) & [`authoring-protocols.md`](../dev/guidelines/authoring-protocols.md) (GL-0007)
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Overview & Normative Authority

This directory houses the binding architectural specifications for all subsystems and Layer 7 network protocols within the Legatus platform. In accordance with the **Normative Authority Hierarchy (`GL-0000`)**, the specifications in this directory outrank all source code implementations, developer guides, and Architecture Decision Records (ADRs):

$$\text{Code Implementation} \prec \text{Developer Guides} \prec \text{Architecture Decisions (ADRs)} \prec \text{Subsystem Specs} \prec \text{Developer Guidelines}$$

All documents within this tree strictly adhere to the **Specification vs. Code Separation Invariant (`GL-0005`)**: contracts are defined exclusively via finite state machines, mathematical bounds, JSON schemas, and behavioral matrices without raw implementation code.

---

## 2. Core Subsystem Specifications

| Subsystem | Specification Document | Target Binaries | Host Language & Stack | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Worker Node** | [`worker-spec.md`](worker-spec.md) | `legatusd`, `legatus`, `legatus-runner` | C++20 (`reproc++`, `nng`, `IXWebSocket`, `Solix VM`) | Final (`v1.0.0-FINAL`) |
| **Sandboxed Stdlib** | [`stdlib-spec.md`](stdlib-spec.md) | `legatus-runner` | C++20 (`Solixlib`, in-memory POSIX/REST/JSON) | Final (`v1.0.0-FINAL`) |
| **Server Broker** | [`server-spec.md`](server-spec.md) | `legatus-server` | Java 21 LTS (Spring Boot 3, Loom, H2, Caffeine) | Final (`v1.0.0-FINAL`) |
| **Studio Cockpit**| [`studio-spec.md`](studio-spec.md) | `legatus-studio` | Java 21 LTS (OpenJFX 21, AtlantaFX, Ikonli) | Final (`v1.0.0-FINAL`) |

---

## 3. Layer 7 Network & Wire Protocol Specifications

| Protocol | Specification Document | Endpoints / Direction | Transport & Framing | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Worker Wire (LCTP)** | [`protocols/worker-wire.md`](protocols/worker-wire.md) | `legatusd` $\leftrightarrow$ `legatus-server` | Secure WebSocket (`wss://`, TLS 1.3, JSON) | Final (`v1.0.0-FINAL`) |
| **REST API Gateway** | [`protocols/api-endpoint.md`](protocols/api-endpoint.md) | Workers, CLI, Studio $\rightarrow$ `legatus-server` | HTTPS REST (`https://`, TLS 1.3, JSON) | Final (`v1.0.0-FINAL`) |
| **Studio Wire (LSTCP)** | [`protocols/studio-wire.md`](protocols/studio-wire.md) | `legatus-studio` $\leftrightarrow$ `legatus-server` | Secure WebSocket (`wss://`, TLS 1.3, JSON) | Final (`v1.0.0-FINAL`) |