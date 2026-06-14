claude --new-session \
  "Read .claude/context/PLAN.md fully. 
   Your role: Test Agent. Write failing tests ONLY — no implementation.
   Tech stack: JUnit 5, Mockito, Spring Boot Test.
   Test scenarios are in PLAN.md section 'Test Scenarios'.
   Output: tests under src/test/java/.
   When done, update .claude/context/HANDOFF.md with: 
   - tests written, test class names, what each covers."