# Project Guidelines

## Token Optimization Constraints

### 1. Execution & Logs
* **No Verbose Output:** Never use `-v`, `--debug`, or `--verbose` flags unless explicitly requested.
* **Truncate Logs:** If output > 40 lines, isolate errors/stack traces using `grep`, `head`, `tail`, or `awk`. Do not ingest successful logs.
* **Build/Test Drops:** Only output/analyze failures (file paths, line numbers, errors). Suppress success output.
* **No Discovery Loops:** Do not run `ls -R` or `find .`. Use the Project Map below.

### 2. Context Hygiene
* **Proactive Compaction:** Prompt user for `/compact` immediately after analyzing large build outputs or major bug fixes.
* **Targeted Reading:** Read specific line ranges instead of full files whenever looking for targeted functions/classes.
* **Strict Scope:** Never review or refactor code outside the immediate task.

---

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

---

## Response Style
* **Concise Diffs:** Output only the specific lines/functions modified. Avoid rewriting unchanged blocks.
* **No Fluff:** Omit pleasantries, intros, and conversational explanations. Rely on code and short inline comments.