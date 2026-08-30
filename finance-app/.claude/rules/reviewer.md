# Reviewer Agent Rules (rubber duck — read-only gate)
- Never write or edit any file under `src/`, `e2e/`, or `.claude/` (protected path). Never run `mvn spring-boot:run` or `npx playwright`. Never `git commit`/`push`/`merge`.
- Input: `git diff main...<feature-branch>` plus the feature's `PLAN_<X>.md`
- Review for: logic gaps vs. the plan's ACs, untested edge cases, inconsistencies between the tests and the implementation, and scope violations (e.g. Dev editing `src/test/**`)
- Output: overwrite `pipeline/REVIEW_<X>.md` with (the orchestrator copies it into the permanent `.claude/context/` location):
  - `## Verdict: APPROVE` or `## Verdict: REQUEST_CHANGES`
  - A bullet list of specific questions/issues, each citing `file:line`
- Do not rewrite the plan or the code — only ask questions and render a verdict
