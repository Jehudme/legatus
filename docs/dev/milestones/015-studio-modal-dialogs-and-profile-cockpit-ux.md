# Milestone 015: Studio Modal Dialogs, Unified Execution Profiles & Quarantine Cockpit UX

## Document Metadata
* **Milestone ID:** MS-015
* **Target Version:** `v1.11.0-studio-cockpit-ux`
* **Status:** COMPLETED
* **Lead Architect:** @ux-lead & @client-lead
* **Target Delivery Date:** 2027-06-15
* **Actual Delivery Date:** 2026-10-16
* **Related ADRs:** ADR-0002, ADR-0005, ADR-0006

---

## 1. Executive Summary & Objectives

The comprehensive audit and live operator testing highlighted key ergonomics regressions and layout clutter introduced in Milestone 011:
1. **Cluttered Inline Panels vs Sleek Modal Dialogs:** Functions and Execute Workbench were split into 3 persistent side-by-side columns (Master List, Create Panel, Inspect Panel). This cramped horizontal screen space and clashed with the modern, intuitive AtlantaFX modal dialog UX established in Admin Users (`[+ New Account]`).
2. **Execution Lockout & Re-Submission Failure:** After executing a function once, the Execute Workbench locked out subsequent executions due to stuck button state and mismatched monitored task IDs. Furthermore, profiles failed to auto-select their associated function in dropdowns.
3. **Execution Profile Architectural Definition:** An Execution Profile is the complete execution template—binding the target `function_id`, config parameters, instruction/memory budgets, timeout, priority, and JSON arguments. The workbench layout must present a clean master list of available profiles and a dedicated inspection & execute workbench.
4. **Quarantine Experience Ergonomics:** The quarantine attestation interface must provide a clean, non-intrusive attestation card with inline friendly name configuration, hardware specifications, Ed25519 fingerprint, and 3-word visual code, completely free of fullscreen popups.
5. **Universal WebSocket Prepending:** Newly added functions, execution profiles, workers, and tasks must be prepended at the top (index 0) of observable lists in real time.

This milestone restores layout elegance by implementing AtlantaFX modal dialogs for New Function and New Profile, streamlines the Execution Workbench for repeated dispatch, and polishes the Quarantine Attestation experience.

---

## 2. Scope & Subsystems Affected

* **`studio/` (`legatus-studio` - Java 21 LTS / JavaFX 21 / AtlantaFX PrimerDark):**
  * `resources/com/legatus/studio/functions_workbench.fxml`: Redesigned into a clean 2-column layout: Functions Master Table on the left and Function Metadata Inspector on the right; added `[+ New Function]` header button and modal overlay.
  * `resources/com/legatus/studio/execute_workbench.fxml`: Redesigned into a clean 2-column layout: Profiles Master Table on the left and Dedicated Execution & Output Workbench on the right; added `[+ New Profile]` header button and modal overlay.
  * `controllers/FunctionsWorkbenchController.java`: Implemented `newFunctionModal` popup with drag-and-drop bytecode file selection, reactive upload button disable binding, and `.solx` magic header validation (`0x534F4C58`); removed obsolete 1h invocations column.
  * `controllers/ExecuteWorkbenchController.java`: Implemented `newProfileModal` popup with live JSON syntax validation; fixed function dropdown auto-selection from `profile.getFunctionId()`; guaranteed `executeButton` re-enables immediately for repeated dispatch.
  * `controllers/QuarantineAttestationController.java`: Polished inline attestation card with hardware specs, Ed25519 fingerprint, visual confirmation code card, friendly name field, and auto-accept banner.
  * `viewmodel/FunctionsViewModel.java` & `ExecutionProfileViewModel.java`: Enforced reactive top-of-list prepending at index 0 and schema validation.

---

## 3. Prioritized Deliverables & Task Matrix

### 3.1 P0: Critical Blockers (Release Gate)
- [x] `[P0]` **Modal Dialog Popup for New Function:** Implement an AtlantaFX modal dialog overlay for creating new functions, matching the `AdminUsersController` pattern. Includes Function Name, Entry Point, Bytecode file chooser / drag-and-drop target, `.solx` magic header validation (`0x534F4C58`), and auto-dismiss on successful upload.
- [x] `[P0]` **Modal Dialog Popup for New Profile:** Implement an AtlantaFX modal dialog overlay for creating new execution profiles. Includes Profile Name, Target Function selector, JSON Arguments area with live syntax validation, Instruction Budget (with unlimited toggle), Timeout (with unlimited toggle), Memory limit, Priority dropdown, and Notify on failure checkbox.
- [x] `[P0]` **Execute Workbench Repeated Execution Fix:** Ensure that clicking `[Execute Profile]` dispatches the task, updates status badges, and immediately re-enables `executeButton` upon dispatch confirmation, allowing operators to execute the same or modified profile repeatedly without reloading views.
- [x] `[P0]` **Execution Profile Function Resolution:** Ensure that selecting a profile in the master table automatically resolves and selects the matching `BytecodeItem` in the target function dropdown based on `profile.getFunctionId()`.

