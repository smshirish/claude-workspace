# Implementation Plan: [Feature Name]

<!-- TEMPLATE: Replace [Feature Name] with a short noun phrase: "Account CSV Validation", "User Password Reset", "Dashboard Charts". -->

---

## 1. Feature Requirements

<!-- TEMPLATE: List every functional requirement as a numbered FR-N entry.
  - Tier structure is optional — use it when the feature has fail-fast / accumulative / reporting layers (e.g. validation pipelines).
  - For single-concern features (e.g. "add a new field"), one FR entry is fine.
  - Each FR must describe BEHAVIOUR (what the system must do), not implementation (how to code it).
  - Front-end-only features: describe UI/UX behaviour. Back-end-only: describe API / domain behaviour. Full-stack: describe both.
  - Include tables for structured rules (allowed enum values, column contracts, etc.) when they reduce ambiguity.
-->

### FR-1: [Short title of first requirement]
<!-- TEMPLATE: Describe the primary behaviour in plain language.
  Example structure:
    - Trigger / precondition
    - Expected system behaviour (happy path)
    - Constraints / invariants (what is tolerated, what is rejected)
    - Behavior on failure (stop immediately vs accumulate errors)
-->

### FR-2: [Short title of second requirement]
<!-- TEMPLATE: Add as many FR-N blocks as needed. Delete unused ones.
  Typical breakdown for a validation feature: schema-level → row-level → error reporting.
  Typical breakdown for a CRUD feature: create → update → delete → read/display.
-->

### FR-3: [Short title of third requirement]
<!-- TEMPLATE: For front-end-only features, FRs describe UI states, component behaviour, and user interactions.
  For back-end-only features, FRs describe domain rules, exception contracts, and service behaviour.
  Omit this block entirely if the feature only needs two requirements.
-->

---

## 2. Acceptance Criteria

<!-- TEMPLATE: Each AC must be independently testable — a QA engineer or automated test should be able to read it and know exactly what to verify.
  Format: "A [subject] with [condition] [does/returns/shows] [observable outcome]."
  Map ACs back to FRs implicitly through naming (schema ACs → FR-1, row ACs → FR-2, etc.).
  One row per observable outcome. Do NOT merge two distinct outcomes into one AC.
  Front-end ACs: reference visible UI elements (testids, text, visibility).
  Back-end ACs: reference return types, thrown exceptions, model attributes.
-->

| ID | Criterion |
|---|---|
| AC-1 | [Happy path: valid input produces the expected successful outcome] |
| AC-2 | [First failure case: describe input, describe observable result] |
| AC-3 | [Edge case: boundary or tolerance condition] |
| AC-4 | [Add rows as needed — delete unused rows] |

---

## 3. Component Breakdown

<!-- TEMPLATE: List every class/file that must be created or modified, grouped by architectural layer.
  Use the project's package structure: domain → application → infrastructure/adapter.
  For each component include:
    - Full relative package path (e.g. domain/exception/FooException)
    - Whether it is NEW or MODIFIED
    - A one-line description of its responsibility
    - Key methods / fields if they are non-obvious
    - For modified components: the current flow vs. the new flow
  Front-end-only features: sections 3.1–3.3 will be empty — skip them and start at 3.4/3.5.
  Back-end-only features: skip 3.5 (web adapter) and 3.6 (template) if no UI is touched.
  Always include ALL layers that are touched, even if the change is small (e.g. a one-line controller change).
-->

### 3.1 New Domain Exceptions
<!-- TEMPLATE: List any new exception classes under domain/exception/.
  Pattern: ClassName (extends ParentException) — what condition it signals, what payload it carries.
  Example: CsvSchemaException (extends AccountImportException) — holds String schemaError.
  Skip this section if no new exceptions are needed. -->

### 3.2 New Domain Value Objects / Records
<!-- TEMPLATE: List new immutable value types (Java records, enums, DTOs) under domain/model/.
  Show the record/class signature and describe each field.
  Skip if none. -->

