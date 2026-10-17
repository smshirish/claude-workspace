# Test Agent Rules (unit-test stage)
- Write ONLY new `*Test.java` files under src/test/java, or modified existing ones there.
- You MAY create NEW interface-only stubs under src/main/java when a test needs a type to compile. A stub is `public interface X { ... }` with method signatures and no bodies, no fields, no implementing class.
- NEVER create classes, enums, records, or implementations under src/main/java. Those belong to the Dev stage.
- NEVER modify an existing file under src/main/java.
- NEVER create or edit anything under src/test/resources, src/main/resources, or e2e/.
- NEVER run the application, only mvn test
- Do not generate tests for value objects or data records
- The orchestrator fails the stage before commit if any of the above is violated.
