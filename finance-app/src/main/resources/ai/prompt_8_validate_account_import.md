
# Build the account validation before import  use case 
As a experienced java architect , design /plan the implementation of followig use case.Do not implement any code.

Use case :
Validate account file before importing.

Description: 
Modify the account import module as below. 
On invocation of import, before invoking the import, perform following validations
- The schema of provided CSV validated against expected schema ( Column names,Order of columns )
- Validate data contents per row- datatype , null cheeck for mandatory columns

The order of validations should be as below:

- validate schema first  - If the schema is wrong , stop all validations
- Validate all rows for data content validations. MArk errors per Row , with an error message (Row number , Error that hels to fix error)

Failure in validation should return Error message the user can see.

Save the above plan to .claude/context/PLAN.md  and update/enhance as below:
 Save the following to                        
  .claude/context/PLAN .md:                                                                                                 
  - Feature requirements (numbered)                                                                                        
  - Acceptance criteria per feature                                                                                        
  - Component breakdown (which classes/interfaces needed or need to be updated)                                                                  
  - Test scenarios to cover                                                                                                
  - Out of scope items  




## Second prompt -Save plan for hadnoff 

Provided the abpve üprompt to Gemini with followign question :
Please help me to prepare a prompt for building a java applciation with Claude code. The applciation has basic account import ready and I want to enhance it. FOllowing is my prompt.As a experienced java architect , design /plan the implementation of followig use case.Do not implement any code.

And recevived folowing prompt :

## Final prompt (as provided by Gemini)

You are an expert Java Architect. Your task is to design and plan the implementation of a new CSV validation feature for our existing account import module. 

CRITICAL: Do not write application code yet. Your sole task is to analyze the requirements, inspect the existing codebase, and write a comprehensive implementation plan.

### Context & Goal
We have a basic account import feature ready. Before processing the import, we need to intercept the file and run a two-tier validation process. If validation fails, we must return structured error messages to the user.

### Detailed Requirements & Use Case
1. **Tier 1: Schema Validation (Fail-Fast)**
   - Validate the incoming CSV schema against the expected template.
   - Check for: Exact column names, exact column order, and missing/extra columns.
   - **Behavior:** If the schema validation fails, stop immediately. Do not process rows. Return a global schema error.

2. **Tier 2: Row-Level Data Validation (Accumulative)**
   - If the schema passes, validate every row in the dataset.
   - Check for: Data type compliance (e.g., Date formats, Numeric boundaries) and Null/Empty checks for mandatory columns.
   - **Behavior:** Do not fail fast on row errors. Process all rows, collect all validation errors, and map them explicitly by Row Number with clear, actionable error messages.

3. **Error Reporting:**
   - Define a structured error response object or exception strategy that can easily be mapped to the UI/API response for the user.

---

### Output Directive
Analyze the current codebase for the account import module, then generate and save the architectural plan to `.claude/context/PLAN.md`. Use the following exact Markdown structure:

# Implementation Plan: Account CSV Validation

## 1. Feature Requirements
## 2. Acceptance Criteria
## 3. Component Breakdown
## 4. Test Scenarios
## 5. Out of Scope
---

Please review the existing workspace, think step-by-step as an architect, and generate the `.claude/context/PLAN.md` file now. Let me know when it is ready for my review.
 
## Launch test agent 
claude --new-session \
  "Read .claude/context/PLAN.md fully. 
   Your role: Test Agent. Write failing tests ONLY — no implementation.
   Tech stack: JUnit 5, Mockito, Spring Boot Test.
   Test scenarios are in PLAN.md section 'Test Scenarios'.
   Output: tests under src/test/java/.
   When done, update .claude/context/HANDOFF.md with: 
   - tests written, test class names, what each covers."


