# Meta-Guideline: Authoring Engineering Guidelines

## Document Metadata
* **Guideline ID:** GL-0004
* **Scope:** Universal / Meta-Standard (`docs/dev/guidelines/`)
* **Status:** Final Implementation Standard
* **Audience:** Core Systems Engineers, Platform Contributors, Documentation Stewards, Architecture Reviewers
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `REQUIRED`, `SHALL`, `SHALL NOT`, `SHOULD`, `SHOULD NOT`, `RECOMMENDED`, `MAY`, `OPTIONAL`)

---

## 1. Executive Summary & Purpose

Engineering guidelines define the constitution of the Legatus platform. If guideline documents are written haphazardly, inconsistently formatted, or lack normative precision, the engineering integrity of the entire distributed runtime collapses. Without a rigorous meta-standard governing the guidelines themselves, individual guidelines drift into subjective opinion pieces, omit critical failure mode analyses, or become ignored by contributors.

This meta-guideline establishes the non-negotiable structural requirements, review processes, lifecycle states, and normative standards governing the authoring, amendment, and retirement of all guideline documents within `docs/dev/guidelines/`. It enforces the canonical seven-section structure, RFC 2119 keyword rigor, and concrete Legatus system contextualization across the entire documentation suite.

---

## 2. Normative Invariants & Core Principles

Every developer guideline within `docs/dev/guidelines/` **MUST** adhere to the following binding invariants:

1. **Mandatory Seven-Section Layout:** Every guideline file **MUST** contain exactly the seven canonical sections defined in Section 3 of this specification, in the exact sequence specified. No section **MAY** be omitted, combined, or renamed.
2. **Deterministic Identifier Assignment:** Every guideline **MUST** be assigned a unique, sequential identifier formatted as `GL-XXXX` (e.g., `GL-0001`, `GL-0012`). Once allocated, a guideline identifier **SHALL NOT** be reassigned to another topic, even if the original guideline is deprecated.
3. **Strict RFC 2119/8174 Keyword Usage:** Normative requirements **MUST** use uppercase RFC 2119 keywords (**MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, **OPTIONAL**). The document metadata **MUST** explicitly cite RFC 2119 / RFC 8174 conformance.
4. **Implementation-Agnostic Normative Core:** Guidelines **MUST** establish invariants and standards that transcend temporary implementation details. They specify *how* engineering must be performed, *what* invariants must be maintained, and *why* specific failure modes must be avoided, without pasting ephemeral compiler build logs or transient debug sessions.
5. **Mandatory Concrete Counter-Examples:** Section 6 ("Anti-Patterns & Counter-Examples") **MUST** present at least three concrete, technical counter-examples illustrating flawed practices, their exact catastrophic failure modes in the Legatus distributed runtime, and their compliant corrections.
6. **Master Index Synchronization:** Any pull request adding, modifying, or deprecating a guideline **MUST** synchronously update `docs/dev/guidelines/README.md` (GL-0000) within the same commit.

---

## 3. Structural Specification & Schema

### 3.1 Guideline Lifecycle State Machine

A guideline document progresses through four deterministic states:

```
    ┌──────────┐
    │  DRAFT   │
    └────┬─────┘
         │ (PR opened, initial authoring complete)
         ▼
    ┌──────────┐
    │  REVIEW  │ ◄──────────────────────────────┐
    └────┬─────┘                                │
         │ (Two Core Maintainer sign-offs)      │ (Substantive amendment proposed)
         ▼                                      │
    ┌─────────────────────────────────┐         │
    │  FINAL IMPLEMENTATION STANDARD  │ ────────┘
    └────────────────┬────────────────┘
                     │ (Superseded by newer standard)
                     ▼
             ┌──────────────┐
             │  DEPRECATED  │
             └──────────────┘
```

1. **`DRAFT`**: Document is undergoing active composition. Not yet normative.
2. **`REVIEW`**: Document is undergoing formal review in a GitHub pull request.
3. **`FINAL IMPLEMENTATION STANDARD`**: Fully approved and merged into `master`. Legally binding on all code and architecture.
4. **`DEPRECATED`**: Replaced by a newer standard or rendered obsolete by architectural evolution. Preserved with a deprecation notice.

### 3.2 Canonical Seven-Section Specification

Every guideline document **MUST** adhere to the following schema:

