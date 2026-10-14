# Legatus Studio Subsystem

## Component Metadata
* **Component:** `studio` (`legatus-studio`)
* **Language & Standard:** OpenJDK / Eclipse Temurin Java 21 LTS + OpenJFX 21
* **UI Design System:** AtlantaFX (`PrimerDark`, `NordDark`) + Ikonli Feather Icons
* **Build System:** Apache Maven 3.9+ / `jpackage` (`studio/pom.xml`)
* **Normative Specification:** [`docs/specs/studio-spec.md`](../docs/specs/studio-spec.md)
* **Wire Protocol:** [`docs/specs/protocols/studio-wire.md`](../docs/specs/protocols/studio-wire.md)
* **Operator Manual:** [`docs/guides/04-studio.md`](../docs/guides/04-studio.md)
* **Cockpit Wireframe:** [`docs/architecture/wireframes/studio/studio-wireframe.md`](../docs/architecture/wireframes/studio/studio-wireframe.md)

---

## 1. Overview & Architecture

The `studio/` subsystem is the native desktop telemetry cockpit and administrative console for the Legatus platform:

1. **60Hz Telemetry Coalescing:** Lock-free ring buffers buffer incoming high-throughput WebSocket frames, flushing UI property updates via `Platform.runLater()` at a strict $16.6\text{ ms}$ ($60\text{ Hz}$) cadence to prevent UI thread stutter.
2. **Viewport Subscription Scoping:** Implements `viewport_filter` criteria in `STUDIO_SESSION_INIT`, throttling node state updates strictly to the visible fleet table view.
3. **Visual Quarantine Attestation:** Side-by-side modal rendering node hardware specs, colon-separated SHA-256 fingerprints, and highlighted 3-word visual confirmation codes (`Tiger-Galaxy-77`).
4. **Bounded Task Console:** Live `stdout`/`stderr` streaming tabs backed by circular ring buffers capped at 10,000 lines (or 8 MB of UTF-8 text) with FIFO eviction.
5. **OS Keychain Security:** Encrypts saved operator refresh tokens and cluster connection profiles in `studio.json` using AES-256-GCM backed by native OS keystores (`libsecret`, macOS `Keychain`, Windows `DPAPI`).

---

## 2. Directory Layout

```text
studio/
├── pom.xml                    # Maven POM build and jpackage configuration
├── CHANGELOG.md               # Release Please component changelog
├── README.md                  # Subsystem overview (This file)
├── src/
│   ├── main/
│   │   ├── java/dev/legatus/studio/
│   │   │   ├── app/           # JavaFX Application entry point and lifecycle
│   │   │   ├── model/         # Observable JavaFX properties and domain records
│   │   │   ├── net/           # HttpClient REST and WebSocket client (Project Loom)
│   │   │   ├── security/      # OS Keychain integration (libsecret, Keychain, DPAPI)
│   │   │   ├── telemetry/     # Lock-free ring buffers and 60Hz frame coalescers
│   │   │   └── view/          # View controllers and custom AtlantaFX controls
│   │   └── resources/
│   │       ├── fxml/          # FXML views (MainCockpit, QuarantineModal, TaskExplorer)
│   │       ├── css/           # Custom AtlantaFX stylesheet extensions
│   │       └── icons/         # Application vector assets
│   └── test/                  # TestFX and JUnit 5 headless UI tests
```

---

## 3. Building & Testing

### 3.1 Run Directly from Source
```bash
mvn javafx:run
```

### 3.2 Running Automated Headless Tests (TestFX & Monocle)
```bash
mvn clean test -Dheadless=true
```

### 3.3 Building Native Installers (`jpackage`)
```bash
mvn clean package -Pnative-package
```
*Outputs generated in `target/dist/`:*
* Linux: `.deb` / `.rpm`
* macOS: `.dmg` / `.pkg`
* Windows: `.msi` / `.exe`
