# Plan: Bank Accounts Overview Module

## Feature Requirements

### F1 — Import Accounts from CSV
The user can upload a CSV file containing their bank accounts. The application parses the file and saves the accounts to a persistent `accounts.csv` store. A re-upload replaces all previously stored accounts (idempotent import).

**Acceptance Criteria:**
- AC1.1: A POST `/accounts/import` endpoint accepts a `multipart/form-data` CSV file upload.
- AC1.2: The CSV must contain a header row with columns: `bankName`, `accountNumber`, `accountType`, `balance`, `currency`.
- AC1.3: Each valid row is persisted as a `BankAccount` entity with a generated `accountId` and `importedAt` timestamp.
- AC1.4: A re-upload deletes all previously stored accounts before saving the new ones.
- AC1.5: Uploading an empty file (header only or no rows) raises an `AccountImportException` — no data is written.
- AC1.6: A CSV with an unrecognised `accountType` value raises an `AccountImportException` with a descriptive message.
- AC1.7: On success the user is redirected to `GET /accounts`.

### F2 — View All Accounts
The user can see an overview page listing all imported bank accounts.

**Acceptance Criteria:**
- AC2.1: A `GET /accounts` endpoint renders an `accounts.html` Thymeleaf template.
- AC2.2: The template displays a table with columns: Bank Name, Account Number, Account Type, Balance, Currency.
- AC2.3: When no accounts have been imported, the page shows an empty-state message (no error).
- AC2.4: The page is accessible only to authenticated users (Spring Security applies existing rules).

### F3 — Persist Accounts to accounts.csv
Imported accounts survive application restarts by being written to a dedicated CSV file on disk.

**Acceptance Criteria:**
- AC3.1: Accounts are stored at the path configured by `finance.storage.accounts-file` (default: `~/.finance-app/data/accounts.csv`).
- AC3.2: The CSV file has a fixed header: `accountId,bankName,accountNumber,accountType,balance,currency,importedAt`.
- AC3.3: The file is created (including parent directories) on first write if it does not exist.
- AC3.4: `findAll()` returns an empty list when the file does not exist — no exception.

---

## Component Breakdown

### Domain Layer (`domain/`)

| Class / Interface | Type | Responsibility |
|---|---|---|
| `AccountId` | Value Object | Type-safe UUID wrapper. `of(String)` factory + `generate()` factory. |
| `BankAccount` | Entity | Immutable domain entity. Fields: `accountId`, `bankName`, `accountNumber`, `accountType`, `balance` (BigDecimal), `currency`, `importedAt`. Static `create(...)` factory sets `accountId` and `importedAt`. |
| `AccountType` | Enum | `CHECKING`, `SAVINGS`, `CREDIT`, `INVESTMENT`, `OTHER` |
| `AccountImportException` | Exception | Unchecked domain exception for parsing and import failures. |
| `ImportAccountsUseCase` | Port (in) | `importAccounts(ImportAccountsCommand)`. Command record holds `InputStream fileContent` + `String fileName`. |
| `GetAllAccountsUseCase` | Port (in) | `getAllAccounts() : List<BankAccount>`. |
| `AccountRepository` | Port (out) | `saveAll(List<BankAccount>)`, `findAll()`, `deleteAll()`. |
| `AccountFileParser` | Port (out) | `parse(InputStream, String fileName) : List<BankAccount>`. Decouples parsing technology from the use case. |

### Application Layer (`application/service/`)

| Class | Responsibility |
|---|---|
| `AccountApplicationService` | Implements `ImportAccountsUseCase` + `GetAllAccountsUseCase`. Orchestrates `AccountFileParser` → `AccountRepository`. No Spring/framework imports. |

### Infrastructure — Driving Adapters (`infrastructure/adapter/in/web/`)

| Class | Responsibility |
|---|---|
| `AccountController` | Spring MVC controller. `GET /accounts` → renders account list. `POST /accounts/import` → receives `MultipartFile`, wraps as command, calls use case, redirects. |

### Infrastructure — Driven Adapters (`infrastructure/adapter/out/persistence/`)