### 3.3 New / Modified Domain Services
<!-- TEMPLATE: List pure domain classes under domain/service/ or application/service/.
  For each:
    - Class name + layer
    - Method signatures (inputs → outputs)
    - Business rules each method enforces
    - Any collaborating domain classes called
  "Pure domain" means zero Spring/framework imports.
  Skip or merge with 3.4 for simple features. -->

### 3.4 Modified Infrastructure Adapters (Out-bound / Persistence)
<!-- TEMPLATE: List any changes to infrastructure/adapter/out/ (CSV parsers, DB repos, external API clients).
  Show: current flow → new flow using numbered steps.
  Call out what is removed, what is reused, and what is superseded.
  Skip if no persistence layer is touched. -->

### 3.5 Modified Web Adapters (In-bound / Controllers)
<!-- TEMPLATE: List any changes to infrastructure/adapter/in/web/ controllers.
  Show: current catch/return structure → new structure.
  List every model attribute added, removed, or renamed.
  Skip if no controller is touched (back-end-only with no HTTP surface, or front-end-only via static serving). -->

### 3.6 Template / UI Changes
<!-- TEMPLATE: List every Thymeleaf template (or static asset) changed.
  For each template block added or modified:
    - Show the HTML snippet with all required data-testid attributes (see thymeleaf-templates.md rules).
    - Describe the th:if / th:each condition and what model attribute drives it.
  Skip entirely for back-end-only features with no UI surface.
  For pure front-end features, this section may be the ONLY section with content. -->

---

## 4. Test Scenarios

<!-- TEMPLATE: Cover every layer touched by the feature.
  Rule of thumb: one test class per new service/validator; augment existing test classes for modifications.
  Test IDs must be unique across all tables in this section (S-*, R-*, P-*, C-*).
  For back-end-only features: Unit + Integration + MockMvc tables are sufficient; skip E2E (Section 5).
  For front-end-only features: MockMvc may be thin; E2E (Section 5) carries the weight.
  For pure domain features: Unit table is primary; Integration and MockMvc may be empty — delete empty tables.
-->

### Unit — `[TestClassName]`
<!-- TEMPLATE: Test class name matches the class under test + "Test" suffix.
  "Input" column: describe the specific input or state (not "valid input" — be precise).
  "Expected" column: describe the exact return value or exception thrown.
  Add one table per new domain service/validator being unit-tested.
-->
| # | Input | Expected |
|---|---|---|
| S-1 | [Specific input description] | [Exact return or exception] |
| S-2 | [Edge case input] | [Exception type + message content] |

### Unit — `[SecondTestClassName]`
<!-- TEMPLATE: Add this block only if a second domain class needs its own unit test table. Delete if not needed. -->
| # | Input | Expected |
|---|---|---|
| R-1 | [Input description] | [Expected outcome] |

### Integration — `[TestClassName]`
<!-- TEMPLATE: Integration tests for infrastructure adapters (parsers, repositories, file adapters).
  "Input" is typically a file/stream/DTO fed to the adapter.
  "Expected" is the return value or exception propagated up.
  Explicitly call out which existing tests change behaviour (mark as "replaces T-x.y").
  Delete this table if no infrastructure adapter is modified. -->
| # | Input | Expected |
|---|---|---|
| P-1 | [Input description] | [Expected return or exception] |

### MockMvc — `[TestClassName]`
<!-- TEMPLATE: Controller-layer tests using Spring MockMvc.
  "Scenario" column: describe the HTTP request + the exception the mocked use-case throws.
  "Expected model attributes" column: list which attributes are present/absent after the request.
  One row per exception type the controller must handle.
  Delete this table if no controller is modified. -->
| # | Scenario | Expected model attributes |
|---|---|---|
| C-1 | [Use case throws XException] | `[attributeName]` present; `[otherAttribute]` absent |

---

## 5. E2E Test Plan

<!-- TEMPLATE: Include this section ONLY if the feature has a UI surface (Thymeleaf templates or static pages).
  For back-end-only features: delete this entire section.
  The E2E plan must be runnable with the existing Playwright + TypeScript setup in e2e/ — do not add new test runners.
