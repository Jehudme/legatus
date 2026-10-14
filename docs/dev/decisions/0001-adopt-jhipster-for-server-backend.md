# ADR-0001: Adopt Spring Boot 3 (JHipster Core) & Java 21 for Server Orchestration Broker

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @server-lead, @security-lead
* **Date:** 2026-10-14
* **Technical Story:** Issue #105 / PR #108
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

The Legatus cluster architecture requires a centralized orchestration broker (`legatus-server`) responsible for worker node enrollment attestation, ephemeral transport ticket issuance, Solix bytecode cataloging and distribution, capacity-aware task scheduling, encrypted relational persistence, and full-duplex WebSocket telemetry streaming to desktop administrative cockpits (`docs/specs/server-spec.md`).

Historically, distributed orchestration brokers have either:
1. Relied on heavy external operational dependencies (e.g., dedicated PostgreSQL clusters, Redis caches, HashiCorp Vault, and distributed consensus rings like etcd), severely compromising the goal of a standalone, single-binary server deployment; or
2. Relied on reactive asynchronous runtimes (e.g., Netty, WebFlux, Vert.x) where non-blocking code is notoriously difficult to debug, trace, and audit due to fragmented stack traces, callback nesting, and thread-local context loss.

We require a robust, enterprise-grade backend technology stack that provides comprehensive REST API routing, TLS 1.3 WebSocket brokerage, schema migration management, and cryptographic primitives while operating as a self-contained, single-process executable with zero external database or caching daemon dependencies.

---

## 2. Decision Drivers (Forces)

1. **Zero External Daemon Infrastructure:** The server must execute as a standalone JAR requiring zero external Docker containers, external PostgreSQL/MySQL instances, or standalone Redis daemons. All relational state must reside in an embedded, encrypted ACID database, and transient tickets in an in-process cache.
2. **High-Concurrency Virtual Threading (Project Loom):** The broker must handle thousands of concurrent worker WebSockets (`LCTP`), REST task ingestion requests, and Studio telemetry streams concurrently using synchronous, readable, imperatively written Java code without reactive complexity or thread starvation.
3. **Enterprise Domain Scaffolding & Security:** Must provide battle-tested authentication filters, structured error envelopes, Liquibase database migration checksums, and robust JSON schema binding out of the box.
4. **Shared Type Semantics with Client Subsystems:** Must facilitate clean mental model alignment and potential serialization contract sharing with the Java 21 / OpenJFX desktop cockpit (`legatus-studio`).
5. **Cryptographic Defense-in-Depth:** Must natively support modern cryptographic operations (Argon2id password hashing, AES-256-GCM envelope encryption, TLS 1.3 engine) backed by certified security providers.

---

## 3. Considered Options

* **Option 1:** Spring Boot 3 (JHipster Core architecture) + Java 21 LTS (Project Loom Virtual Threads) + Embedded H2 Database (with AES-128 file-level encryption at rest).
* **Option 2:** Go (Golang) with Gin/Fiber HTTP + Gorilla WebSocket + SQLite / bbolt embedded storage.
* **Option 3:** Rust with Tokio + Axum / Actix-Web + SQLx / SQLite embedded engine.
* **Option 4:** Quarkus or Micronaut with Reactive Streams (Mutiny / RxJava) + PostgreSQL.

---

## 4. Evaluation of Considered Options

### Option 1: Spring Boot 3 (JHipster Core) & Java 21 LTS (Project Loom)
* **Description:** Leverage Java 21 LTS with Spring Boot 3.3+ utilizing JHipster's battle-tested domain and security scaffolding. All network I/O and scheduling execute on Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`). Persistence is handled via Spring Data JPA and Liquibase on top of an embedded, file-backed H2 relational database configured with `CIPHER=AES` and `AUTO_SERVER=TRUE`. In-memory transient ticket leases are managed via Caffeine.
* **Good:**
  * **Project Loom Concurrency:** Achieves massive socket and request concurrency using straightforward synchronous blocking code. Virtual threads eliminate the cognitive overhead and debugging nightmares of reactive programming (Reactor / WebFlux).
  * **Zero External Dependencies:** H2 runs in-process with full ACID relational semantics, optimistic locking (`@Version`), and automated Liquibase migrations; zero external database setup required.
  * **Enterprise Security Baseline:** JHipster provides pre-configured JWT authentication filters, standard exception translation, Actuator health metrics, and Spring Security 6 integration.
  * **DTO & Serialization Parity:** Directly mirrors the Java 21 `record` architecture utilized in `legatus-studio`, eliminating language impedance mismatches across backend and desktop teams.
* **Bad:**
  * Higher base memory footprint (JVM heap baseline of ~128–256 MB) compared to compiled Go or Rust binaries.
  * Slower initial startup time (~2–4 seconds) compared to native executables (mitigated by long-running cluster daemon lifecycle).

