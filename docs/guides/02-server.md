# Legatus Server Operator & Production Deployment Guide

## Document Metadata
* **Scope:** Server Subsystem Configuration & Production Deployment (`docs/guides/02-server.md`)
* **Status:** Final Implementation Standard
* **Audience:** DevOps Engineers, Platform Administrators, Site Reliability Engineers (SREs)
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/server-spec.md`](../specs/server-spec.md)
  * [`docs/specs/protocols/api-endpoint.md`](../specs/protocols/api-endpoint.md)
  * [`docs/dev/decisions/0001-adopt-jhipster-for-server-backend.md`](../dev/decisions/0001-adopt-jhipster-for-server-backend.md)

---

## 1. Executive Summary & Production Invariants

The **Legatus Server** (`legatus-server`) is a single-binary, standalone JVM application built on Java 21 LTS, Spring Boot 3, and Project Loom. It brokers worker enrollment, persists cluster metadata in an embedded encrypted H2 database, schedules tasks across compute nodes, and broadcasts live telemetry.

Production deployments **MUST** uphold three foundational invariants:
1. **Master Key Bootstrapping:** In production profile (`prod`), the server **MUST** derive an AES-256-GCM envelope key from the `LEGATUS_MASTER_KEY` environment variable. If omitted, the server **MUST** fail startup with exit code `78` (`EX_CONFIG`).
2. **TLS 1.3 Transport Mandatory:** Plaintext HTTP/WS connections are strictly disabled. All communication enforces TLS 1.3 with standard PKCS12 keystores.
3. **Dedicated Carrier Thread Bounds:** Argon2id password hashing is bound to a dedicated 4-thread pool to prevent memory exhaustion surge attacks during mass enrollment events.

---

## 2. Configuration Reference (`server.yml`)

The server is configured via standard Spring Boot YAML located at `/etc/legatus/server.yml`:

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    protocol: TLS
    enabled-protocols: TLSv1.3
    key-store: /etc/legatus/tls/keystore.p12
    key-store-password: "${TLS_KEYSTORE_PASSWORD}"
    key-store-type: PKCS12

spring:
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:h2:file:/var/lib/legatus/db/legatus;CIPHER=AES;AUTO_SERVER=TRUE;FILE_LOCK=FS;LOCK_TIMEOUT=10000
    username: legatus
    password: "${H2_DATABASE_PASSPHRASE}"
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false

legatus:
  cluster:
    name: "production-core"
    max-pending-enrollments: 50
    enrollment-ttl-seconds: 900
    ticket-ttl-seconds: 30
  heartbeat:
    interval-ms: 5000
    timeout-ms: 15000
  scheduler:
    strategy: LEAST_LOADED
    dispatch-batch-size: 64
    max-queue-depth: 100000
    default-max-retries: 2
  retention:
    auto-purge: true
    completed-task-ttl-hours: 168
```

---

## 3. Production Environment Variables

| Variable Name | Required | Default | Purpose & Constraints |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Yes | `prod` | Profile selector (`prod` or `dev`). `prod` enables strict key and security checks. |
| `LEGATUS_MASTER_KEY` | Yes (in `prod`)| None | 256-bit passphrase (minimum 32 characters) used to derive the AES-256-GCM secret vault key. |
| `H2_DATABASE_PASSPHRASE` | Yes | None | Encryption password for the file-backed H2 database (`CIPHER=AES`). |
| `TLS_KEYSTORE_PASSWORD` | Yes | None | Decryption password for the PKCS12 server TLS keystore. |

---

## 4. Systemd Service Deployment (Linux)

Install the server executable and configuration files under standard Linux system paths:

```bash
sudo mkdir -p /var/lib/legatus/db /var/log/legatus /etc/legatus/tls
sudo cp server/target/legatus-server.jar /opt/legatus/legatus-server.jar
sudo chown -R legatus:legatus /var/lib/legatus /var/log/legatus /etc/legatus /opt/legatus
sudo chmod 700 /var/lib/legatus/db /etc/legatus/tls
```

Create `/etc/systemd/system/legatus-server.service`:

```ini
[Unit]
Description=Legatus Server Orchestration Broker
After=network.target

[Service]
Type=simple
User=legatus
Group=legatus
WorkingDirectory=/var/lib/legatus
Environment="SPRING_PROFILES_ACTIVE=prod"
EnvironmentFile=/etc/legatus/server.env
ExecStart=/usr/bin/java \
    --enable-preview \
    -Xms512m -Xmx4g \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -jar /opt/legatus/legatus-server.jar \
    --spring.config.additional-location=file:/etc/legatus/server.yml
Restart=always
RestartSec=5s
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now legatus-server
```

---

## 5. Maintenance & Operational Procedures

### 5.1 Database Backups (Online Hot Backup)
Because `AUTO_SERVER=TRUE` is configured in H2, administrators can execute an online hot backup without stopping the server daemon:

```bash
curl -X POST https://localhost:8443/api/v1/admin/maintenance/backup \
  -H "Authorization: Bearer <operator_jwt>" \
  -H "Content-Type: application/json" \
  -d '{"target_path": "/var/backups/legatus/legatus-backup-$(date +%F).zip"}'
```

### 5.2 Purging Completed Task History
By default, completed task history older than 7 days (`168` hours) is automatically purged by a nightly Virtual Thread sweeper. To trigger an immediate purge manually:

```bash
curl -X POST https://localhost:8443/api/v1/admin/maintenance/purge-tasks \
  -H "Authorization: Bearer <operator_jwt>" \
  -H "Content-Type: application/json" \
  -d '{"retention_hours": 24}'
```

---

## 6. Failure Modes & Troubleshooting

| Symptom / Log Entry | Root Cause | Remediation Action |
| :--- | :--- | :--- |
| `System.exit(78 / EX_CONFIG)` on boot | `LEGATUS_MASTER_KEY` environment variable is missing or shorter than 32 characters in `prod` profile. | Define `LEGATUS_MASTER_KEY` with a strong 32+ character passphrase in `/etc/legatus/server.env`. |
| `File locked by another process: .../legatus.mv.db` | Multiple JVM instances attempting to open the H2 database without `AUTO_SERVER=TRUE`, or ungraceful crash leaving lock file. | Verify no orphaned `java` process is running. Inspect `/var/lib/legatus/db/` and remove stale `.lock.db` files if verified dead. |
| `HTTP 429 ERR_ENROLLMENT_QUEUE_FULL` | Over 50 worker nodes attempting to register concurrently, or potential enrollment DoS attack. | Review pending nodes in Legatus Studio quarantine queue and approve or reject backlog. |
| `WS 1011 ERR_WORKER_HEARTBEAT_TIMEOUT` | Worker node failed to transmit a `HEARTBEAT` frame within 15 seconds. | Check network connectivity between worker host and server. Check if worker host CPU is saturated. |
