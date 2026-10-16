claude --new-session \
  "Read .claude/context/PLAN_A_ColumnSorting.md and .claude/context/HANDOFF.md.
   Your role: Dev Agent. Make the failing E2E pass. 
   The Front end changes for this use case are not implemented.
   DO NOT modify test files.
   Plan the changes that are 
   Write production code only under src/main/java/.
  
  CRITICAL: 
   Do not write application code yet. Your sole task is to analyze the requirements, inspect the existing codebase, and write a comprehensive implementation plan.
     IMPORTANT CONSTRAINTS:
   - This feature has NO backend changes. Do NOT touch src/main/java/.
   - Do NOT touch any existing test files.

   Update HANDOFF.md with implementation status."