### Option 2: Go (Golang) with Gin/Fiber & SQLite
* **Description:** Single-binary Go daemon utilizing lightweight goroutines for concurrency and `mattn/go-sqlite3` or `modernc.org/sqlite` for persistence.
* **Good:**
  * Extremely fast cold start (< 100ms) and minimal baseline RAM footprint (~20 MB).
  * Natural single-binary deployment.
* **Bad:**
  * CGo dependency required for optimal SQLite performance and OpenSSL-backed cryptographic operations; pure Go crypto lacks certified Argon2id SIMD optimizations.
  * ORM and migration ecosystem (e.g., GORM, Goose) is significantly less mature and type-safe than JPA/Hibernate with Liquibase checksum tracking.
  * Diverges from the JavaFX technology stack used in `legatus-studio`, preventing cross-subsystem code sharing.

### Option 3: Rust with Tokio, Axum & SQLite
* **Description:** Asynchronous Rust web stack compiled to a native static ELF/PE binary using Tokio async runtime, Axum, and SQLx.
* **Good:**
  * Maximum raw execution speed and absolute minimal memory consumption (~10 MB).
  * Compile-time memory safety and zero garbage collection pauses.
* **Bad:**
  * Extreme engineering complexity and slow iteration velocity for enterprise backend features (complex relational graphs, audit tables, admin security filters).
  * Tokio async contagion across database calls (SQLite blocking calls require `spawn_blocking` thread offloading).
  * High maintenance burden for distributed business logic.

### Option 4: Quarkus / Micronaut Reactive with External PostgreSQL
* **Description:** Micro-framework with GraalVM native image compilation and reactive non-blocking SQL clients connected to an external PostgreSQL instance.
* **Good:**
  * Rapid startup and low memory footprint via GraalVM native images.
* **Bad:**
  * Strictly violates Decision Driver 1: requires provisioning and maintaining an external PostgreSQL container/cluster.
  * Reactive streams (Mutiny) complicate error handling, distributed tracing, and transactional consistency.

---

## 5. Decision Outcome

Chosen Option: **Option 1: Spring Boot 3 (JHipster Core) & Java 21 LTS (Project Loom)**.

This stack delivers the ideal balance between developer productivity, enterprise security, and distributed operational simplicity. Project Loom virtual threads provide high-throughput WebSocket concurrency without reactive programming, while embedded H2 with AES encryption satisfies the zero-external-dependency requirement for Legatus deployments.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* The server ships as a standalone executable JAR (`legatus-server.jar`) that runs anywhere with a standard JRE 21 runtime, requiring zero external database or caching servers.
* Virtual thread execution (`spring.threads.virtual.enabled: true`) ensures that thousands of long-lived worker WebSockets (`LCTP`) and Studio streams do not exhaust OS thread pools or block event loops.
* Relational schema evolution is completely deterministic and version-controlled via Liquibase XML/YAML migrations.
* Task secrets in `secrets_vault` are encrypted via AES-256-GCM envelope encryption backed by `LEGATUS_MASTER_KEY` (`server-spec.md` Section 5.2).

### 6.2 Negative Consequences (Trade-offs & Technical Debt)
* **Memory Footprint:** The JVM requires configuring heap boundaries (`-Xms256m -Xmx2g`) to avoid unbound memory growth on resource-constrained hosts.
* **H2 Concurrency Limits:** While H2 with `AUTO_SERVER=TRUE` handles Legatus cluster loads effectively, high-frequency concurrent writes require strict lock timeout configurations (`LOCK_TIMEOUT=10000;FILE_LOCK=FS`) and JPA optimistic locking (`@Version`) to mitigate write lock contention.
* **Argon2id CPU Throttling:** Argon2id hashing is resource-intensive ($64\text{ MB}$ RAM per derivation); server must restrict hash operations to a dedicated 4-thread pool to prevent virtual thread starvation on the carrier pool.

### 6.3 Neutral Consequences
* Configuration adheres to standard Spring Boot externalized configuration (`application.yml`, environment variables, CLI overrides).

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Formally codified in [`docs/specs/server-spec.md`](../../specs/server-spec.md) and [`docs/specs/protocols/api-endpoint.md`](../../specs/protocols/api-endpoint.md).
* **Code Implementation:** Governed by `server/pom.xml` and package structure `com.legatus.server.*`.
* **Automated Verification:**
  * Project Loom Virtual Thread execution verified via `VirtualThreadVerificationTest.java` (asserting `Thread.currentThread().isVirtual() == true` in controllers and WebSocket handlers).
  * Embedded H2 encryption-at-rest verified via `DatabaseEncryptionIT.java` (attempting to read raw H2 file blocks without cryptographic passphrase fails).
  * Rate-limiting and ticket burn-on-read verified via `WorkerEnrollmentControllerIT.java`.
