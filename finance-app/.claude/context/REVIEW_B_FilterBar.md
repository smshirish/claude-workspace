## Verdict: REQUEST_CHANGES

Reviewed: `git diff main...feature/B_FilterBar` against `.claude/context/PLAN_B_FilterBar.md`

---

### Issue 1 — `startsWith` used instead of `contains` in `AccountApplicationService` — FR-3 violated [CRITICAL]

**File:** `src/main/java/com/finance/app/application/service/AccountApplicationService.java`, `matches()` private method (last method in the class)

FR-3 requires a `contains` (substring) match. The service's `matches` helper reads:

```java
value.toLowerCase().startsWith(filter.toLowerCase())
```

The controller's private `applyFilter()` correctly uses `.contains()` (lines 83–85), but the service-level implementation is wrong. Because all test fixture inputs happen to be prefixes of the test data (e.g., `"chase"`, `"CHA"`, `"SAV"`, `"000111"`), the 7 unit tests in `AccountApplicationServiceFilterTest` pass coincidentally. A mid-string filter such as `"hase"` would silently return zero results.

**Fix:** Replace `.startsWith(...)` with `.contains(...)` in `AccountApplicationService.matches()`.

---

### Issue 2 — `FilterAccountsUseCase` is dead code; controller bypasses it [CRITICAL]

**Files:** `AccountController.java:67–87`, `AccountApplicationService.java` (`filterAccounts` method)

Plan §3.5 states the controller should "call `filterAccountsUseCase` when any criterion is non-blank; else call `getAllAccountsUseCase`". The actual controller never imports or calls `FilterAccountsUseCase`. Instead it always calls `getAllAccountsUseCase.getAllAccounts(criteria)` and then filters via the private `applyFilter()` method.

Consequences:
- `FilterAccountsUseCase.filterAccounts()` is never exercised by any HTTP request.
- All 7 unit tests in `AccountApplicationServiceFilterTest` test a code path that is never invoked by the controller.
- `AccountControllerFilterTest` never injects or mocks `FilterAccountsUseCase`; it validates only the private-method path.

**Fix (choose one):**
- (a) Wire `FilterAccountsUseCase` into the controller and call it when any filter criterion is non-blank. Remove the private `applyFilter()` method. Matches the plan.
- (b) Remove the `FilterAccountsUseCase` port, its implementation in `AccountApplicationService`, and the service-level tests; keep only the controller-level `applyFilter()`. Update plan §3.3/§3.5 accordingly.

---

### Issue 3 — Test-scope stubs not deleted after production classes landed [HIGH]

**Files:**
- `src/test/java/com/finance/app/domain/model/AccountFilterCriteria.java`
- `src/test/java/com/finance/app/domain/port/in/FilterAccountsUseCase.java`

Both stub files carry the comment "delete once production [class/interface] lands in src/main/java". Production equivalents now exist in `src/main/java`. Two classes with the same fully-qualified name on the test classpath create ambiguity and can cause unexpected behaviour depending on classpath ordering.

**Fix:** Delete both stub files.

---

### Issue 4 — Stale "RUNTIME FAILURE EXPECTED" and `// FAILS` comments [MEDIUM]

**File:** `AccountControllerFilterTest.java`, class-level Javadoc (lines 31–38) and inline comments on every `andExpect` in C-1 through C-5

The feature is now implemented; these tests pass. The comments are actively misleading to future readers.

**Fix:** Remove or update the stale failure comments.

---

### Issue 5 — C-4 test does not assert sort order [MINOR]

**File:** `AccountControllerFilterTest.java:104–115`

C-4's scenario is "filtered Chase rows sorted by balance asc" but the assertion only checks `hasSize(2)`. Order is not verified, so the sort-after-filter behaviour is untested.

**Fix:** Assert element order, e.g., first account balance `200.00`, second `500.00`.

---

### Issue 6 — E2E test file missing [NOTE — outside Dev Agent scope]

`e2e/tests/account-filter.spec.ts` does not exist. Plan §5 calls for this file with 6 test cases (E2E-1 through E2E-6). The E2E agent stage must create it before the feature is complete.
