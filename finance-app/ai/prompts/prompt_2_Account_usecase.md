
# Build the backend classes 
As a experienced java architect , design /plan the implementation of followig use case.Do not implement any code.

Use case :
Build Overveiw of all bank acocunts.

Description:
The applcaition shows a list of all bank accounts of the user.

The user provides excel/csv file containing list of accounts.
Design classes for  a module that imports those accounts into the repository and saves as a seperate accounts.csv file.
Consider any available java libraries that provide an easy way to map a CSV structure into a Java bean.
Explain the class model to be used to this implementation , with the function of each class and the public interface of each class.
This shoud bcome the basis for implementing these classes using TDD technique.

## This returned 
accountUsecase_1_PLAN.md

## Second prompt -Save plan for hadnoff 
 Save the above plan to .claude/context/PLAN.md  and update/enhance as below:Save the following to                        
  .claude/context/PLAN.md:                                                                                                 
  - Feature requirements (numbered)                                                                                        
  - Acceptance criteria per feature                                                                                        
  - Component breakdown (which classes/interfaces needed)                                                                  
  - Test scenarios to cover                                                                                                
  - Out of scope items  

## Launch test agent 
claude --new-session \
  "Read .claude/context/PLAN.md fully. 
   Your role: Test Agent. Write failing tests ONLY — no implementation.
   Tech stack: JUnit 5, Mockito, Spring Boot Test.
   Test scenarios are in PLAN.md section 'Test Scenarios'.
   Output: tests under src/test/java/.
   When done, update .claude/context/HANDOFF.md with: 
   - tests written, test class names, what each covers."


