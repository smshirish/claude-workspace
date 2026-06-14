claude --new-session \
  "Read .claude/context/PLAN.md and .claude/context/HANDOFF.md.
   Your role: Dev Agent. Make the failing tests pass.
   DO NOT modify test files.
   Write production code only under src/main/java/.
   Run 'mvn test' after each implementation. Fix compile errors before moving on.
   Update HANDOFF.md with implementation status."