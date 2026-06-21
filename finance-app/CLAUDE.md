# Project Rules
- NEVER read, search, or index files inside the `target/` directory. 
- All build artifacts and compiled classes are irrelevant to coding tasks. Focus entirely on `src/`.

## Project Map & Commands

### Stack & Config
* **Backend:** Java 21, Spring Boot 3.3, Spring Security, Maven, Embedded Tomcat (Port 8080)
* **Frontend:** Thymeleaf templates (`src/main/resources/templates/`: `login.html`, `dashboard.html`), Static CSS (`static/css/style.css`)
* **Persistence:** No DB. Local CSV file at `~/.finance-app/data/users.csv`
* **Properties:** `src/main/resources/application.yml` (Port 8080, CSV path, default admin creds: `admin123`)

### Architecture (`src/main/java/com/finance/app/`)
* `domain/[model|exception|port/in|port/out]` — Core domain logic and interfaces.
* `application/service/` — Use case implementations (Zero framework dependencies).
* `infrastructure/adapter/in/[web|security]` — Web and Security entry points.
* `infrastructure/adapter/out/[persistence|security]` — `CsvFileAdapter` for `User`, security providers.
* `infrastructure/config/` — Framework configuration.

### Lean Commands
* **Quiet Build:** `mvn clean compile -q`
* **Isolate Test Failures:** `mvn test | grep -E "FAILURE|ERROR" -A 5`
* **Run App:** `mvn spring-boot:run`
* **Silent Frontend Build:** `npm run build -- --silent`
* **Short Git Status:** `git status -s`

### E2E Testing
- E2E tests: Node.js project under e2e/
- Runner: `npx playwright test` (from e2e/ directory)
- NOT the Java Playwright port — never add com.microsoft.playwright to pom.xml
- Run backend first: `mvn spring-boot:run` then `cd e2e && npx playwright test`

## Context Files
- Feature plan: @.claude/context/PLAN.md — active plan for the current iteration; overwritten per feature.
- Plan template: @.claude/context/PLAN_template.md — read this before generating a new PLAN.md; never modify.
- Handoff status: @.claude/context/HANDOFF.md