### 3.2 P1: High Priority (Milestone Core)
- [x] `[P1]` **Streamlined Functions Workbench 2-Column Layout:** Redesign `functions_workbench.fxml` into a 2-column view: Functions Master Table (Function Name, Entry Point, Size, SHA-256, Created At) on the left, and Selected Function Detail Card on the right. Remove cluttered inline create panel and obsolete 1h invocations column.
- [x] `[P1]` **Streamlined Execution Workbench 2-Column Layout:** Redesign `execute_workbench.fxml` into a 2-column view: Profiles Master Table (Profile Name, Function Name, Priority, Budget) on the left, and Inspect & Execute Workbench (Config fields, JSON arguments, status badge, execution progress, terminal area) on the right.
- [x] `[P1]` **Polished Quarantine Attestation UX:** Streamline `quarantine_attestation.fxml` with an inline `friendlyNameField` inside the Attestation Card, hardware specifications, Ed25519 fingerprint, highlighted 3-word visual confirmation code, and auto-accept banner.
- [x] `[P1]` **Universal Top-of-List Prepending Invariant:** Enforce that newly registered functions, execution profiles, and historical tasks are always prepended at index 0 in observable collections across all ViewModels.

### 3.3 P2: Medium Priority (Ergonomics & Hardening)
- [x] `[P2]` **Profile JSON Import/Export Validation:** Validate imported profile JSON schemas against `ExecutionProfileDto` before adding them to the profiles table, displaying descriptive error alerts on invalid schemas.
- [x] `[P2]` **Bytecode Magic Header Pre-Upload Verification:** Read the first 4 bytes of selected bytecode files before network submission. If not `0x534F4C58` ("SOLX"), display an inline validation error: "Selected file is not valid compiled Solix bytecode."

---

## 4. Test Strategy Overhaul & Verification Matrix

Previously, UI dialogs and modal workflows were completely untested in automated builds. This milestone introduced comprehensive headless UI automation testing:

### 4.1 Test Refactoring & New Test Deliverables

| Component Under Test | Flawed Old Test Practice | Refactored / New Test Suite | Verification Scenario | Status |
| :--- | :--- | :--- | :--- | :--- |
| **New Function Modal Dialog** | File upload tested via raw mock REST client | `NewFunctionModalWorkflowTest.java` | Clicks `[+ New Function]`. Asserts modal dialog opens. Inputs valid name and entry point. Attaches valid `.solx` file. Submits. Verifies modal closes and new function appears at index 0 of table. | PASS (100%) |
| **New Profile Modal Dialog** | Tested POJO DTO mapping only | `NewProfileModalWorkflowTest.java` | Clicks `[+ New Profile]`. Enters profile details and valid JSON arguments. Submits. Verifies modal closes, profile appears at index 0, and function is resolved. | PASS (100%) |
| **Repeated Execution Flow** | Tested single submit call once | `ExecuteWorkbenchRepeatTest.java` | Selects profile. Dispatches execution 5 times consecutively without view reloading. Asserts each dispatch returns unique task ID, status updates, and button re-enables immediately. | PASS (100%) |
| **Bytecode Header Pre-Validation** | Untested; triggered raw 400 REST errors | `BytecodeHeaderValidationTest.java` | Attempts upload of arbitrary text file. Asserts pre-validation catches invalid magic bytes and prevents REST dispatch. | PASS (100%) |
| **Quarantine Inline Attestation** | Untested; fullscreen dialog blocked CI | `QuarantineAttestationWorkflowTest.java` | Selects pending worker. Modifies friendly name field inline. Clicks Approve. Verifies approval payload and removal from quarantine list. | PASS (100%) |

---

## 5. Definition of Done (DoD) Sign-Off Matrix

| DoD Requirement | Target Benchmark | Verification Tool | Sign-off Status |
| :--- | :--- | :--- | :--- |
| **Headless UI Suite** | 100% pass across all dialog & workflow tests | `mvn test -Dtest=*WorkflowTest,*RepeatTest` | [x] COMPLETED |
| **Visual Polish** | Modal dialogs conform to AtlantaFX PrimerDark theme | FXML layout & style verification | [x] COMPLETED |
| **Zero Layout Clutter** | Side-by-side 2-column layouts in Functions and Execute | FXML layout verification | [x] COMPLETED |
| **Living Specs Updated** | Milestone documentation signed off | Documentation audit | [x] COMPLETED |
