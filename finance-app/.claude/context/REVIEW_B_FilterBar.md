## Verdict: REQUEST_CHANGES

Reviewed: `git diff main...feature/B_FilterBar` against `.claude/context/PLAN_B_FilterBar.md`

---

### Issue 1 — `startsWith` used instead of `contains` — FR-3 violated [CRITICAL]

**Files:** `AccountController.java:83-85`, `AccountApplicationService.java:64`

FR-3 explicitly requires a **`contains`** match ("partial, case-insensitive match"). Both filtering implementations use `startsWith`:

```java
// AccountController.java:83
a.bankName().toLowerCase().startsWith(bankName.toLowerCase())
// AccountApplicationService.java:64
value.toLowerCase().startsWith(filter.toLowerCase())
```

AC-1 criterion: "accounts where `bankName` **contains** 'chase' (case-insensitive)". A filter term that is a mid-string fragment (e.g., "hase", "ank Name") will not match even though it should. The unit and MockMvc tests do not catch this because all test inputs happen to be prefixes of the fixture data.

**Fix:** Replace `.startsWith(...)` with `.contains(...)` in both locations.

---

### Issue 2 — `FilterAccountsUseCase` is dead code; controller bypasses it [CRITICAL]

**Files:** `AccountController.java:10-11,67-68`, `AccountApplicationService.java:54-65`

Plan §3.5 states the controller should "Build `AccountFilterCriteria`; call `filterAccountsUseCase` when any criterion is non-blank; else call `getAllAccountsUseCase`".

The actual controller never imports or calls `FilterAccountsUseCase`. It always calls `getAllAccountsUseCase.getAllAccounts(criteria)` then applies an inline private `applyFilter()` method. The fully-implemented `AccountApplicationService.filterAccounts()` is never invoked. This means:

- The `FilterAccountsUseCase` port, its implementation in `AccountApplicationService`, and any bean wiring for it are dead code from the controller's perspective.
- The 7 unit tests in `AccountApplicationServiceFilterTest` test an execution path that is never triggered by any HTTP request.

**Fix (choose one):**
- (a) Wire the controller to inject and call `FilterAccountsUseCase` as planned; remove the private `applyFilter()` method.
- (b) Remove the `FilterAccountsUseCase` port, its implementation, and the service-level `filterAccounts()` method; keep only the controller-level `applyFilter()` logic. Delete/update tests accordingly.

Option (a) matches the plan. Either must be chosen — the current state has both paths and neither is authoritative.

---

### Issue 3 — Test-scope stubs not deleted after production classes landed [HIGH]

**Files:**
- `src/test/java/com/finance/app/domain/model/AccountFilterCriteria.java`
- `src/test/java/com/finance/app/domain/port/in/FilterAccountsUseCase.java`

Both stub files carry the comment "delete once production [class/interface] lands in src/main/java" — but they were not deleted. Production equivalents now exist in `src/main/java`. Having two classes with the same fully-qualified name on the test classpath creates ambiguity and can cause unexpected behaviour depending on classpath ordering.

**Fix:** Delete both stub files.

---

### Issue 4 — Stale "RUNTIME FAILURE EXPECTED" and `// FAILS` comments [MEDIUM]

**File:** `AccountControllerFilterTest.java:31-38,71-72,84-85,97-98,113-114,127-129`

The class-level Javadoc and inline test comments state that the tests will fail until the controller is implemented. The controller is now implemented and these tests pass. The comments are actively misleading to future readers.

**Fix:** Remove or update the stale failure comments.

---

### Issue 5 — C-4 test does not assert sort order [MINOR]

**File:** `AccountControllerFilterTest.java:104-115`

C-4 scenario description is "filtered Chase rows sorted by balance asc" but the assertion only checks `hasSize(2)`. It does not verify the order of accounts, so the sort-after-filter path is not validated by this test.

**Fix:** Add assertion on `accounts` list order (e.g., first element has balance `200.00`, second has `500.00`).

---

### Issue 6 — E2E test file missing [NOTE — outside Dev Agent scope]

`e2e/tests/account-filter.spec.ts` does not exist. This is expected if the E2E agent stage has not run yet; noting here for the orchestrator's awareness. The E2E stage must create this file before the feature can be considered complete.
