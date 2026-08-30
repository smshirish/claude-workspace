# AI-Assisted Development Workflow

This project is built iteratively using LLM agents (Claude Code). This document describes the conventions, agent roles, and context files that make that workflow repeatable.

---

## Context Files

| File | Purpose | Changes when |
|---|---|---|
| `finance-app/CLAUDE.md` | Tool rules, lean build commands, testing constraints — auto-loaded by Claude Code | Tool conventions change |
| `finance-app/PROJECT.md` | Product vision and doc index | Vision changes |
| `finance-app/docs/` (this folder) | Architecture, project plan, this workflow doc | Each doc has its own cadence |
| `.claude/context/HANDOFF.md` | Live implementation status: what's done, what's failing, what's next | After every agent session |
| `.claude/context/PLAN.md` | Active feature plan (overwritten per feature) | Start of each new feature |
| `.claude/context/PLAN_template.md` | Template for generating new plans — **never modify** | — |
| `.claude/context/PLAN_[X]_[Feature].md` | Permanent archived plan for a completed or in-progress feature | Stays fixed after creation |
| `.claude/rules/testing.md` | Constraints for the test agent | When test conventions change |
| `.claude/rules/thymeleaf-templates.md` | Thymeleaf `data-testid` conventions | When UI conventions change |

Completed use case plans are archived under `src/main/resources/ai/completed_usecases/`.

---

## Feature Delivery Sequence

Each feature follows this fixed sequence. Agents hand off via `HANDOFF.md`.

```
1. Plan Agent    → Produces PLAN.md
                   (requirements, acceptance criteria, component breakdown,
                    unit/MockMvc/E2E test scenarios)

2. Dev Agent     → Implements production code in src/main/java only
                   Never touches src/test or e2e/

3. Test Agent    → Writes unit + MockMvc tests in src/test/java only
                   Never edits src/main; if a class is missing, stubs an interface

4. E2E Agent     → Writes Playwright specs in e2e/tests/
                   Runs against live backend on http://localhost:8080

5. Verify        → All tests green → HANDOFF.md updated → plan archived
```

---

## Agent Constraints

### Dev Agent
- Writes to `src/main/java` only.
- Never modifies test files.

### Test Agent
- Writes to `src/test/java` only.
- Never runs the application — only `mvn test`.
- If a required class does not exist, create a stub interface; do not implement it.
- Does not generate tests for value objects / data records.

### E2E Agent
- Uses Node.js Playwright (`@playwright/test`) — never the Java Playwright port.
- Backend must be running (`mvn spring-boot:run`) before tests execute.
- Run from the `e2e/` directory: `npx playwright test`.

---

## Interaction Style

When working with the LLM interactively (not in a fully automated agent run), follow this style:

- **Plan before generating.** Show a list of all classes / components you intend to create and get confirmation before writing any code.
- **Generate one class at a time.** After showing the plan, produce each class individually and pause for feedback before moving to the next.
- **Tests before implementation.** Write unit tests first, present them for review, then write the production class. Take feedback on each before continuing.
- **Architecture questions.** When asked to understand the codebase, analyse the code and provide a high-level architecture overview before proposing any changes.

---

## PLAN.md Structure

Plans follow the template at `.claude/context/PLAN_template.md` exactly. Sections:

1. Feature Requirements (FR-N)
2. Acceptance Criteria (AC-N)
3. Component Breakdown (domain exceptions → value objects → services → adapters → controllers → templates)
4. Test Scenarios (unit, MockMvc, integration tables)
5. E2E Test Plan (framework config, fixtures, test cases, regression impact)
6. Out of Scope

---

## UI Conventions

- Every interactive element carries `data-testid="[feature]-[element]"`.
- Dynamic rows: `th:attr="data-testid='account-row-' + ${account.accountId()}"`.
- Conditional blocks: `data-testid` on the outermost rendered element.
- Never use `th:field`-generated `name` attributes as Playwright selectors.

---

## Token Optimisation Rules

(Kept here so agents pick them up — also enforced in `CLAUDE.md`.)

- No verbose flags (`-v`, `--debug`) unless explicitly requested.
- Truncate logs > 40 lines — isolate errors with `grep`, `head`, `tail`.
- Only output/analyse failures. Suppress success output.
- Read specific line ranges rather than full files when targeting a function.
- Never run `ls -R` or `find .` for discovery — use the project map in `CLAUDE.md`.
