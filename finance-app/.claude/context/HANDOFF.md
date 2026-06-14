# Handoff: Test Agent → Implementation Agent

## Status
All test classes written. No production code exists yet — build will fail with compile errors until implementation classes are created. That is the expected RED state.

---

## Tests Written

### 1. `BankAccountTest`
**Path:** `src/test/java/com/finance/app/domain/model/BankAccountTest.java`
**Covers:** T1.1, T1.2, T1.3
- `create(...)` sets non-null `accountId` and `importedAt`
- `create(...)` sets all supplied field values
- Full-constructor null guard for each of: `bankName`, `accountNumber`, `accountType`, `balance`, `currency`
- `equals`/`hashCode` based on `accountId` only
- Two accounts with different generated IDs are not equal

**Classes referenced (not yet created):**
- `com.finance.app.domain.model.BankAccount`
- `com.finance.app.domain.model.AccountId`
- `com.finance.app.domain.model.AccountType`

---

### 2. `ImportAccountsApplicationServiceTest`
**Path:** `src/test/java/com/finance/app/application/service/ImportAccountsApplicationServiceTest.java`
**Covers:** T2.1, T2.2, T2.3, T2.4
- Delegates parsing to `AccountFileParser` then calls `AccountRepository.saveAll()`
- Verifies `deleteAll()` is called **before** `saveAll()` using `InOrder`
- Throws `AccountImportException` when parser returns empty list
- Does **not** call `saveAll()` when list is empty

**Classes referenced (not yet created):**
- `com.finance.app.domain.exception.AccountImportException`
- `com.finance.app.domain.port.in.ImportAccountsUseCase.ImportAccountsCommand`
- `com.finance.app.domain.port.out.AccountFileParser`
- `com.finance.app.domain.port.out.AccountRepository`
- `com.finance.app.application.service.AccountApplicationService`

---

### 3. `GetAllAccountsApplicationServiceTest`
**Path:** `src/test/java/com/finance/app/application/service/GetAllAccountsApplicationServiceTest.java`
**Covers:** T3.1, T3.2
- Returns the list from `AccountRepository.findAll()`
- Returns empty list when repository is empty — no exception thrown

**Classes referenced:** same `AccountApplicationService`, `AccountRepository`

---

### 4. `OpenCsvAccountParserTest`
**Path:** `src/test/java/com/finance/app/infrastructure/adapter/out/persistence/OpenCsvAccountParserTest.java`
**Covers:** T4.1, T4.2, T4.3, T4.4, T4.5
- Parses a valid 3-row CSV via `ByteArrayInputStream` → 3 `BankAccount` instances with correct fields
- Throws `AccountImportException` (message contains the bad value) on unrecognised `accountType`
- Returns empty list for header-only CSV
- Throws `AccountImportException` when a required column (`accountNumber`) is absent from the header
- Maps `balance` string `"12345.67"` to `BigDecimal` exactly via `isEqualByComparingTo`

**Classes referenced (not yet created):**
- `com.finance.app.infrastructure.adapter.out.persistence.OpenCsvAccountParser`
- `com.finance.app.infrastructure.adapter.out.persistence.AccountCsvRecord` (internal to parser)

---

### 5. `CsvFileAccountRepositoryTest`
**Path:** `src/test/java/com/finance/app/infrastructure/adapter/out/persistence/CsvFileAccountRepositoryTest.java`
**Covers:** T5.1, T5.2, T5.3, T5.4, T5.5
- `saveAll()` writes header `accountId,bankName,accountNumber,accountType,balance,currency,importedAt` + one line per account
- Full round-trip: saved account fields survive `findAll()` including `accountId` and `importedAt`
- `findAll()` returns empty list when file does not exist (no exception)
- `deleteAll()` leaves file with header row only
- `saveAll()` creates nested parent directories if they don't exist
- Uses `@TempDir` for file isolation (matches `CsvFileUserRepositoryTest` convention)

**Classes referenced (not yet created):**
- `com.finance.app.infrastructure.adapter.out.persistence.CsvFileAccountRepository`

---

### 6. `AccountControllerTest`
**Path:** `src/test/java/com/finance/app/infrastructure/adapter/in/web/AccountControllerTest.java`
**Covers:** T6.1, T6.2, T6.3, T6.4, T6.5
- `GET /accounts` (authenticated via `@WithMockUser`) → HTTP 200, view `"accounts"`, model has `"accounts"` attribute
- `GET /accounts` with empty list → HTTP 200, no exception, model attribute is empty list
- `POST /accounts/import` with valid `MockMultipartFile` → calls use case, redirects `3xx` to `/accounts`
- `POST /accounts/import` when use case throws `AccountImportException` → HTTP 200, model has `"importError"` attribute
- `GET /accounts` without authentication → `3xx` redirect to `**/login`
- Uses `@WebMvcTest(AccountController.class)` + `@MockBean` for both use-case ports
- Uses `csrf()` post-processor on POST requests

**Classes referenced (not yet created):**
- `com.finance.app.infrastructure.adapter.in.web.AccountController`
- `com.finance.app.domain.port.in.GetAllAccountsUseCase`
- `com.finance.app.domain.port.in.ImportAccountsUseCase`

---

## What the Implementation Agent Must Create

To turn this from RED → GREEN, create these classes in dependency order:

1. `domain/model/AccountId.java` — value object, `generate()` + `of(String)`
2. `domain/model/AccountType.java` — enum: `CHECKING, SAVINGS, CREDIT, INVESTMENT, OTHER`
3. `domain/model/BankAccount.java` — entity with full constructor + `create(...)` factory
4. `domain/exception/AccountImportException.java` — unchecked exception
5. `domain/port/out/AccountRepository.java` — `saveAll`, `findAll`, `deleteAll`
6. `domain/port/out/AccountFileParser.java` — `parse(InputStream, String)`
7. `domain/port/in/ImportAccountsUseCase.java` — with `ImportAccountsCommand` record
8. `domain/port/in/GetAllAccountsUseCase.java`
9. `application/service/AccountApplicationService.java` — implements both use cases
10. `infrastructure/adapter/out/persistence/AccountCsvRecord.java` — OpenCSV `@CsvBindByName` POJO
11. `infrastructure/adapter/out/persistence/OpenCsvAccountParser.java` — implements `AccountFileParser`
12. `infrastructure/adapter/out/persistence/CsvFileAccountRepository.java` — implements `AccountRepository`
13. `infrastructure/adapter/in/web/AccountController.java` — Spring MVC `@Controller`
14. `infrastructure/config/ApplicationConfig.java` — wire `AccountApplicationService` bean
15. `src/main/resources/application.yml` — add `finance.storage.accounts-file` property
16. `src/main/resources/templates/accounts.html` — Thymeleaf template (view name `"accounts"`, model attribute `"accounts"`, model attribute `"importError"`)
17. `pom.xml` — add `com.opencsv:opencsv:5.9` dependency
