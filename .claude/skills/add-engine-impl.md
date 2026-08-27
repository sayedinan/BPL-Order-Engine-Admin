---
name: add-engine-impl
description: Generates a new engine implementation conforming to OrderEngineOperations and registers it in OrderEngineFactory.
---

# Instructions for adding an Engine Implementation
1. Create a class implementing `OrderEngineOperations` under `com.commlink.bpl.admin.engine.impl`.
2. Implement thread-safe mock state management using `AtomicReference<EngineStatus>`.
3. Annotate the class with `@Component("<engineName>OrderEngine")`.
4. Register the new type in `EngineType` enum and update `OrderEngineFactory` switch statement.
