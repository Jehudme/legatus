# Changelog

All notable changes to the Legatus Server subsystem (`legatus-server`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.2.0](https://github.com/Jehudme/legatus/compare/server-v0.1.0...server-v0.2.0) (2026-10-16)


### Features

* **all:** resolve telemetry live updates, audit verification, worker fleet, and vault contracts ([3f04ac6](https://github.com/Jehudme/legatus/commit/3f04ac6efa0666e130ebba548e8691e66b8ad763))
* **cockpit:** implement Milestone 011 live sync, telemetry overhaul, and UX improvements ([1dff83b](https://github.com/Jehudme/legatus/commit/1dff83ba3823c3a34c04913cb5df147a3094711d))
* **control-plane:** implement server-centric worker control plane and dynamic configuration ([c7ce658](https://github.com/Jehudme/legatus/commit/c7ce6582e321a3d2bc00b2938d2fcb26f390943e))
* **platform:** implement MS-005 protocol parity, streaming, and workflow DAG engine ([3c17611](https://github.com/Jehudme/legatus/commit/3c17611449bf49087fff50c9410278d68c219154))
* **security:** task time ledger, admin security overhaul, and universal websocket sync (MS-010) ([439c5f9](https://github.com/Jehudme/legatus/commit/439c5f9b465c83b28a76570ec758fef24081062b))
* **server,studio:** add worker fleet admin endpoints and studio runtime session wiring ([9dee8c5](https://github.com/Jehudme/legatus/commit/9dee8c58e5dc3ec9a3166ae7f003e66df9960283))
* **server:** add h2 worm non-deletability trigger on audit logs ([a44964c](https://github.com/Jehudme/legatus/commit/a44964ccba2ccc7d305299125f5e01bf33ad932d))
* **server:** add Liquibase performance indexes on tasks and workers ([6088f39](https://github.com/Jehudme/legatus/commit/6088f39ac24c40b3184a781d5d21d3d0daaf5f13))
* **server:** add optional prometheus actuator exporter and forwarder ([b9e2302](https://github.com/Jehudme/legatus/commit/b9e230231303b52ecfac468790cfbdfee3e483ee))
* **server:** bootstrap jhipster backend on embedded h2 disk ([72993a3](https://github.com/Jehudme/legatus/commit/72993a3651a6880db31e643b0c90f40bd11a733d))
* **server:** disable liquibase faker context to prevent random sample data generation ([11e3da7](https://github.com/Jehudme/legatus/commit/11e3da7c8f76e1da2d04f5b11b4066ec389ca2aa))
* **server:** implement bytecode cas storage and bounded argon2id service ([fef41a8](https://github.com/Jehudme/legatus/commit/fef41a8cd0e6d5d925617a0e2009842607ad9b6a))
* **server:** implement cron trigger scheduler and webhook ingestion gateway ([c2574a5](https://github.com/Jehudme/legatus/commit/c2574a528155c23851916e9279d86f091e7e0e06))
* **server:** implement deterministic bin-packing task scheduler with optimistic locking ([b19a541](https://github.com/Jehudme/legatus/commit/b19a541610a89996553b101feda3dc08cb665750))
* **server:** implement lctp websocket broker and dead-man switch sweeper ([4a34f9c](https://github.com/Jehudme/legatus/commit/4a34f9c02aeda92dbcd8f8c3e0e8d5c50afeda47))
* **server:** implement LSTCP studio stream engine with metric ticks, push alerts, and scoped log subscriptions ([5d6470a](https://github.com/Jehudme/legatus/commit/5d6470a44820a5313d02c5f1e3678cbee9df9745))
* **server:** implement operator rbac security engine and brute-force lockout ([69fed87](https://github.com/Jehudme/legatus/commit/69fed8716ee50b4a60536e1ef1997cc2ef5ed3e0))
* **server:** implement REST task and bytecode gateway with ETag caching and abort forwarding ([33c6ea3](https://github.com/Jehudme/legatus/commit/33c6ea397a774402d7d3c8dbb8de62d7685c20a4))
* **server:** implement secret vault and ed25519 fingerprint services ([6bd088a](https://github.com/Jehudme/legatus/commit/6bd088a6ec77aa674a788efc3285800472a068a6))
* **server:** implement secret vault repository persistence and zeroizable task dispatch ([03f771b](https://github.com/Jehudme/legatus/commit/03f771b6d54864bb51afb42a17b89fbcd90dc3bb))
* **server:** implement statutory worm audit log admin endpoints and rest 405 rejection ([1da7757](https://github.com/Jehudme/legatus/commit/1da775702e3178af509658fe15c14a11606ead9c))
* **server:** implement task history retention sweeper ([b9b7425](https://github.com/Jehudme/legatus/commit/b9b742515f09be77f703ce1ce4926a6c956b4d24))
* **server:** implement ticket broker and worm audit services ([3e4be0a](https://github.com/Jehudme/legatus/commit/3e4be0a7784fc8c19674acd3b01d5ee0b657b262))
* **server:** implement worker quarantine enrollment and auth endpoints ([d6d2a08](https://github.com/Jehudme/legatus/commit/d6d2a08d93b9c126a83f95bae6e781507a018c7c))
* **studio:** add execution workbench, profile engine, and catalog metadata inspector (MS-009) ([1980951](https://github.com/Jehudme/legatus/commit/19809512f0103a22408ef12af644b5dc189e2a70))
* **tasks:** dispatch bytecode payload, broadcast task lifecycle, and sync Studio task explorer ([5826aba](https://github.com/Jehudme/legatus/commit/5826aba8e8a2ec889f3d3842c09dcf22c094dd08))
* **worker:** implement MS-006 sandboxed standard library and pure in-memory runtime ([84383bf](https://github.com/Jehudme/legatus/commit/84383bf97f69a875153d766385fe0a99efaf12e1))


### Bug Fixes

* **server:** allow any checksum on modified fake data and bypass trigger during cleanup ([e709012](https://github.com/Jehudme/legatus/commit/e709012e51f590d430f6bb61257da242e0cf740f))
* **server:** bootstrap admin account if admin does not exist rather than checking table count ([7f311d9](https://github.com/Jehudme/legatus/commit/7f311d92deaa79ba7bfe031ae84c36e37b806881))
* **server:** resolve heartbeat long null checks and synchronize worker online state ([0f261a9](https://github.com/Jehudme/legatus/commit/0f261a9c749f50810f86f94525b5ad9bf6538eb2))

## [0.1.0] - 2026-10-14
### Documentation
* Establish subsystem specification (`docs/specs/server-spec.md`) and REST API specification (`docs/specs/protocols/api-endpoint.md`).
* Formally record architectural decisions in `ADR-0001` (Spring Boot 3, Loom, H2 AES encryption) and `ADR-0005` (Quarantine Enrollment).
* Codify Project Loom concurrency invariants, 4-thread Argon2id pool, and HKDF-SHA256 master key bootstrap.
* Publish server deployment and operations guide in `docs/guides/02-server.md`.
