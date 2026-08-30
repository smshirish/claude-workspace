# Finance App

A single-owner personal finance web application for importing, tracking, and analysing bank accounts and investment portfolios. Built as a local-first, zero-infrastructure tool — no external database, no cloud dependency.

## Vision

Help one person gain a clear, consolidated view of their finances:

1. **Account tracking** — import bank account exports (CSV), validate them, browse and filter the data.
2. **Investment analytics** — calculate PnL, analyse asset allocation, benchmark portfolio performance.
3. **Visualisations** — charts and summaries that make patterns visible at a glance.

The app is intentionally simple in infrastructure so it can run anywhere with no setup beyond Java.

## Design Principles

- **Single owner** — no multi-tenancy, no user management UI.
- **Local-first** — all data lives in CSV files on the user's machine (`~/.finance-app/data/`).
- **Privacy by default** — account numbers and personal identifiers are masked in any analysis output.
- **Domain-driven** — business logic lives in a framework-free domain layer; infrastructure is pluggable.

---

## Documentation Index

| Document | Purpose | Changes when |
|---|---|---|
| **[docs/architecture.md](docs/architecture.md)** | Technical design: layers, patterns, package structure, key decisions | Architecture changes |
| **[docs/projectplan.md](docs/projectplan.md)** | Feature backlog: what is built, what is next, known technical debt | Features ship or priorities change |
| **[docs/ai-workflow.md](docs/ai-workflow.md)** | How AI-assisted development works: agents, context files, conventions | Workflow evolves |
| **[ai/prompts/](ai/prompts/)** | Reusable prompt scripts for agent sessions (copy-paste reference) | New prompts added per feature |
| **[ai/completed-plans/](ai/completed-plans/)** | Archived PLAN files for shipped features | Each time a feature ships |
| **[.claude/context/](.claude/context/)** | Live agent context: active PLAN, HANDOFF status, plan template | Every agent session |
