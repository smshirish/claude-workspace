## Verdict: REQUEST_CHANGES

Reviewed: `git diff main...feature/B_FilterBar` against `.claude/context/PLAN_B_FilterBar.md`

---

### Blocking Issues

- **[HIGH] `FilterAccountsUseCase` is dead code; controller bypasses it.**
  `AccountController.java:80-87` contains a private `applyFilter()` method that re-implements containment filtering inline. The controller's constructor (`AccountController.java:38-42`) only accepts `ImportAccountsUseCase` and `GetAllAccountsUseCase`; `FilterAccountsUseCase` is never injected and never called. Every HTTP request goes through `getAllAccountsUseCase.getAllAccounts()` then `applyFilter()`. The service's `filterAccounts()` implementation (`AccountApplicationService.java:54-59`) and the port (`FilterAccountsUseCase.java`) exist but are unreachable from the web layer. Plan §3.5 explicitly requires the controller to call `filterAccountsUseCase`. Feature C (Filtered Total) depends on a correct filter flow and cannot leverage the domain port in its current state. **Fix:** wire `FilterAccountsUseCase` into `AccountController`, call it in place of the private `applyFilter()`, and remove the private method.

- **[HIGH] Test-scope stubs for `AccountFilterCriteria` and `FilterAccountsUseCase` not deleted.**
  `src/test/java/com/finance/app/domain/model/AccountFilterCriteria.java` and `src/test/java/com/finance/app/domain/port/in/FilterAccountsUseCase.java` both carry explicit "delete once production class lands in `src/main/java`" comments. Both production equivalents are now present in `src/main/java`. Two class files with the same fully-qualified name on the test classpath create classpath shadowing: the JVM loads the stub (test-classes precede main-classes under Surefire) rather than the production type, so service-level tests exercise the stub interface, not the production one. **Fix:** delete both stub files.

---

### Suggestions (non-blocking)

- **[MEDIUM] Stale "RUNTIME FAILURE EXPECTED" and `// FAILS` comments** (`AccountControllerFilterTest.java:32-38` and inline on every `andExpect` call in C-1 through C-5; `AccountApplicationServiceFilterTest.java:24-30`). The feature is implemented and these tests pass. The comments are actively misleading. Remove or rewrite them to describe current intent.

- **[MINOR] C-4 does not assert sort order** (`AccountControllerFilterTest.java:104-115`). The scenario is "filtered Chase rows sorted by balance asc" but the only assertion is `hasSize(2)`. The filter-then-sort interaction is not verified. Add an ordered assertion (e.g., first element balance `200.00`, second `500.00`).

- **[NOTE — outside Dev Agent scope] E2E test file missing.** `e2e/tests/account-filter.spec.ts` does not exist. Plan §5 specifies 6 test cases (E2E-1 through E2E-6). The E2E agent stage must create this file before the feature is complete.
