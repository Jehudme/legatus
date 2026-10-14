# Changelog

All notable changes to the Legatus Server subsystem (`legatus-server`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.1.0] - 2026-10-14
### Documentation
* Establish subsystem specification (`docs/specs/server-spec.md`) and REST API specification (`docs/specs/protocols/api-endpoint.md`).
* Formally record architectural decisions in `ADR-0001` (Spring Boot 3, Loom, H2 AES encryption) and `ADR-0005` (Quarantine Enrollment).
* Codify Project Loom concurrency invariants, 4-thread Argon2id pool, and HKDF-SHA256 master key bootstrap.
* Publish server deployment and operations guide in `docs/guides/02-server.md`.
