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
| Server | Embedded Tomcat, port 8080 |
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
- **Default admin password:** configurable in `application.yml` (`finance.security.default-admin-password`).