-->

### 5.1 Framework & Configuration
<!-- TEMPLATE: State:
  - Test file name and location (e.g. e2e/tests/[feature].spec.ts) — new file or added to existing?
  - Shared helpers reused from existing specs (login(), writeTempCsv(), etc.)
  - New helper files introduced (e.g. csvFixtures.ts, formHelpers.ts)
  - Any new npm packages required (state "none" explicitly if not)
  - Any existing test files that require modification due to this feature (breaking changes)
-->

**Framework:** Playwright + TypeScript — existing setup in `e2e/` (no new dependencies).

**Test file:** `e2e/tests/[feature].spec.ts`

**Shared helpers:** [List helpers reused from existing specs, e.g. `login()`, `writeTempCsv()`]

**New helpers:** [List new helper files, or "None"]

**No new packages required.** [Update this line if packages are needed]

**Breaking changes in existing tests:** [List tests that break, or "None"]

---

### 5.2 Test Fixtures / Data Inventory

<!-- TEMPLATE: List every fixture (CSV file, JSON payload, form state, seed data) needed.
  For CSV-based features: one row per named fixture constant exported from a helpers file.
  For form-based features: describe each form state (valid, missing field, invalid value, etc.).
  For API-based features: list request payloads.
  Column "Purpose" must explain WHY this fixture exists — what scenario it enables.
-->

| Constant / Fixture | Valid? | Issues | Purpose |
|---|---|---|---|
| `VALID_[FIXTURE]` | Yes | None | Happy path |
| `[ERROR_FIXTURE]` | No | [Describe defect] | [Scenario it tests] |

---

### 5.3 E2E Test Cases — [Feature Group Name]

<!-- TEMPLATE: Group tests by feature area using describe() blocks.
  Annotation line: "> Maps to FR-N, AC-N, AC-N. All tests in describe('[Group Name]')."
  "CSV Fixture" column → rename to "Fixture / State" for non-CSV features.
  "Assertions" column: use exact testid selectors and state (visible/absent/count/text contains).
  Follow testid conventions from thymeleaf-templates.md (data-testid="[feature]-[element]").
  Add additional 5.x subsections for each distinct describe() group.
-->

> Maps to FR-1, AC-1, AC-2. All tests in `describe('[Feature Group Name]')`.

| ID | Scenario | Fixture / State | Assertions |
|---|---|---|---|
| E2E-1 | [Scenario description] | `[FIXTURE_CONSTANT]` | `[data-testid="..."]` visible; `[data-testid="..."]` absent |
| E2E-2 | [Scenario description] | [Form state / fixture] | [Observable UI outcome with exact selectors] |

---

### 5.4 E2E Regression — Existing Tests That Must Be Updated

<!-- TEMPLATE: List every existing E2E test that breaks because this feature changes the UI or behaviour it was asserting.
  "Change required": describe the OLD assertion and the NEW assertion — be specific about selector or text changes.
  If no existing tests are affected, write "No existing E2E tests are affected by this feature." and delete the table.
-->

| Existing test | Change required |
|---|---|
| [Test name in existing spec file] | Change assertion from `[old-selector]` to `[new-selector]`; update description |

---

## 6. Out of Scope

<!-- TEMPLATE: Explicitly list things that are NOT part of this feature.
  This section prevents scope creep and documents deliberate decisions.
  Each row must include a REASON — "not required" alone is insufficient.
  Typical candidates:
    - Related features deferred to a follow-on ticket
    - Infrastructure concerns excluded by design (auth, rate limiting, file size)
    - i18n / accessibility / mobile responsiveness if not required
    - Cross-field or cross-entity validation rules not specified
  A well-filled Out-of-Scope section is as important as the requirements above.
-->

| Item | Reason |
|---|---|
| [Deferred or excluded concern] | [Why it is not part of this iteration] |
| [Another excluded concern] | [Reason] |
