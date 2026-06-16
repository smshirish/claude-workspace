# Prompt 1 
please review this prompt for a test automator agent for claude code . Provide suggestions to improve it. The prompt is first step in e2 e test .SO it should only ask to prepare E2E tests and Update the plan.Md file accordingly. The prompt i have prepared is:Prepare comprehensive E2E tests for m
## Answer 1 

Your initial prompt is a solid starting point, but when dealing with CLI-based agents like Claude Code, explicit boundaries are crucial. Because Claude Code operates directly on your file system and can execute commands, a prompt that is too broad (like "design and implement the framework") risks the agent running away with the task—generating hundreds of lines of code, installing random npm/pip packages, or getting stuck in a massive context loop before you can review the strategy.

Since this is strictly the first step, the prompt should force the agent to analyze, plan, and document before writing any test scripts.

Here is a breakdown of how to improve it, followed by a revised version.

Key Areas for Improvement

Establish a Strict Stop Condition: Tell the agent exactly when its job is done for this turn (i.e., "Stop after updating plan.md"). This saves tokens and keeps you in control.

Enforce Reading Before Writing: Explicitly instruct it to read your CLAUDE.md and plan.md first to respect your existing environment constraints, rather than letting it decide how to query context.

Define the Output Structure: Instead of just "updating the plan," specify what the update should look like (e.g., proposed framework, list of test cases, required dependencies).

Optimized Prompt Options

Depending on how much control you want to retain over the framework choice, choose one of the options below.

Option 1: Structured & Highly Controlled (Recommended)

This option ensures the agent does not install anything or write test code yet, focusing purely on alignment and documentation.

Prompt

You are acting as a Test Automation Agent. This is step 1: Assessment and Planning. Do NOT write or implement any test scripts yet.

1. Read 'CLAUDE.md' to understand the project architecture, tech stack, and development constraints.

2. Read 'plan.md' to analyze the E2E use case definitions.

3. Formulate a comprehensive E2E testing strategy that aligns with our existing setup. 

Update 'plan.md' with a new section containing:

- Proposed E2E testing framework (e.g., Playwright, Cypress) and rationale based on our tech stack.

- A structural list of proposed E2E test cases/scenarios mapping back to the use cases.

- Any new dependencies or configuration changes required.

Once 'plan.md' is updated with this strategy, stop and present your summary for my review. Do not execute any installation commands or create test files in this step.



Option 2: Brief & Direct

If you prefer a shorter prompt that still maintains the boundaries you requested:

Prompt 

Act as a Test Automation Agent. This is the initial planning step. 

Based on the project constraints in 'CLAUDE.md' and the use cases in 'plan.md', design a comprehensive E2E testing strategy. 

Your only tasks for this step are to:

1. Analyze the application context and requirements.

2. Propose the automation framework and a list of E2E test cases.

3. Update 'plan.md' to reflect this E2E test plan.

Do NOT implement any test scripts or install dependencies yet. Stop after updating 'plan.md' so I can review the plan.

Why these changes help Claude Code

Context Efficiency: By telling it exactly which files to read (CLAUDE.md and plan.md), it avoids wasting tokens scanning irrelevant directories or large dependency folders.

Prevents Hallu-installing: Agents sometimes default to tools they know best rather than what fits your environment. Forcing a written proposal in plan.md lets you course-correct if it suggests a framework you don't want to use.

Clear Milestone: It gives Claude Code a clear "definition of done" for the current execution cycle.

# Final prompt prepared by me 

Act as a Test Automation Agent. This is the initial planning step. 

Based on the project constraints in 'CLAUDE.md' and the use cases in 'plan.md', design a comprehensive E2E testing strategy. 

Your only tasks for this step are to:
1. Analyze the application context and requirements.
2. Propose the automation framework and a list of E2E test cases.
3. Update 'plan.md' to reflect this E2E test plan as explained below.

Update 'plan.md' with a new section containing:
- A structural list of proposed E2E test cases/scenarios mapping back to the use cases.
- Any new dependencies or configuration changes required.

Do NOT implement any test scripts or install dependencies yet. Stop after updating 'plan.md' so I can review the plan.