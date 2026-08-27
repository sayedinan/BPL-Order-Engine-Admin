# Specification: BPL Order Engine Admin UI & Management Service

## 1. Executive Summary
The BPL Order Engine Admin UI is an internal administrative service and web dashboard that enables engineers and operators to monitor, start, stop, and view logs for order processing engines.

## 2. Architecture & Design Patterns
- OrderEngineOperations interface (getStatus, startEngine, stopEngine, getLogs)
- BplOrderEngineOperations (In-memory mock state machine)
- PclOrderEngineOperations (Placeholder)
- OrderEngineFactory (Factory Method pattern)

## 3. Security & Access Control
- ROLE_ADMIN: admin / admin123 (Full control)
- ROLE_VIEWER: viewer / viewer123 (Read-only status & logs)

## 4. Safety Guardrail
- No direct connections or actions targeting staging container 180.210.129.233 or bpl-order-engine.
