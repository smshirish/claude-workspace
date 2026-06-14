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

## Response Style
* **Concise Diffs:** Output only the specific lines/functions modified. Avoid rewriting unchanged blocks.
* **No Fluff:** Omit pleasantries, intros, and conversational explanations. Rely on code and short inline comments.