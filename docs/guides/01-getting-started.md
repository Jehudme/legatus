# Getting Started with Legatus: Local Cluster Quickstart

## Document Metadata
* **Scope:** Local Development Cluster Deployment (`docs/guides/01-getting-started.md`)
* **Status:** Final Implementation Standard
* **Audience:** Software Engineers, Contributors, Evaluators
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)
* **Related Specifications:**
  * [`docs/specs/server-spec.md`](../specs/server-spec.md)
  * [`docs/specs/worker-spec.md`](../specs/worker-spec.md)
  * [`docs/specs/studio-spec.md`](../specs/studio-spec.md)

---

## 1. Introduction

This guide walks through building, configuring, and executing an end-to-end local Legatus compute cluster on a single workstation (Linux, macOS, or Windows WSL2). By the end of this quickstart, you will:
1. Compile the C++20 worker daemon (`legatusd`), runner (`legatus-runner`), and operator CLI (`legatus`).
2. Package and launch the Java 21 orchestration broker (`legatus-server`).
3. Boot the worker daemon and visually attest its quarantine registration in `legatus-studio`.
4. Deploy and execute a deterministic guest task using Solix bytecode.

---

## 2. Step 1: Clone and Build the Monorepo

Ensure your host meets the toolchain prerequisites in [`docs/guides/README.md`](README.md) (Java 21, C++20 compiler, CMake 3.28+, Maven 3.9+, OpenSSL 3.0).

```bash
git clone https://github.com/legatus/legatus.git
cd legatus
```

### 2.1 Build the C++20 Worker Subsystem
```bash
cmake -B worker/build -S worker -DCMAKE_BUILD_TYPE=Release
cmake --build worker/build --parallel $(nproc)
```
*Outputs generated in `worker/build/bin/`:*
* `legatusd`: Worker supervisor daemon
* `legatus`: Operator interactive CLI
* `legatus-runner`: Ephemeral child task runner

### 2.2 Build the Java 21 Server Broker
```bash
mvn clean package -DskipTests -pl server -am
```
*Output generated:* `server/target/legatus-server.jar`

### 2.3 Build the OpenJFX 21 Studio Cockpit
```bash
mvn clean package -DskipTests -pl studio -am
```
*Output generated:* `studio/target/legatus-studio.jar`

---

## 3. Step 2: Launch the Coordination Server

In development environments, launch `legatus-server` using the `dev` profile (which automatically provisions an ephemeral H2 database and development cryptographic keys):

```bash
export SPRING_PROFILES_ACTIVE=dev
java -jar server/target/legatus-server.jar
```

The server binds to port `8443` (TLS 1.3) and outputs:
```text
2026-10-14 17:30:00.120 [main] INFO  c.l.s.LegatusServerApplication - Starting LegatusServer v1.0.0...
2026-10-14 17:30:01.450 [main] INFO  c.l.s.c.DatabaseConfiguration - Initialized encrypted H2 persistence (CIPHER=AES).
2026-10-14 17:30:02.100 [main] INFO  c.l.s.w.BrokerWebSocketHandler - LCTP Worker Broker listening on wss://localhost:8443/ws/worker
2026-10-14 17:30:02.250 [main] INFO  c.l.s.s.StudioStreamEngine - Studio Telemetry Stream listening on wss://localhost:8443/ws/studio
```

---

## 4. Step 3: Launch the Worker Supervisor Daemon

In a new terminal window, start the local worker daemon using the operator CLI:

```bash
./worker/build/bin/legatus up --config /tmp/legatus-worker-dev.toml --server https://localhost:8443 --insecure
```

Upon initial launch, `legatusd` detects an unconfigured node, generates an Ed25519 keypair, and submits a quarantine registration request:
```text
[2026-10-14 17:30:05] [INFO] Generated local node identity: compute-dev-node-01
[2026-10-14 17:30:05] [INFO] Ed25519 Fingerprint: 4E:8A:BC:31:09:FF:7D:12
[2026-10-14 17:30:05] [WARN] Node quarantined in PENDING_APPROVAL state.
[2026-10-14 17:30:05] [WARN] >>> Visual Confirmation Code: [ TIGER - GALAXY - 77 ] <<<
[2026-10-14 17:30:05] [INFO] Polling server for approval every 5000ms...
```

---

## 5. Step 4: Launch Studio & Attest the Node

In a third terminal window, launch the Legatus Studio administration cockpit:

```bash
java -jar studio/target/legatus-studio.jar --server https://localhost:8443 --insecure
```

1. Log in with the default development credentials (`admin` / `admin`).
2. A yellow notification badge will appear next to the **Quarantine Attestation** tab on the left navigation rail.
3. Click **Quarantine Attestation**. You will see the pending registration card for `compute-dev-node-01`.
4. Visually verify that the **3-Word Visual Confirmation Code** matches the worker terminal: `Tiger-Galaxy-77`.
5. Click **[Approve & Issue Lease]**.

Immediately upon clicking Approve:
* The server issues an approved secret lease.
* The worker terminal prints:
  ```text
  [2026-10-14 17:30:15] [INFO] Node lease approved! Stored credentials in /tmp/legatus-worker-dev.toml.
  [2026-10-14 17:30:15] [INFO] LCTP WebSocket connected to wss://localhost:8443/ws/worker. State: ONLINE.
  ```
* In Studio, `compute-dev-node-01` moves into the active **Fleet Topology** ring with a green `ONLINE` status badge.

---

## 6. Step 5: Submit and Execute a Task

You can now submit a serverless task for execution using `curl`:

```bash
curl -k -X POST https://localhost:8443/api/v1/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer dev-operator-token" \
  -d '{
    "function_id": "00000000-0000-0000-0000-000000000001",
    "input_json": "{\"message\": \"Hello from Legatus cluster!\", \"value\": 42}",
    "constraints": {
      "instruction_budget": 500000,
      "max_memory_bytes": 67108864,
      "timeout_ms": 5000
    }
  }'
```

**Expected Response:**
```json
{
  "task_id": "tsk_01HXYZ789ABCDEF012345678",
  "status": "QUEUED",
  "created_at": 1728907815000
}
```

---

## 7. Step 6: Verify Execution in Studio

1. In Legatus Studio, click **Task Explorer** in the left rail.
2. Select task `tsk_01HXYZ789ABCDEF012345678`.
3. View real-time instruction consumption meters and wall-clock execution metrics.
4. Expand the **Bottom Console Drawer** to view real-time standard output streamed directly from `legatus-runner`.
5. Verify the terminal state displays `COMPLETED` with exit code `0`.

---

## 8. Step 7: Clean Teardown

To stop the local cluster gracefully:
1. In the worker terminal, run `./worker/build/bin/legatus drain` to evacuate active runners.
2. Press `Ctrl+C` in the server terminal to cleanly flush database logs.