1. **Title & Document Metadata:**
   * Level-1 header (`# <Title>`) followed immediately by the `## Document Metadata` block containing: `Guideline ID`, `Scope`, `Status`, `Audience`, and `Normative Conformance`.
2. **`## 1. Executive Summary & Purpose`:**
   * Concise statement of why the standard exists, the engineering failure modes it prevents, and the foundational guarantees it maintains across the Legatus ecosystem.
3. **`## 2. Normative Invariants & Core Principles`:**
   * Numbered list of non-negotiable rules. Every rule **MUST** employ bold uppercase RFC 2119 keywords.
4. **`## 3. Structural Specification & Schema`:**
   * The formal definitions, state machines, grammars, or tabular schemas governing the domain.
5. **`## 4. Production-Ready Template`:**
   * A complete, copy-pasteable template with bracketed placeholders and guidance comments.
6. **`## 5. Concrete Production Example`:**
   * A realistic, comprehensive example specifically contextualized to real Legatus components (`legatusd`, `legatus`, `server`, `studio`, `solix`, `reproc++`, `nng`).
7. **`## 6. Anti-Patterns & Counter-Examples (What NOT to Do)`:**
   * Itemized failure modes detailing flawed code/behavior, the specific operational catastrophe it causes, and the corrected pattern.
8. **`## 7. Verification & Compliance Checklist`:**
   * An actionable markdown task-list (`- [ ]`) specifying automated and manual review criteria.

---

## 4. Production-Ready Template

Engineers proposing a new guideline document **MUST** populate the following exact template:

```markdown
# [Title of Guideline]

## Document Metadata
* **Guideline ID:** GL-[XXXX]
* **Scope:** [Subsystems affected, or "Universal / Cluster-Wide"]
* **Status:** DRAFT
* **Audience:** [Target Engineering Roles]
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`)

---

## 1. Executive Summary & Purpose
<!-- 
Direct, uncompromising explanation of why this standard exists, the engineering 
failure modes it prevents, and the foundational guarantees it maintains across 
the Legatus ecosystem.
-->
[Provide executive summary here]

## 2. Normative Invariants & Core Principles
<!-- 
A numbered list of non-negotiable rules. Every rule MUST use RFC 2119 keywords 
in bold capital letters.
-->
1. **[Principle Name]:** Engineers **MUST** ...
2. **[Principle Name]:** Implementations **MUST NOT** ...
3. **[Principle Name]:** Subsystems **SHALL** ...

## 3. Structural Specification & Schema
<!-- 
The exact syntax, fields, format, or architectural layout that engineers must follow. 
Include tables, grammars, or state machines.
-->
[Provide structural specification and schema here]

## 4. Production-Ready Template
<!-- 
A fully usable, copy-pasteable Markdown/Text template containing bracketed placeholders, 
commented hints, and standard front-matter.
-->
[Provide production-ready template here]

## 5. Concrete Production Example
<!-- 
A realistic, comprehensive example specifically contextualized to Legatus 
(referring to legatusd, reproc++, SESSION_INIT, Argon2id, or Solix bytecode).
-->
[Provide concrete production example here]

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)
<!-- 
At least 3 realistic flawed patterns, explaining the exact technical failure 
mode or cluster catastrophe they cause, and providing the corrected form.
-->
### Anti-Pattern 1: [Flawed Pattern Name]
* **Flawed Practice:** [Description of mistake]
* **Failure Mode / Catastrophe:** [Technical catastrophe in distributed runtime]
* **Corrected Form:** [Compliant implementation or specification]

### Anti-Pattern 2: [Flawed Pattern Name]
* **Flawed Practice:** [Description of mistake]
* **Failure Mode / Catastrophe:** [Technical catastrophe in distributed runtime]
* **Corrected Form:** [Compliant implementation or specification]

### Anti-Pattern 3: [Flawed Pattern Name]
* **Flawed Practice:** [Description of mistake]
* **Failure Mode / Catastrophe:** [Technical catastrophe in distributed runtime]
* **Corrected Form:** [Compliant implementation or specification]

