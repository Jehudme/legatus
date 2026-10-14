# Legatus Developer & Operator Guides Index

## Document Metadata
* **Scope:** Operational Manuals & Deployment Runbooks (`docs/guides/`)
* **Status:** Final Implementation Standard
* **Audience:** DevOps Engineers, Cluster Administrators, Platform Contributors, Systems Operators
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Governing Guidelines:** [`docs/dev/guidelines/authoring-docs.md`](../dev/guidelines/authoring-docs.md) (`GL-0010`), [`docs/dev/guidelines/README.md`](../dev/guidelines/README.md) (`GL-0000`)

---

## 1. Overview & Operational Manual Mission

The `docs/guides/` directory contains procedural, step-by-step user manuals, operational runbooks, and developer quickstarts for the Legatus ecosystem.

In accordance with the **Living Documentation Invariant (`GL-0010`)**:
1. Every command-line flag and environment variable documented in these guides **MUST** match the actual `CLI11` options in `worker/` and Spring properties in `server/` byte-for-byte.
2. Every code snippet, configuration block, and systemd service unit **MUST** be verified against automated build targets.
3. Guides document current production reality; unreleased features are strictly prohibited.

---

## 2. Guides Directory Manifest

| Guide Link | Subsystem Focus | Target Persona | Core Contents |
| :--- | :--- | :--- | :--- |
| **[`01-getting-started.md`](01-getting-started.md)** | Full Platform | New Developers & Evaluators | End-to-end local cluster bootstrap: compile binaries, launch server, boot worker, attest in Studio, and run a Solix task. |
| **[`02-server.md`](02-server.md)** | `server` | DevOps & Cloud Engineers | Production deployment of `legatus-server`, `server.yml` reference, TLS keystores, `LEGATUS_MASTER_KEY` bootstrap, H2 encryption, and systemd units. |
| **[`04-studio.md`](04-studio.md)** | `studio` | Cluster Operators & SREs | Operator manual for `legatus-studio`, visual 3-word quarantine attestation, bytecode deployment, task console log drawer, and shortcuts. |
| **[`05-worker.md`](05-worker.md)** | `worker` / `cli` | Systems Administrators | Worker daemon installation, `worker.toml` reference, `legatus` CLI commands (`up`, `down`, `drain`, `status`), Windows/POSIX IPC, and exit codes. |

---

## 3. Prerequisite Toolchain Matrix

To build, configure, and execute the full Legatus platform locally, host environments require:

| Component | Minimum Version | Linux Package / Tool | macOS Standard | Windows Standard |
| :--- | :--- | :--- | :--- | :--- |
| **Java Development Kit** | `OpenJDK 21 LTS` | `temurin-21-jdk` | `brew install openjdk@21` | Eclipse Temurin 21 MSI |
| **C++ Compiler** | `C++20 compliant` | `gcc 13+` or `clang 17+` | Apple Clang 15+ / Homebrew Clang | MSVC v143 (VS 2022) |
| **Build Systems** | `CMake 3.28+`, `Maven 3.9+` | `cmake`, `maven` | `brew install cmake maven` | `choco install cmake maven` |
| **Cryptographic Library** | `OpenSSL 3.0+` | `libssl-dev` / `openssl` | `brew install openssl@3` | OpenSSL v3.x Win64 |
| **IPC & Process Libraries** | Managed via CMake | `FetchContent` (`nng`, `reproc++`, `IXWebSocket`, `CLI11`) | Managed via CMake | Managed via CMake |
