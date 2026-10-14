# Operator Administrative & User Operations Sequences

## Document Metadata
* **Scope:** Operator CLI Local IPC, Studio Cockpit Attestation & Function Cataloging (`docs/architecture/sequences/user-operations.md`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Operators, UI/UX Architects, Security Engineers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/worker-spec.md`](../../specs/worker-spec.md)
  * [`docs/specs/studio-spec.md`](../../specs/studio-spec.md)
  * [`docs/specs/protocols/api-endpoint.md`](../../specs/protocols/api-endpoint.md)
  * [`docs/dev/decisions/0002-adopt-javafx-for-studio-ui.md`](../../dev/decisions/0002-adopt-javafx-for-studio-ui.md)
  * [`docs/dev/decisions/0005-node-enrollment-and-attestation.md`](../../dev/decisions/0005-node-enrollment-and-attestation.md)
  * [`docs/dev/decisions/0007-adopt-nng-for-local-daemon-ipc.md`](../../dev/decisions/0007-adopt-nng-for-local-daemon-ipc.md)

---

## 1. Executive Summary & Purpose

Platform operations within the Legatus ecosystem divide into two administrative channels:
1. **Interactive Node Control via Operator CLI (`legatus`):** High-speed, local-only inter-process communication with the local supervisor daemon (`legatusd`) over `nng` IPC sockets, guaranteeing zero direct network coupling to the remote server.
2. **Visual Cluster Management via Desktop Cockpit (`legatus-studio`):** Secure operator attestation of quarantined compute nodes, Solix function bytecode deployment, live task log observation, and cluster-wide emergency drainage.

This document formally specifies the operator workflows, communication framing, IPC protocols, and security attestations executed during user-initiated administrative actions.

---

## 2. Local Operator CLI & Daemon IPC (`nng` over `ipc://`)

The `legatus` CLI utility manages the local node daemon strictly over local IPC, abstracting Unix Domain Sockets and Windows Named Pipes via `nng` Request/Reply (`REQ0`/`REP0`):

```mermaid
sequenceDiagram
    autonumber
    participant Op as Human Operator
    participant CLI as legatus CLI (CLI11)
    participant NNG as nng Client Socket (REQ0)
    participant Pipe as IPC Transport (ipc://)
    participant Daemon as legatusd Supervisor (REP0)
    participant Server as Server LCTP Broker (WSS)

    Op->>CLI: Execute: legatus drain --timeout 30
    CLI->>CLI: Parse & Validate CLI Flags via CLI11
    CLI->>NNG: nng_req0_open() -> nng_dial("ipc:///tmp/legatus_worker.ipc")
    Note over NNG,Pipe: Windows: Dials ipc://legatus_worker_ipc<br/>POSIX: Dials ipc:///tmp/legatus_worker.ipc (0600)
    CLI->>NNG: nng_send({"command": "DAEMON_DRAIN", "timeout_seconds": 30})
    NNG->>Pipe: Transmit Message-Oriented IPC Frame
    Pipe->>Daemon: nng_recv() on Supervisor REP0 Socket
    Daemon->>Daemon: Set Local Invariant: node_draining = true
    Daemon->>Server: Transmit LCTP DRAIN_NOTICE Frame (expedited unshelving)
    Server-->>Daemon: LCTP TCP ACK
    Daemon->>Pipe: nng_send({"status": "DRAINING", "in_flight_tasks": 2, "drain_deadline_ms": 30000})
    Pipe->>NNG: Receive Response Frame
    NNG->>CLI: Return Response Payload
    CLI->>Op: Print Terminal: "Worker node transitioning to DRAINING. 2 tasks finishing."
```

---

## 3. Visual Quarantine Attestation & Node Lease Approval via Studio

When a newly installed worker contacts `legatus-server`, it is quarantined until an operator visually confirms its cryptographic identity in `legatus-studio`:

```mermaid
sequenceDiagram
    autonumber
    participant WConsole as Physical Worker Terminal
    participant Op as Human Operator
    participant Studio as Legatus Studio (Cockpit)
    participant REST as Server REST Gateway (/api/v1/admin)
    participant DB as Embedded H2 Database
    participant W as Worker Daemon (legatusd)

    Note over WConsole: legatusd prints upon boot:<br/>Ed25519 Fingerprint: 4E:8A:BC:31:09:FF:7D:12<br/>Visual Confirmation Code: Tiger-Galaxy-77
    Studio->>Op: Displays Non-Blocking Toast: "New Worker Enrollment Pending (wk-east-042)"
    Op->>Studio: Open Quarantine Attestation Modal
    Studio->>Studio: Render Node Fingerprint and 3-Word Confirmation Code
    Op->>Op: Visually compare "Tiger-Galaxy-77" on Studio screen vs. Worker terminal
    alt Code Matches & Host Identity Confirmed
        Op->>Studio: Click [Approve & Issue Lease]
        Studio->>REST: POST /api/v1/admin/workers/enrollments/wk-east-042/approve (Bearer JWT)
        REST->>DB: UPDATE workers SET state = 'APPROVED', allocatable_cores = 16 WHERE worker_id = 'wk-east-042'
        REST-->>Studio: HTTP 200 OK {"status": "APPROVED"}
        Studio->>Op: Update Fleet View: Node "wk-east-042" moves to Active Ring
    else Fingerprint Mismatch or Suspicious IP
        Op->>Studio: Click [Reject & Blacklist IP]
        Studio->>REST: POST /api/v1/admin/workers/enrollments/wk-east-042/reject
        REST->>DB: UPDATE workers SET state = 'REVOKED' WHERE worker_id = 'wk-east-042'
        REST-->>Studio: HTTP 200 OK {"status": "REJECTED"}
    end
```

---

## 4. Solix Function Deployment & Bytecode Cataloging Flow

Developers and operators publish serverless compute functions to the centralized catalog via the Studio UI or REST API:

```mermaid
sequenceDiagram
    autonumber
    participant Dev as Developer / Operator
    participant Studio as Legatus Studio UI
    participant REST as Server REST Gateway (/api/v1/functions)
    participant DB as Embedded H2 Database
    participant Cache as Server Bytecode Cache

    Dev->>Studio: Drag & Drop 'image_resizer.solix' + Configure Entry Point
    Studio->>Studio: Compute Local SHA-256 Digest of Bytecode Binary
    Studio->>REST: POST /api/v1/functions (Multipart Form: metadata JSON + binary stream)
    REST->>REST: Stream Binary Stream -> Calculate Server SHA-256 Digest
    alt Computed Digest != Declared Digest
        REST-->>Studio: HTTP 400 Bad Request (ERR_BYTECODE_DIGEST_MISMATCH)
    else Digest Valid
        REST->>DB: Check if bytecode_sha256 already exists in database
        alt Bytecode Deduplicated (Cache Hit)
            REST->>DB: INSERT INTO functions (name, semver, entry_point, bytecode_sha256, default_budget)
            Note over REST,DB: References existing BLOB record; Zero redundant storage
        else New Bytecode Binary
            REST->>DB: INSERT INTO bytecode_blobs (sha256, binary_blob, size_bytes)
            REST->>DB: INSERT INTO functions (name, semver, entry_point, bytecode_sha256, default_budget)
            REST->>Cache: Cache raw binary in server byte arena
        end
        REST-->>Studio: HTTP 201 Created {"function_id": "fnc_01H...", "bytecode_sha256": "4a7b..."}
        Studio->>Dev: Display Success: "Function 'image_resizer:v1.0.0' registered and ready for dispatch."
    end
```

---

## 5. Operator Account Provisioning & RBAC Lifecycle Sequence

The cluster administrator provisions and manages multi-tier operator and auditor accounts through Legatus Studio or the REST API:

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Human Administrator
    participant Studio as Legatus Studio UI
    participant REST as Server REST Gateway (/api/v1/admin)
    participant Sec as Argon2id Bounded Pool (4 Threads)
    participant DB as Embedded H2 Database
    participant WORM as WORM Audit Append Engine

    Admin->>Studio: Navigate to Account Management -> Click [Create Account]
    Admin->>Studio: Input: "auditor@compliance.corp", Role: "AUDITOR", Initial Password
    Studio->>REST: POST /api/v1/admin/accounts (Bearer JWT with CLUSTER_ADMIN)
    REST->>REST: Verify JWT Role == CLUSTER_ADMIN
    alt Non-Admin Role
        REST-->>Studio: HTTP 403 Forbidden (ERR_INSUFFICIENT_PRIVILEGES)
    else Authorized Admin
        REST->>DB: Check if username/email already exists
        alt Account Already Exists
            REST-->>Studio: HTTP 409 Conflict (ERR_ACCOUNT_EXISTS)
        else Unique Account
            REST->>Sec: Queue Argon2id Hash Task (m=64MB, t=3, p=4)
            Sec-->>REST: Return Salted Password Hash
            REST->>DB: INSERT INTO accounts (id, username, email, password_hash, role, status)
            REST->>WORM: Append Audit Event: ACCOUNT_CREATED (target: auditor@compliance.corp)
            WORM->>WORM: Compute Sequential Hash H_i = SHA256(Seq_i || H_{i-1} || Payload)
            WORM->>DB: INSERT INTO audit_logs (sequence_num, prev_record_hash, record_hash, ...)
            REST-->>Studio: HTTP 201 Created {"account_id": "e9f0...", "role": "AUDITOR", "status": "ACTIVE"}
            Studio->>Admin: Display Success: "Account auditor@compliance.corp created."
        end
    end
```

---

## 6. Statutory Audit Trail Verification & Meta-Auditing Sequence

Compliance auditors inspect and verify the tamper-evident WORM audit trail. Every audit action is itself recorded to ensure zero unmonitored access ("Auditing the Auditors"):

```mermaid
sequenceDiagram
    autonumber
    participant Auditor as Compliance Auditor
    participant Studio as Legatus Studio UI
    participant REST as Server REST Gateway (/api/v1/admin)
    participant DB as Embedded H2 Database
    participant WORM as WORM Audit Verification Engine

    Auditor->>Studio: Navigate to Audit Explorer -> Click [Verify Chain Integrity]
    Studio->>REST: GET /api/v1/admin/audit-logs/verify (Bearer JWT with AUDITOR)
    REST->>REST: Verify JWT Role in [CLUSTER_ADMIN, AUDITOR]
    REST->>WORM: Execute Sequential Verification (Sequence 1 to Head)
    WORM->>DB: SELECT * FROM audit_logs ORDER BY sequence_num ASC
    loop For Every Record i from 1 to N
        WORM->>WORM: Verify prev_record_hash_i == record_hash_{i-1}
        WORM->>WORM: Recompute Expected Hash H_i = SHA256(...)
        WORM->>WORM: Verify H_i == record_hash_i
    end
    alt Hash Chain Intact (Zero Tampering)
        WORM->>WORM: Create Meta-Audit Event: AUDIT_LOG_VERIFIED (status: SUCCESS)
        WORM->>DB: INSERT INTO audit_logs (sequence_num: N+1, prev_record_hash: H_N, ...)
        REST-->>Studio: HTTP 200 OK {"status": "VALID", "verified_records_count": N, "chain_integrity": true}
        Studio->>Auditor: Display Shield Green: "Audit Trail Cryptographically Verified (N records)"
    else Hash Discrepancy Detected (Tampered Row K)
        REST-->>Studio: HTTP 500 Internal Server Error (ERR_AUDIT_CHAIN_CORRUPTED, sequence: K)
        Studio->>Auditor: Display Warning Red: "SECURITY ALERT: Tampering detected at sequence K!"
    end

    opt Tamper Simulation (Attempting to Delete Logs)
        Auditor->>REST: DELETE /api/v1/admin/audit-logs/all
        REST->>WORM: Intercept & Enforce Statutory Zero-Deletion Invariant
        WORM->>WORM: Append Meta-Audit Event: AUDIT_LOG_TAMPER_ATTEMPT (action_status: DENIED)
        WORM->>DB: INSERT INTO audit_logs (sequence_num: N+2, event_type: 'AUDIT_LOG_TAMPER_ATTEMPT')
        REST-->>Auditor: HTTP 405 Method Not Allowed (ERR_AUDIT_LOG_IMMUTABLE)
    end
```

---

## 7. Verification & Compliance Checklist

- [ ] **Zero Implementation Code:** Contains zero raw C++ or Java implementation code blocks (`GL-0005`).
- [ ] **Local IPC Architecture:** Enforces `nng` Request/Reply (`REQ0`/`REP0`) without binding to TCP loopback interfaces (`ADR-0007`).
- [ ] **Visual Attestation Protocol:** Formally details the human-in-the-loop 3-word visual confirmation workflow (`Tiger-Galaxy-77`).
- [ ] **Bytecode Deduplication:** Specifies SHA-256 content-addressable storage for Solix binaries in the central catalog.
- [ ] **RBAC Provisioning Sequence:** Formally details multi-operator provisioning and privilege boundaries (`CLUSTER_ADMIN`, `OPERATOR`, `AUDITOR`).
- [ ] **Statutory WORM Verification:** Sequences cryptographic hash-chain verification and meta-audit logging ("Auditing the Auditors").
- [ ] **Automated Test Coverage:** Verified via `test_cli_ipc.cpp`, `FunctionCatalogIT.java`, `StudioEnrollmentViewTest.java`, and `AuditLogVerificationIT.java`.