| Class | Responsibility |
|---|---|
| `AccountCsvRecord` | OpenCSV annotated POJO (`@CsvBindByName`). Maps input CSV columns → Java fields. Infrastructure-only — never crosses into domain. |
| `OpenCsvAccountParser` | Implements `AccountFileParser`. Uses `CsvToBeanBuilder<AccountCsvRecord>`. Maps each record to `BankAccount`. Wraps `CsvException` in `AccountImportException`. |
| `CsvFileAccountRepository` | Implements `AccountRepository`. Reads/writes `accounts.csv` via `java.nio.file`. Mirrors existing `CsvFileUserRepository` pattern. |

### Configuration

| Item | Detail |
|---|---|
| `application.yml` addition | `finance.storage.accounts-file: ${user.home}/.finance-app/data/accounts.csv` |
| `ApplicationConfig` | Expose `AccountApplicationService` as a Spring `@Bean`, injecting `OpenCsvAccountParser` + `CsvFileAccountRepository`. |
| Maven dependency | `com.opencsv:opencsv:5.9` |

---

## Test Scenarios

### Unit Tests (JUnit 5 + Mockito, no Spring context)

**`BankAccountTest`**
- T1.1: `create(...)` generates a non-null `accountId` and sets `importedAt` to a non-null value.
- T1.2: Constructor rejects `null` for each required field with `NullPointerException`.
- T1.3: Two `BankAccount` instances with the same `accountId` are equal.

**`ImportAccountsApplicationServiceTest`**
- T2.1: Delegates to `AccountFileParser.parse()` and then calls `AccountRepository.saveAll()` with the result.
- T2.2: Calls `AccountRepository.deleteAll()` before `saveAll()`.
- T2.3: Throws `AccountImportException` when parser returns an empty list.
- T2.4: Does not call `saveAll()` when the list is empty.

**`GetAllAccountsApplicationServiceTest`**
- T3.1: Returns the list from `AccountRepository.findAll()`.
- T3.2: Returns empty list when repository returns empty — no exception.

**`OpenCsvAccountParserTest`**
- T4.1: Parses a valid 3-row CSV string and returns 3 `BankAccount` instances with correct field values.
- T4.2: Throws `AccountImportException` when a row contains an unrecognised `accountType` value.
- T4.3: Returns empty list for a CSV with header only and no data rows.
- T4.4: Throws `AccountImportException` when a required column is missing from the header.
- T4.5: Correctly maps `balance` string to `BigDecimal`.

**`CsvFileAccountRepositoryTest`**
- T5.1: `saveAll()` writes the correct header line and one CSV line per account to a temp file.
- T5.2: `findAll()` round-trips: saved accounts are returned with all fields intact.
- T5.3: `findAll()` returns empty list when the file does not exist.
- T5.4: `deleteAll()` overwrites the file leaving only the header row.
- T5.5: `saveAll()` creates parent directories if they do not exist.

### Integration / Web Tests (Spring MockMvc)

**`AccountControllerTest`**
- T6.1: `GET /accounts` returns HTTP 200 and model attribute `accounts` is present.
- T6.2: `GET /accounts` with no stored accounts renders page without error.
- T6.3: `POST /accounts/import` with a valid `MockMultipartFile` calls `ImportAccountsUseCase` and returns a 3xx redirect to `/accounts`.
- T6.4: `POST /accounts/import` with an empty file returns an error response or re-renders with an error message.
- T6.5: Unauthenticated `GET /accounts` is redirected to `/login` by Spring Security.

---

## Out of Scope

| Item | Reason |
|---|---|
| Excel (.xlsx) file support | Only CSV required; parser port design allows adding Apache POI adapter later without changing the use case. |
| Per-user account isolation | Current architecture has no multi-user session concept beyond authentication. All accounts belong to the single logged-in user. |
| Account editing / deletion via UI | Import replaces all data; no individual CRUD operations in this iteration. |
| Pagination of the accounts list | Out of scope for initial overview; can be added to `GetAllAccountsUseCase` as a query parameter later. |
| Balance aggregation / totals | Display only; no calculations or summaries in this iteration. |
| Currency conversion | All balances displayed as-is in their original currency. |
| Input CSV validation beyond column mapping | No business-rule validation (e.g. balance must be positive). Only structural/parse errors are caught. |
| Database persistence | The project uses file-based CSV storage by design; no JPA/DB in scope. |
| Async / batch import for large files | Synchronous import only; no job queue or progress tracking. |
