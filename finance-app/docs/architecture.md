# Architecture

## Pattern

Hexagonal Architecture (Ports & Adapters). The domain is the centre; all framework and I/O concerns are adapters that plug in via interfaces.

```
┌──────────────────────────────────────────────────────────────┐
│  Infrastructure (Adapters)                                   │
│  Inbound:  Spring MVC controllers, Spring Security           │
│  Outbound: CsvFileUserRepository, CsvFileAccountRepository   │
│            OpenCsvAccountParser, BcryptPasswordHasherAdapter  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Application Layer                                     │  │
│  │  AuthenticationApplicationService                      │  │
│  │  AccountApplicationService                             │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │  Domain (zero framework dependencies)                  │  │
│  │  Models:    BankAccount, User, value objects, enums    │  │
│  │  Ports In:  GetAllAccountsUseCase, ImportAccountsUseCase│  │
│  │             AuthenticateUserUseCase                    │  │
│  │  Ports Out: AccountRepository, UserRepository          │  │
│  │             AccountFileParser, PasswordHasher          │  │
│  │  Services:  AccountCsvSchemaValidator                  │  │
│  │             AccountCsvRowValidator                     │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## Package Structure

```
src/main/java/com/finance/app/
  domain/
    model/            — Entities, value objects, enums
    exception/        — Domain exceptions
    port/in/          — Use case interfaces (inbound ports)
    port/out/         — Repository / parser interfaces (outbound ports)
    service/          — Domain services (e.g., CSV validators)
  application/service/ — Use case implementations
  infrastructure/
    adapter/in/web/       — Spring MVC controllers
    adapter/in/security/  — Spring Security adapters
    adapter/out/persistence/ — CSV file adapters
    adapter/out/security/    — BCrypt adapter
    config/               — Spring @Configuration classes
```

## Key Design Decisions

| Concern | Choice | Reason |
|---|---|---|
| Architecture | Hexagonal (Ports & Adapters) | Keeps domain testable and framework-independent |
| Web | Spring MVC + Thymeleaf (server-side rendering) | Simple, no build pipeline for JS |
| Authentication | Spring Security form login + BCrypt | Standard; no custom auth logic needed |
| Persistence | Flat CSV files at `~/.finance-app/data/` | Zero infrastructure; single-user local tool |
| Authorization | Single role: `OWNER` | Single-owner app; no role hierarchy needed |
| Domain purity | No Spring annotations in `domain/` package | Domain can be tested without container |

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3, Spring Security |
| Build | Maven |
| Server | Embedded Tomcat, port from `app.env` (`APP_PORT`, default 8080) |
| Templates | Thymeleaf |
| CSV parsing | OpenCSV |
| E2E testing | Node.js Playwright (`e2e/` directory) |

## Authentication Flow

1. Browser POSTs credentials → Spring Security form-login intercepts.
2. `FinanceUserDetailsService` loads the user via `UserRepository`.
3. Spring Security's `DaoAuthenticationProvider` verifies the BCrypt password.
4. Success → redirect to `/dashboard`; failure → `/login?error`.

## Persistence

- **Users:** `~/.finance-app/data/users.csv` — bootstrapped on startup with default admin if empty.
- **Accounts:** `~/.finance-app/data/accounts.csv` — replaced on each import.
- **Default admin password:** `finance.security.default-admin-password` in `application.yml` resolves `${ADMIN_PASSWORD}`; see [Configuration & Secrets](#configuration--secrets).

## Configuration & Secrets

| File | Checked in? | Holds | Read by |
|---|---|---|---|
| `app.env` | Yes | Non-secret settings: `APP_PORT`, `MAPPING_FILE` (anonymisation mapping path, relative to `finance-app/`; default `local/anonym_to_real_id.json`) | `application.yml` (via `spring.config.import`), `bin/env.sh`, `bin/stop.sh`, `e2e/playwright.config.ts`, `tools/screenshots/capture.mjs` |
| `finance-app-secrets.env` | **No** — lives in the same directory as `MAPPING_FILE` (`finance-app/local/`, gitignored) | Secrets: `ADMIN_PASSWORD` | `bin/env.sh` only |

How the secret is sourced:

1. `bin/start.sh` sources `bin/env.sh`, which loads `app.env`, derives the secrets file path as `dirname(MAPPING_FILE)/finance-app-secrets.env`, and exports everything in it as environment variables (with `set -a`).
2. Spring resolves `${ADMIN_PASSWORD}` from the environment. There is no default: if the variable is missing, startup fails with an unresolved-placeholder error, and `env.sh` prints a warning naming the missing file.
3. Create the file once (the app only uses it when it first creates the admin user, so changing it later does not alter an existing `users.csv`):
   ```bash
   printf 'ADMIN_PASSWORD=<choose-a-password>\n' > local/finance-app-secrets.env
   chmod 600 local/finance-app-secrets.env
   ```
4. Anything that needs the secret outside `start.sh` must source it first: `source bin/env.sh` (E2E specs read `ADMIN_PASSWORD` in `e2e/tests/helpers/auth.ts`, and `tools/screenshots/capture.mjs` reads it too; both fail with a clear message if it is unset).
5. Unit and integration tests do not use the secrets file: `src/test/resources/application.properties` supplies a test-only `ADMIN_PASSWORD`.

`finance-app/local/` is persistent and gitignored: it holds the secrets file, `anonym_to_real_id.json` and the csv-converter's default output (`account_conversion.bash` writes there unless given another output dir). `bin/env.sh` creates the directory if it is missing, but the secrets file must be created once. The original password (`admin123`) remains in git history.
