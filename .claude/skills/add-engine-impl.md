---
name: add-engine-impl
description: Generates a new OrderEngineOperations implementation; Spring's Map<String, OrderEngineOperations> autowiring picks it up by @Service bean name, so no factory/registry edit is required.
---

# Add a new Order Engine

1. Create a class in package `com.BPL_Order_Engine_Admin.manager.engine.<id>`,
   where `<id>` is the engine id (e.g. `pcl`). The class must implement
   `com.BPL_Order_Engine_Admin.manager.engine.OrderEngineOperations`
   (see `BPL-Order-Engine-Admin-backend/src/main/java/com/BPL_Order_Engine_Admin/manager/engine/OrderEngineOperations.java`).
2. Annotate it `@Service("<id>")` — the value MUST match the engine id
   (e.g. `@Service("pcl")`). The bean name is what `OrderEngineFactory`
   keys on, so a mismatch means 404 at lookup time. Reference:
   `BplOrderEngineOperations.java` is annotated `@Service("bpl")`.
3. Implement `engineId()` (return the id), `displayName()` (return the
   human-readable name, e.g. `"PCL Order Engine"`), `status()`, `start()`,
   `stop()`, and `getLogs(int)`.
4. Keep the implementation thread-safe — the controller, the status poller,
   and (for the mock) the scheduled log generator all call into one
   instance concurrently. The existing `BplOrderEngineOperations` uses a
   `ReentrantLock` + `AtomicReference<EngineStatus>` for state transitions
   and a bounded `ArrayDeque<LogLine>` (cap 500) for the log buffer; mirror
   that pattern.
5. **No edits** to `OrderEngineFactory`, `OrderEngineController`, `SecurityConfig`,
   or any other file. `OrderEngineFactory` is a plain `@Component` whose
   constructor takes `Map<String, OrderEngineOperations>` — Spring autowires
   every `@Service("<id>")` bean by name, so the new implementation is
   registered automatically. There is no enum, no switch, and no registry
   to update.
6. Before the class exists, requests for the new id throw
   `EngineNotSupportedException` from the factory → HTTP 404. That is the
   correct not-yet-implemented behavior; do not add a stub class that
   throws "not implemented" — the 404 already says that.
7. Add the engine to the role matrix in SPEC.md §3.4 if its endpoints
   diverge from the existing pattern, and update SPEC.md §2.3 to add the
   new subpackage to the package-structure tree.
