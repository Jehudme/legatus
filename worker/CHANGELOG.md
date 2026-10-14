# Changelog

All notable changes to the Legatus Worker subsystem (`legatusd`, `legatus`, `legatus-runner`) will be documented in this file. See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.1.0] - 2026-10-14
### Documentation
* Establish subsystem specification (`docs/specs/worker-spec.md`) and LCTP wire protocol (`docs/specs/protocols/worker-wire.md`).
* Document cross-platform defenses: anonymous `stdin` envelope streaming, `CREATE_NEW_PROCESS_GROUP`, and Windows Named Pipe SDDL DACL (`D:(A;;GRGW;;;AU)`).
* Formally record architectural decisions in `ADR-0004` (Solix VM), `ADR-0006` (Jittered Heartbeats), `ADR-0007` (`nng` IPC), and `ADR-0008` (`reproc++` Isolation).
* Publish worker operations guide in `docs/guides/05-worker.md`.
