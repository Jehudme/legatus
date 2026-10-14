# Legatus Server Subsystem

## Component Metadata
* **Component:** `server` (`legatus-server`)
* **Language & Standard:** OpenJDK / Eclipse Temurin Java 21 LTS (Project Loom enabled)
* **Framework:** Spring Boot 3.3+ (JHipster Core architecture)
* **Build System:** Apache Maven 3.9+ (`server/pom.xml`)
* **Persistence:** Embedded H2 Database (AES-128 page cipher, `AUTO_SERVER=TRUE`, `LOCK_TIMEOUT=10000`)
* **Normative Specification:** [`docs/specs/server-spec.md`](../docs/specs/server-spec.md)
* **API Specification:** [`docs/specs/protocols/api-endpoint.md`](../docs/specs/protocols/api-endpoint.md)
* **Deployment Guide:** [`docs/guides/02-server.md`](../docs/guides/02-server.md)

---

## 1. Overview & Architecture

The `server/` subsystem operates as the centralized cluster coordination broker. It is engineered as a standalone, zero-external-dependency executable JAR that coordinates the entire Legatus compute fabric:

1. **Project Loom Concurrency:** All REST endpoints, LCTP worker WebSocket sessions, and Studio telemetry streams execute on Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`), achieving high socket density without reactive programming complexity.
2. **Encrypted Embedded Persistence:** Persists worker metadata, task ledgers, audit logs, and Solix bytecode binaries inside an embedded H2 relational database secured with file-level AES-128 encryption (`CIPHER=AES`).
3. **Deterministic Task Scheduling:** Bin-packs incoming tasks across online worker nodes based on real-time allocatable core counts, memory ceilings, and instruction budgets (`LEAST_LOADED`, `BIN_PACK`, `LOCALITY_FIRST`).
4. **Cryptographic Defense-in-Depth:** Derives an AES-256-GCM envelope vault key from `LEGATUS_MASTER_KEY` via HKDF-SHA256, bounds Argon2id hash derivations to a 4-thread pool, and issues single-use 30-second ephemeral tickets (`ws_tkt_...`) for WebSocket upgrades.

---

## 2. Directory Layout

```text
server/
├── pom.xml                    # Maven POM build configuration
├── CHANGELOG.md               # Release Please component changelog
├── README.md                  # Subsystem overview (This file)
├── src/
│   ├── main/
│   │   ├── java/dev/legatus/server/
│   │   │   ├── config/        # Security, Virtual Thread executor, WebSocket configs
│   │   │   ├── domain/        # JPA Entities and Java 21 domain records
│   │   │   ├── repository/    # Spring Data JPA repositories with optimistic locking
│   │   │   ├── scheduler/     # Bin-packing algorithms and dispatch loops
│   │   │   ├── security/      # Argon2id carrier pool, HKDF master key, JWT filters
│   │   │   └── web/           # REST Controllers and WebSocket LCTP handlers
│   │   └── resources/
│   │       ├── config/        # Liquibase master XML changesets
│   │       └── application.yml# Spring Boot configuration
│   └── test/                  # JUnit 5 and Testcontainers verification suites
```

---

## 3. Building & Testing

### 3.1 Build Executable JAR via Maven
```bash
mvn clean package -DskipTests
```
*Output generated:* `target/legatus-server.jar`

### 3.2 Running Automated Tests (JUnit 5)
```bash
mvn clean test
```

### 3.3 Running Integration Tests
```bash
mvn clean verify -Pprod,test
```
