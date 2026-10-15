# Changelog

All notable changes to the Legatus Worker subsystem (`legatusd`, `legatus`, `legatus-runner`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [1.0.0-worker-mvp] - 2026-10-15
### Added
* Bootstrap C++20 CMake build system with FetchContent dependencies (`solix` v1.1.1, `CLI11`, `nlohmann_json`, `spdlog`, `fmt`, `nng`, `reproc++`, `IXWebSocket`, `Catch2`).
* Define core targets `legatus-runner`, `legatusd`, `legatus`, and `legatus_common`.
* Implement cryptographic and secure zeroing utilities in `legatus::common`.

## [0.1.0] - 2026-10-14
### Documentation
* Establish subsystem specification (`docs/specs/worker-spec.md`) and LCTP wire protocol (`docs/specs/protocols/worker-wire.md`).
* Document cross-platform defenses: anonymous `stdin` envelope streaming, `CREATE_NEW_PROCESS_GROUP`, and Windows Named Pipe SDDL DACL (`D:(A;;GRGW;;;AU)`).
* Formally record architectural decisions in `ADR-0004` (Solix VM), `ADR-0006` (Jittered Heartbeats), `ADR-0007` (`nng` IPC), and `ADR-0008` (`reproc++` Isolation).
* Publish worker operations guide in `docs/guides/05-worker.md`.
