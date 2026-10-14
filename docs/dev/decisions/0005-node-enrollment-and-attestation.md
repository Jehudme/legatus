# ADR-0005: Quarantine Node Enrollment, Visual Attestation & Ephemeral Ticket Exchange

## Metadata
* **Status:** ACCEPTED
* **Deciders:** @jehud-metellus, @sys-architect, @security-lead
* **Date:** 2026-10-14
* **Technical Story:** Issue #109 / PR #112
* **Supersedes:** None
* **Superseded By:** None

---

## 1. Context and Problem Statement

When provisioning distributed compute nodes (`legatusd`) across bare-metal servers, hybrid cloud instances, or edge gateways, operators face a classic bootstrapping dilemma: how do new worker nodes securely authenticate with the centralized orchestration broker (`legatus-server`) without requiring operators to manually generate, copy, and paste sensitive credentials into `worker.toml` on every host beforehand?

Furthermore, once a node is enrolled and holds a long-term credential (`secret_token`), passing that static token directly in WebSocket upgrade URLs (`wss://server/ws/worker?token=...`) exposes long-lived credentials to reverse-proxy access logs, load balancers, and network diagnostic tools, while using stateless long-lived JWTs prevents immediate revocation of compromised nodes. We must establish an enrollment and session authentication architecture that provides zero-config initial node startup, Man-in-the-Middle (MitM) attestation, instant revocation, and zero credential leakage in WebSocket URLs.

---

## 2. Decision Drivers (Forces)

1. **Zero-Friction Node Provisioning:** A newly installed `legatusd` binary must be able to boot and contact the server with only the server's HTTPS URL, requiring zero pre-baked secrets in the base OS image.
2. **Cryptographic MitM & Rogue Node Defense:** Unapproved nodes must be strictly quarantined (zero task dispatch, zero cluster visibility) until an operator visually verifies the node's cryptographic identity in `legatus-studio`, while the registration endpoint must resist Denial-of-Service (DoS) flooding.
3. **Zero Static Credential Exposure in WebSocket Handshakes:** Long-term node secrets must never appear in URL query parameters or proxy logs during RFC 6455 WebSocket upgrades.
4. **Instant Node Revocation:** Compromised or decommissioned worker nodes must be revocable immediately without rotating cluster-wide signing keys.

---

## 3. Considered Options

* **Option 1:** Quarantine Enrollment with Ed25519 Visual Fingerprint Attestation + Two-Tier Ephemeral Burn-on-Read Ticket Exchange.
* **Option 2:** Pre-Shared Static Long-Lived JWTs manually copied into `worker.toml` on each machine.
* **Option 3:** Mutual TLS (mTLS) with an internal X.509 Certificate Authority (CA) and manual CSR signing per node.

---

## 4. Evaluation of Considered Options

### Option 1: Quarantine Enrollment + Ephemeral Burn-on-Read Ticket Exchange
* **Description:** Unconfigured workers generate an local Ed25519 keypair via OpenSSL 3.0 and submit their public key and hardware profile to `POST /api/v1/workers/register`. Both worker CLI and `legatus-studio` independently compute $\text{SHA256}(\text{public\_key})$ and render a colon-separated hex fingerprint alongside a deterministic 3-word visual code (`Tiger-Galaxy-77`). While in `PENDING_APPROVAL`, the node is quarantined. Upon operator approval in Studio, the server issues a 256-bit `secret_token` (stored as an Argon2id hash in H2). For live connections, the worker exchanges its `secret_token` via `POST /api/v1/auth/worker` for a 30-second, single-use ticket (`ws_tkt_...` stored in Caffeine) that is atomically burned upon the `GET /ws/worker?ticket=...` WebSocket upgrade.
* **Good:**
  * Zero manual file editing during node deployment; operators verify the 3-word visual code (`Tiger-Galaxy-77`) and click **[Approve]** in Studio.
  * Ephemeral tickets have a 30-second TTL and are purged atomically on read; any leaked query string in proxy logs is already invalidated and useless to an attacker.
  * Instant revocation: marking a worker `REVOKED` in H2 immediately blocks ticket issuance and severs active sessions.
