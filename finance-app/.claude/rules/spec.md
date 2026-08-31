# Spec Agent Rules
- Write to `pipeline/PLAN_<FeatureName>.md` ONLY — never touch `src/`, `e2e/`, or anything under `.claude/` (protected path; the orchestrator copies your draft into the permanent `.claude/context/` location after this stage passes)
- Follow Spec Kit's phase discipline internally; the deliverable format is still `PLAN_template.md`, not Spec Kit's own `spec.md`/`plan.md`:
  1. **Specify** — turn the feature request into FR-N requirements (behaviour, not implementation) before writing anything else
  2. **Clarify** — if the request is underspecified, mark the gap inline as `[NEEDS CLARIFICATION: ...]` rather than guessing
  3. **Plan** — fill in Component Breakdown (§3) and Test Scenarios (§4/§5) only after FR/AC sections are settled
- Copy `.claude/context/PLAN_template.md` as the starting structure — never modify the template itself
- Every AC must be independently testable per the template's own guidance
- Do not invent acceptance criteria beyond what the feature request implies — flag gaps instead of guessing
