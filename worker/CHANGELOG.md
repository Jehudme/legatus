# Changelog

All notable changes to the Legatus Worker subsystem (`legatusd`, `legatus`, `legatus-runner`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [1.0.0](https://github.com/Jehudme/legatus/compare/worker-v0.2.1...worker-v1.0.0) (2026-10-17)


### ⚠ BREAKING CHANGES

* Promotion to v1.0.0 General Availability with unified multi-platform CI/CD and release publishing.

### Features

* promote Legatus to v1.0.0 General Availability ([7b4ef2c](https://github.com/Jehudme/legatus/commit/7b4ef2c4389d7fd54dcf2bac3777d153d352b209))


### Bug Fixes

* **ci:** exclude unused upstream solix targets and build explicit legatus binaries on Windows ([39b5931](https://github.com/Jehudme/legatus/commit/39b5931fb50ec97edc7368a40cd4e607a98609f1))
* resolve process drain pipe bounds, SecretZeroizer lifetime, and multiplatform CI matrix ([30eb70e](https://github.com/Jehudme/legatus/commit/30eb70e37ecea0d7003c5139f9e0d3559a3e3ee1))
* **worker:** adopt canonical reproc::drain and enforce OpenSSL on Windows CI ([7c18b92](https://github.com/Jehudme/legatus/commit/7c18b922e9d66cbf3f4888ca95f6b17bba3c4546))
* **worker:** initialize Winsock for in-process servers and handle Windows task abort termination ([240acb4](https://github.com/Jehudme/legatus/commit/240acb443b296333b0412e9909b1cecf02d67eba))
* **worker:** link ws2_32 and crypt32 for networking on Windows MSVC ([4b08277](https://github.com/Jehudme/legatus/commit/4b082773221a90d143fbed3dd0258d3a44ef0237))
* **worker:** resolve cross-platform MSVC intrinsics, stream pipe draining, and signal safety ([deaa77d](https://github.com/Jehudme/legatus/commit/deaa77dbe8e7ca4abd082c086d54719f52e6cbad))
* **worker:** resolve SecretZeroizer lifetime use-after-free and Windows ERROR macro collision ([4079d3a](https://github.com/Jehudme/legatus/commit/4079d3a22258d6a5edeeb74c0347ff981743cbf3))

## [0.2.1](https://github.com/Jehudme/legatus/compare/worker-v0.2.0...worker-v0.2.1) (2026-10-16)


### Bug Fixes

* **studio:** implement Milestone 014 reactive telemetry, multicast LSTCP, and inspector bindings (MS-014) ([8bb1efe](https://github.com/Jehudme/legatus/commit/8bb1efee9b3e3a4e9173459ebc7086e154892bac))
* **worker:** implement Milestone 013 process isolation, signal lifecycle, and protocol pipe separation (MS-013) ([7231224](https://github.com/Jehudme/legatus/commit/7231224193f4c588b4c2f33e5ab9f8e0d6d7a387))

## [0.2.0](https://github.com/Jehudme/legatus/compare/worker-v0.1.0...worker-v0.2.0) (2026-10-16)


### Features

* **all:** resolve telemetry live updates, audit verification, worker fleet, and vault contracts ([3f04ac6](https://github.com/Jehudme/legatus/commit/3f04ac6efa0666e130ebba548e8691e66b8ad763))
* **cockpit:** implement Milestone 011 live sync, telemetry overhaul, and UX improvements ([1dff83b](https://github.com/Jehudme/legatus/commit/1dff83ba3823c3a34c04913cb5df147a3094711d))
* **control-plane:** implement server-centric worker control plane and dynamic configuration ([c7ce658](https://github.com/Jehudme/legatus/commit/c7ce6582e321a3d2bc00b2938d2fcb26f390943e))
* **platform:** implement MS-005 protocol parity, streaming, and workflow DAG engine ([3c17611](https://github.com/Jehudme/legatus/commit/3c17611449bf49087fff50c9410278d68c219154))
* **stdlib:** add Solix build manifest for worker stdlib ([0d97074](https://github.com/Jehudme/legatus/commit/0d97074853bd56eee542a5a17cf270c7f1faf77b))
* **studio:** add execution workbench, profile engine, and catalog metadata inspector (MS-009) ([1980951](https://github.com/Jehudme/legatus/commit/19809512f0103a22408ef12af644b5dc189e2a70))
* **tasks:** dispatch bytecode payload, broadcast task lifecycle, and sync Studio task explorer ([5826aba](https://github.com/Jehudme/legatus/commit/5826aba8e8a2ec889f3d3842c09dcf22c094dd08))
* **worker:** configure CMake precompiled headers and CPack installer rules ([fabf9ff](https://github.com/Jehudme/legatus/commit/fabf9ff8a47590d9beffe46c0409c34298256445))
* **worker:** implement CLI11 operator CLI, legatusd daemon loop, and spdlog rotating sinks ([c3cc85e](https://github.com/Jehudme/legatus/commit/c3cc85e3dacac6704a4326961c3a533069166630))
* **worker:** implement cross-platform nng REQ0/REP0 local IPC bus with 6 formal actions ([19698c3](https://github.com/Jehudme/legatus/commit/19698c340d0ba88bddebc43d524753be30b1b97f))
* **worker:** implement cross-platform paths, host telemetry collector, and bytecode deduplication cache ([9071faa](https://github.com/Jehudme/legatus/commit/9071faacd336b02c8929076799dd534c2e46b8fa))
* **worker:** implement LCTP WebSocket client with jittered heartbeats and dead-man switch ([ebd31eb](https://github.com/Jehudme/legatus/commit/ebd31eb0974dfd03c0427b2b281d67088f5d0043))
* **worker:** implement legatus up daemon launcher subcommand ([8e9101e](https://github.com/Jehudme/legatus/commit/8e9101e851563a89193f6c1f4426631e523dc907))
* **worker:** implement legatus-runner task engine and secret zeroization ([bda8e6e](https://github.com/Jehudme/legatus/commit/bda8e6ea9ff08a9ecc02f8ae0b90c4a546709de6))
* **worker:** implement MS-006 sandboxed standard library and pure in-memory runtime ([84383bf](https://github.com/Jehudme/legatus/commit/84383bf97f69a875153d766385fe0a99efaf12e1))
* **worker:** implement quarantine node enrollment, ed25519 visual attestation, and worker.toml config ([61b2c66](https://github.com/Jehudme/legatus/commit/61b2c66f64df9970c0d909d4609f6e010be591fa))
* **worker:** implement reproc++ child process supervisor with two-stage kill escalation ([f14c60c](https://github.com/Jehudme/legatus/commit/f14c60c4f2edd7a08d1cc64fe3ad10b7af0a0cbd))
* **worker:** scaffold c++20 worker build system and project structure ([a3aee30](https://github.com/Jehudme/legatus/commit/a3aee3046b1586825f72f13f04413591c95befee))

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
