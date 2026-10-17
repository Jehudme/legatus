# Changelog

All notable changes to the Legatus Studio subsystem (`legatus-studio`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [1.0.0](https://github.com/Jehudme/legatus/compare/studio-v0.3.0...studio-v1.0.0) (2026-10-17)


### ⚠ BREAKING CHANGES

* Promotion to v1.0.0 General Availability with unified multi-platform CI/CD and release publishing.

### Features

* promote Legatus to v1.0.0 General Availability ([7b4ef2c](https://github.com/Jehudme/legatus/commit/7b4ef2c4389d7fd54dcf2bac3777d153d352b209))


### Bug Fixes

* resolve process drain pipe bounds, SecretZeroizer lifetime, and multiplatform CI matrix ([30eb70e](https://github.com/Jehudme/legatus/commit/30eb70e37ecea0d7003c5139f9e0d3559a3e3ee1))
* **studio:** completely remove artificial bytecode magic header validation ([cf0ea96](https://github.com/Jehudme/legatus/commit/cf0ea968aa0f1045e4441aa3a33cefaa56d46e1b))
* **studio:** persist uploaded functions in catalog database to survive app restarts ([21e8e65](https://github.com/Jehudme/legatus/commit/21e8e6522c8edc0961cad1cdfb535c4cce92a92e))

## [0.3.0](https://github.com/Jehudme/legatus/compare/studio-v0.2.0...studio-v0.3.0) (2026-10-16)


### Features

* **e2e:** implement Milestone 016 military-grade cluster verification suite (MS-016) ([39118aa](https://github.com/Jehudme/legatus/commit/39118aa154828bd292e26543dc823b8c57b46cb4))
* **studio:** implement Milestone 015 modal dialogs, function inspector, and profile cockpit (MS-015) ([12e9f0a](https://github.com/Jehudme/legatus/commit/12e9f0ab3a08a250e0122cf19fd0bceae0eabfb0))


### Bug Fixes

* **studio:** implement Milestone 014 reactive telemetry, multicast LSTCP, and inspector bindings (MS-014) ([8bb1efe](https://github.com/Jehudme/legatus/commit/8bb1efee9b3e3a4e9173459ebc7086e154892bac))

## [0.2.0](https://github.com/Jehudme/legatus/compare/studio-v0.1.0...studio-v0.2.0) (2026-10-16)


### Features

* **all:** resolve telemetry live updates, audit verification, worker fleet, and vault contracts ([3f04ac6](https://github.com/Jehudme/legatus/commit/3f04ac6efa0666e130ebba548e8691e66b8ad763))
* **cockpit:** implement Milestone 011 live sync, telemetry overhaul, and UX improvements ([1dff83b](https://github.com/Jehudme/legatus/commit/1dff83ba3823c3a34c04913cb5df147a3094711d))
* **platform:** implement MS-005 protocol parity, streaming, and workflow DAG engine ([3c17611](https://github.com/Jehudme/legatus/commit/3c17611449bf49087fff50c9410278d68c219154))
* **security:** task time ledger, admin security overhaul, and universal websocket sync (MS-010) ([439c5f9](https://github.com/Jehudme/legatus/commit/439c5f9b465c83b28a76570ec758fef24081062b))
* **server,studio:** add worker fleet admin endpoints and studio runtime session wiring ([9dee8c5](https://github.com/Jehudme/legatus/commit/9dee8c58e5dc3ec9a3166ae7f003e66df9960283))
* **studio:** add execution workbench, profile engine, and catalog metadata inspector (MS-009) ([1980951](https://github.com/Jehudme/legatus/commit/19809512f0103a22408ef12af644b5dc189e2a70))
* **studio:** add function execution workbench, catalog persistence, audit refresh, and live console streaming ([9dc8e99](https://github.com/Jehudme/legatus/commit/9dc8e9930ab8c96de86851c6a3328a1f361a729a))
* **studio:** configure native jpackage desktop bundling and automated guide verification ([9c5df0e](https://github.com/Jehudme/legatus/commit/9c5df0eae82279aa4180652128725cf9d11ff368))
* **studio:** ensure FX thread safety across viewmodels and fix lstcp ws url normalization ([2cd267f](https://github.com/Jehudme/legatus/commit/2cd267f05018299fb34e7a74cf43940e801bbd85))
* **studio:** implement decoupled MVVM ViewModels layer for all cockpit views ([d25889f](https://github.com/Jehudme/legatus/commit/d25889f694bb9197ce79c05a053b48e8493bedd2))
* **studio:** implement FXML views, styles, and controllers for all cockpit views and dynamic cells ([0130a5e](https://github.com/Jehudme/legatus/commit/0130a5efe36d1b570bc378a5ed9aed31103fd8bd))
* **studio:** implement LSTCP wire protocol frames, REST DTOs, RestClient, and TelemetryCoalescer ([af334a6](https://github.com/Jehudme/legatus/commit/af334a68384a53f2693fc798632d548cfe74b065))
* **studio:** implement observable domain models and business services layer ([2fbfbfa](https://github.com/Jehudme/legatus/commit/2fbfbfaf944195efbff1091e191d5c6a7fb0c28d))
* **studio:** implement OS keychain security, AES-256-GCM crypto, config store, and operator session ([3bf7260](https://github.com/Jehudme/legatus/commit/3bf7260336a53f4f018a288e8aee3145d5c242c7))
* **studio:** overhaul cluster overview, fleet topology, and quarantine attestation UI ([0aec65f](https://github.com/Jehudme/legatus/commit/0aec65fcfc1b530a1c242c1329dfb22fcf5a3b78))
* **studio:** scaffold default JavaFX application and fix launcher entry point ([de491a7](https://github.com/Jehudme/legatus/commit/de491a77c04cf1565f4f1bd1aa84cce8f2c25363))
* **studio:** upgrade to Java 21, OpenJFX 21, AtlantaFX and add ViewLoaderService ([8e7d336](https://github.com/Jehudme/legatus/commit/8e7d33613e1dd7bb3e7f68e62004fd5859f59f8a))
* **tasks:** dispatch bytecode payload, broadcast task lifecycle, and sync Studio task explorer ([5826aba](https://github.com/Jehudme/legatus/commit/5826aba8e8a2ec889f3d3842c09dcf22c094dd08))
* **worker:** implement MS-006 sandboxed standard library and pure in-memory runtime ([84383bf](https://github.com/Jehudme/legatus/commit/84383bf97f69a875153d766385fe0a99efaf12e1))


### Bug Fixes

* **studio:** support studio_ws_ticket in LoginResponse and ignore unknown JSON properties ([3e295eb](https://github.com/Jehudme/legatus/commit/3e295eb8449d99cb12dea0fced0c14721a0a18c8))
* **studio:** update default connection URL, dynamic RestClient binding, and MainViewModel wiring ([923cbb2](https://github.com/Jehudme/legatus/commit/923cbb234ef7c21e974bbc6f86a25f1dc1cbccd8))

## [0.1.0] - 2026-10-14
### Documentation
* Establish subsystem specification (`docs/specs/studio-spec.md`) and LSTCP wire protocol (`docs/specs/protocols/studio-wire.md`).
* Formally record architectural decisions in `ADR-0002` (OpenJFX 21 & AtlantaFX) and `ADR-0005` (Quarantine Enrollment).
* Specify 60Hz telemetry frame coalescer, viewport subscription filtering, and bounded 10,000-line console ring buffer.
* Publish cockpit wireframe specification in `docs/architecture/wireframes/studio/studio-wireframe.md`.
* Publish operator manual in `docs/guides/04-studio.md`.
