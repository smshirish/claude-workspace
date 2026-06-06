# Project Guidelines (Token-Optimized)

## Critical Constraints for Token Management

### 1. Command Execution & Output Handling
* **No Verbose Outputs:** Never run commands with verbose (`-v`, `--debug`, `--verbose`) flags unless explicitly requested.
* **Truncate Logs:** If a command output exceeds 40 lines, do not read or output the entire stream. Use tailored tools (e.g., `grep`, `head`, `tail`, `awk`) to isolate relevant stack traces, errors, or changes.
* **Build/Test Output:** When running builds or test suites (e.g., Maven, npm, pytest), only report the summary and the specific file paths/line numbers of any failures. Do not digest successful logs.
* **Prevent Discovery Loops:** Do not run recursive listing commands (`ls -R`, `find .`) to locate files. Ask the user or refer to the architecture summary below.

### 2. Context & File Hygiene
* **Proactive Compaction:** Suggest a `/compact` command to the user immediately after a large build output is analyzed or after resolving a major bug.
* **Targeted Reading:** Never read entire large files if you only need to look at a specific function or class. Use exact line ranges where possible.
* **Focus Focus Focus:** Do not proactively review or refactor code outside the immediate scope of the current task.

---

## System Architecture Quick-Map
*Avoid running discovery commands; use this map instead.*

### Stack
- Java 21, Spring Boot 3.3, Spring Security, Thymeleaf, Maven
- No database — persistence is a CSV file at `~/.finance-app/data/users.csv`
- Runs on embedded Tomcat, port 8080

### Package Layout (`src/main/java/com/finance/app/`)
| Package | Role |
|---|---|
| `domain/model/` ||
| `domain/port/in/` ||
| `domain/port/out/` ||
| `domain/exception/` ||
| `application/service/` |implements use cases, no framework deps |
| `infrastructure/adapter/in/web/` ||
| `infrastructure/adapter/in/security/` ||
| `infrastructure/adapter/out/persistence/` | `CsvFilrite for `User` |
| `infrastructure/adapter/out/security/` |  |
| `infrastructure/config/` | |

### Key Config (`src/main/resources/`)
- `application.yml` — port 8080, CSV path, default admin password (`admin123`)
- `templates/` — `login.html`, `dashboard.html` (Thymeleaf)
- `static/css/` — `style.css`


### Run
```bash
mvn spring-boot:run        # starts on http://localhost:8080
mvn test                   # full test suite
mvn clean compile -q       # quiet build check

---

## Development Commands
*Always use these lean variants to save tokens.*

| Task | Lean Command (Preferred) | Notes |
| :--- | :--- | :--- |
| **Build Project** | `mvn clean compile -q` | `-q` suppresses normal progress logs |
| **Run Tests** | `mvn test | grep -E "FAILURE|ERROR" -A 5` | Isolates errors instantly |
| **Frontend Dev** | `npm run build -- --silent` | Minimizes bundler output noise |
| **Git Status** | `git status -s` | Short format saves context space |

---

## Response Style
* **Concise Code:** Provide only the specific lines or functions being modified. Do not rewrite entire files in your response unless explicitly asked.
* **No Fluff:** Omit pleasantries, lengthy introductions, and repetitive explanations of how the code works. Let the code and brief inline comments speak for themselves.