* **Bad:**
  * Requires implementing a two-step connection sequence (REST ticket exchange followed by WebSocket upgrade) in `legatusd`.
  * Requires rate-limiting (`5 req/min/IP`) and a pending queue cap (`50 nodes`) on `/register` to prevent unauthenticated queue exhaustion.

### Option 2: Pre-Shared Static Long-Lived JWTs
* **Description:** Operators generate a 90-day or non-expiring JWT on the server, manually copy it into `/etc/legatus/worker.toml` on each server, and pass it in the WebSocket upgrade request.
* **Good:**
  * Simple stateless signature verification on the server.
* **Bad:**
  * Violates instant revocation: a stolen node with a 90-day JWT cannot be revoked without maintaining a stateful blocklist or rotating the entire cluster's signing key.
  * Passing long-lived JWTs in WebSocket upgrade query strings leaks active credentials into reverse-proxy logs.
  * High operational toil when provisioning dozens of bare-metal nodes.

### Option 3: Mutual TLS (mTLS) Client Certificates
* **Description:** Require operators to run an X.509 PKI CA, generate client keypairs and Certificate Signing Requests (CSRs) on every worker, sign them out-of-band, and configure client cert paths in `worker.toml`.
* **Good:**
  * Strong transport-layer cryptographic authentication.
* **Bad:**
  * Notoriously high operational friction; certificate expiration and Certificate Revocation List (CRL / OCSP) management across Windows, Linux, and macOS nodes is a frequent cause of production outages.
  * Lacks a built-in human-friendly UI approval workflow in `legatus-studio` for declaring initial hardware quotas.

---

## 5. Decision Outcome

Chosen Option: **Option 1: Quarantine Enrollment with Ed25519 Visual Fingerprint Attestation + Two-Tier Ephemeral Burn-on-Read Ticket Exchange**, because it combines Tailscale/Kubernetes-style zero-touch onboarding with visual MitM protection, Argon2id secret storage, and atomic burn-on-read WebSocket upgrade tickets.

---

## 6. Consequences & Operational Impact

### 6.1 Positive Consequences
* Deploying a new worker node requires zero pre-shared secrets; operators attest nodes visually in `legatus-studio` by matching `4E:8A:BC:31:09:FF:7D:12 (Tiger-Galaxy-77)`.
* WebSocket upgrade URLs only ever carry 30-second single-use tickets (`ws_tkt_...`) that are atomically deleted from the server's `Caffeine` cache upon handshake completion.
* Static `secret_token` values are hashed at rest using Argon2id ($m=64\text{ MB}, t=3, p=4$).

### 6.2 Negative Consequences (Trade-offs)
* **Enrollment DoS Surface:** Because `/api/v1/workers/register` is reachable by unauthenticated nodes, `legatus-server` must strictly enforce the 5 requests/minute/IP rate limit, the 50-node pending queue ceiling, and the 15-minute expiration TTL.
* **Argon2id CPU/RAM Cost:** Hashing tokens with 64 MB Argon2id is intentionally expensive; therefore, the server pins Argon2id verification to a bounded 4-thread carrier pool (`server-spec.md` Section 4.1).

### 6.3 Neutral Consequences
* Worker enrollment states (`PENDING_APPROVAL`, `APPROVED`, `ONLINE`, `DRAINING`, `OFFLINE`, `REVOKED`, `EXPIRED`) are governed by the FSM in `docs/specs/server-spec.md`.

---

## 7. Compliance, Verification & Implementation Plan

* **Specification Alignment:** Codified in `docs/specs/worker-spec.md` (Sections 4.2 & 4.3), `docs/specs/server-spec.md` (Sections 4.1 & 4.2), and `docs/specs/protocols/api-endpoint.md`.
* **Test Verification:**
  * Catch2 suite `worker/tests/unit/test_crypto_enrollment.cpp` verifying Ed25519 keygen, SHA-256 hex formatting, and 3-word visual code determinism.
  * JUnit 5 suite `WorkerEnrollmentControllerIT.java` verifying rate limiting (`429`), 30-second ticket TTL, and atomic burn-on-read replay rejection (`401`).