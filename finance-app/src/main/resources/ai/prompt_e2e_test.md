# E2E launch prompt 

## Check before launching E2e 
### 1. Verify agent file is in the right place
ls .claude/agents/e2e-playwright.md

### 2. Start the Spring Boot app (separate terminal)
mvn spring-boot:run

### 3. Verify Node.js available (agent needs it for bootstrap)
node --version   # >=18 required

### 4. Confirm PLAN.md exists with E2E scenarios
cat .claude/context/PLAN.md

## Prompt
The unit tests are passing. Now delegate to the e2e-playwright subagent to:
- Read .claude/context/PLAN.md and HANDOFF.md
- Bootstrap the e2e/ directory if it doesn't exist
- Write Playwright tests for all acceptance criteria
- Run the tests and update HANDOFF.md with results
The app is running at http://localhost:8080.