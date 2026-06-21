claude --new-session \
  "Read .claude/context/PLAN.md fully. 
   Your role: Test Agent. Write failing tests ONLY — no implementation.
   Tech stack: JUnit 5, Mockito, Spring Boot Test.
   
   Handling Missing Implementations:
   - If a class, interface, or dependency required for a test scenario does not exist yet, you MUST create a minimal Mock class, interface, or use Mockito to mock its behavior within the test scope so the test code compiles.
   
   Test scenarios are in PLAN.md section 'Test Scenarios'.
   Output: tests under src/test/java/.
   
   When done, update .claude/context/HANDOFF.md with: 
   - tests written, test class names, what each covers, and any generated mock classes/interfaces."