## 7. Verification & Compliance Checklist
<!-- 
A markdown task-list (- [ ]) providing automated linting rules or an engineer 
sign-off audit before merging.
-->
- [ ] **Structural Completeness:** All 7 canonical sections are present and titled correctly.
- [ ] **RFC 2119 Conformance:** Normative keywords are capitalized and strictly conform to RFC 2119.
- [ ] **Contextual Realism:** Examples reference real Legatus subsystems and protocols.
- [ ] **Master Index Updated:** `docs/dev/guidelines/README.md` includes this guideline.
```

---

## 5. Concrete Production Example

### Proposal for a New Guideline: GL-0012 (Cryptographic Key Management)

Below is an excerpt demonstrating how a new guideline is authored adhering strictly to this meta-standard:

```markdown
# Cryptographic Key Management & Secure Zeroing Specification

## Document Metadata
* **Guideline ID:** GL-0012
* **Scope:** Security & Cryptography (`worker/crypto/`, `server/security/`)
* **Status:** DRAFT
* **Audience:** Core Systems Engineers, Security Architects
* **Normative Conformance:** RFC 2119 / RFC 8174 (`MUST`, `MUST NOT`, `SHOULD`, `MAY`)

---

## 1. Executive Summary & Purpose
Legatus compute daemons (`legatusd`) and server brokers exchange Ed25519 node identities, Argon2id token digests, and TLS 1.3 session secrets. Storing private keying material in plain memory pages without explicit wiping creates severe side-channel vulnerabilities, core dump exfiltration vectors, and heap analysis exploits. This specification dictates the mandatory lifecycle, memory locking, and secure memory zeroing standards for all cryptographic secrets across C++20 and Java 21 components.

## 2. Normative Invariants & Core Principles
1. **Mandatory Secure Zeroing:** All memory buffers holding private keys or plaintext credentials **MUST** be sanitized using `explicit_bzero` (POSIX) or `SecureZeroMemory` (Windows) immediately upon release. Standard `memset` **MUST NOT** be used because it is subject to compiler dead-store elimination.
2. **Heap Pinning:** Sensitive key buffers in `legatusd` **SHOULD** be locked in RAM using `mlock()` / `VirtualLock()` to prevent paging to swap disks.
3. **Immutable Identity Invariant:** Compute node Ed25519 private keys **MUST** remain local to the node machine and **SHALL NOT** be transmitted across the wire protocol under any circumstance.
```

---

## 6. Anti-Patterns & Counter-Examples (What NOT to Do)

### Anti-Pattern 1: Section Omission or Renaming
* **Flawed Practice:** Creating a guideline that omits Section 6 ("Anti-Patterns & Counter-Examples") because the author believes "positive examples are sufficient."
* **Failure Mode:** Engineers repeatedly make the exact same design errors because the boundary between acceptable and catastrophic failure modes was never documented.
* **Corrected Approach:** Always include Section 6 with at least three detailed, real-world failure analyses.

### Anti-Pattern 2: Colloquial or Uncapitalized RFC Keywords
* **Flawed Practice:** Writing: "It is recommended that workers must sanitize memory."
* **Failure Mode:** Ambiguity during code review. Developers argue whether "must" in lowercase was a strict mandate or a polite suggestion, leading to unresolved security vulnerabilities in production.
* **Corrected Approach:** Use bold, capitalized keywords: "Workers **MUST** sanitize memory using `explicit_bzero`."

### Anti-Pattern 3: Abstract or Fictional Examples
* **Flawed Practice:** Using generic `FooBarService` and `ExampleWidget` in Section 5 instead of real Legatus architecture.
* **Failure Mode:** Contributors struggle to apply abstract concepts to concrete components (`legatusd`, `server`, `reproc++`), leading to misinterpretations during implementation.
* **Corrected Approach:** Ground every example in actual Legatus subsystems, protocols, and interfaces.

---

## 7. Verification & Compliance Checklist

Authors and maintainers **MUST** verify the following checklist before approving any guideline PR:

- [ ] **Sequential ID Allocated:** Guideline ID follows `GL-XXXX` and is strictly sequential.
- [ ] **Metadata Block Present:** Document header contains all five required metadata fields.
- [ ] **Exact 7 Sections:** The document includes sections 1 through 7 with identical naming and ordering.
- [ ] **RFC 2119 Capitalization:** All normative rules utilize bold, capitalized RFC 2119 keywords.
- [ ] **Three Real Anti-Patterns:** Section 6 contains at least three concrete failure modes with catastrophe descriptions and corrected forms.
- [ ] **Copy-Paste Template Included:** Section 4 contains an unabbreviated, usable template.
- [ ] **Master Index Updated:** `docs/dev/guidelines/README.md` table includes the new guideline ID and details.
