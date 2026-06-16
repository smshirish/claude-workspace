
## Use cases 
### USe cases Implemented 

#### Simple login  1.0

#### Simple account listing 2.0

#### Simple Account import 2.1

### Use cases to Implement 

#### Use case for now 

##### Validate account import file 
Modify the account import module as below. 
On invocation of import, before invoking the import, perform following validations
- The schema of provided CSV validated against expected schema ( Column names,Order of columns )
- Validate data contents per row- datatype , null cheeck for mandatory columns

The order of validations should be as below:

- validate schema first  - If the schema is wrong , stop all validations
- Validate all rows for data content validations. MArk errors per Row , with an error message (Row number , Error that hels to fix error)

Failure in validation should return Error message the user can see

## Technical debt

### Dev container file improvements
- Move all installation related comamnds to dev container , instaed of running them manualyl in container (currently they are added as coments in devcontainer.json file)

--how to instally playwright and chromium 