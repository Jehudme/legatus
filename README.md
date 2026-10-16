# Legatus: Distributed Serverless Runtime Platform

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

Legatus is an ultra-high-density distributed serverless runtime platform designed for deterministic, sandboxed execution of guest Solix bytecode workloads.

## Architecture

Legatus is organized into three core subsystems:

1. **`legatus-server` (`server/`):** Centralized orchestration broker built on Java 21 LTS and Spring Boot 4 using Project Loom Virtual Threads, managing LCTP (Worker) and LSTCP (Studio) WebSocket brokers, task scheduling, node enrollment, and cryptographic secret vaulting.
2. **`legatusd` & `legatus-runner` (`worker/`):** High-density C++20 worker daemon supervisor and execution sandbox, leveraging `reproc++` out-of-process isolation, local `nng` IPC, and an embedded Solix Virtual Machine runtime with discrete instruction budgeting and zero-disk sandboxing.
3. **`legatus-studio` (`studio/`):** Modern desktop administration cockpit built on Java 21, JavaFX 21, and AtlantaFX PrimerDark, featuring 60Hz telemetry frame coalescing, visual cryptographic node attestation, and real-time task lifecycle monitoring.

## Documentation

Full architectural specifications, developer guidelines, wire protocols, and operational guides are available in the [Documentation Portal](docs/README.md):

* **[Master Architecture & Topology](docs/architecture/overview.md)**
* **[Server Specification](docs/specs/server-spec.md)**
* **[Worker Specification](docs/specs/worker-spec.md)**
* **[Studio Cockpit Specification](docs/specs/studio-spec.md)**
* **[Worker Wire Protocol (LCTP)](docs/specs/protocols/worker-wire.md)**
* **[Studio Wire Protocol (LSTCP)](docs/specs/protocols/studio-wire.md)**
* **[Standard Library Specification](docs/specs/stdlib-spec.md)**
* **[Engineering Milestones Roadmap](docs/dev/milestones/README.md)**

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the [LICENSE](LICENSE) file for details.
