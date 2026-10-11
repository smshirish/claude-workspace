# E2E Agent Rules
- Use Node.js Playwright (@playwright/test) ONLY — never add com.microsoft.playwright to pom.xml
- Backend must be running on http://localhost:8080 before executing tests
- Run tests from the e2e/ directory: npx playwright test

## Screenshot Capture (mandatory for every feature with a UI surface)
After writing the Playwright tests, add a corresponding capture function to `tools/screenshots/capture.mjs`:
1. Add a `async function captureFeatureX(page)` function — one `shot()` call per meaningful UI state covered by the E2E plan's §6 Screenshot Capture section.
2. Call the new function in the main block (after the fixture import, alongside the other `captureFeature*` calls).
3. Output directory: `path.join(OUT_DIR, 'X_feature_name')` — use the same letter prefix as the feature (A, B, C, …).
- Do NOT modify `package.json`, `playwright.config.ts`, or any existing capture function.
- For back-end-only features with no UI surface, skip this step.
