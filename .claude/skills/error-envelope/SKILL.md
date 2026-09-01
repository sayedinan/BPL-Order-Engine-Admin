---
name: error-envelope
description: v0.3 — the standard error response shape, ApiExceptionHandler as the single place that maps exceptions to HTTP codes, and the exception → code mapping for v0.3.
---

# Error envelope (v0.3)

Every 4xx and 5xx response in v0.3 has the same shape. Every
exception that bubbles out of a controller is caught by a single
`@ControllerAdvice` and translated to that shape. Controllers never
return `ResponseEntity.status(403).body(...)` with a custom body.

## The envelope

```json
{
    "timestamp": "2026-09-01T09:02:11Z",
    "status": 409,
    "error": "Conflict",
    "message": "Engine 'bpl' is already RUNNING",
    "path": "/api/engines/bpl/start"
}
```

- `timestamp`: ISO-8601 UTC. Set by the handler, not by the exception.
- `status`: HTTP status code as an integer.
- `error`: HTTP reason phrase (`Bad Request`, `Unauthorized`, `Forbidden`, `Not Found`, `Conflict`, `Internal Server Error`, etc.). The default `HttpStatus` enum supplies these.
- `message`: human-readable. The exception's message, sometimes cleaned up. Never includes stack traces, never includes secrets.
- `path`: the request URI. Captured from `HttpServletRequest.getRequestURI()`.
- `details`: **optional.** Present only when the exception carries structured detail (e.g. a `MethodArgumentNotValidException` with field errors, or an `EngineScriptException` with `exitCode` and `stderr`). When present, it is a JSON object, not a string.

## ApiExceptionHandler

One class, one method per exception type. Each method returns a `ResponseEntity<ErrorEnvelope>`.

```java
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        ErrorEnvelope body = new ErrorEnvelope(
            Instant.now(), 400, "Bad Request",
            "Request validation failed", req.getRequestURI(), fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(EngineAuthException.class)
    public ResponseEntity<ErrorEnvelope> handleEngineAuth(EngineAuthException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, "SSH authentication failed for engine '" + ex.getEngineCode() + "'", req, null);
    }

    @ExceptionHandler(EngineUnreachableException.class)
    public ResponseEntity<ErrorEnvelope> handleEngineUnreachable(EngineUnreachableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_GATEWAY, "Engine '" + ex.getEngineCode() + "' is unreachable", req, null);
    }

    @ExceptionHandler(EngineScriptException.class)
    public ResponseEntity<ErrorEnvelope> handleEngineScript(EngineScriptException ex, HttpServletRequest req) {
        Map<String, Object> details = Map.of(
            "engineCode", ex.getEngineCode(),
            "exitCode", ex.getExitCode(),
            "stderr", truncate(ex.getStderr(), 2048)
        );
        return error(HttpStatus.BAD_GATEWAY, "Script exited with code " + ex.getExitCode(), req, details);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorEnvelope> handleTimeout(TimeoutException ex, HttpServletRequest req) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Operation timed out", req, null);
    }

    @ExceptionHandler(EngineNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleNotSupported(EngineNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND, "Engine '" + ex.getEngineCode() + "' is not supported", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        // Don't leak which resource the user lacks access to. Generic message.
        return error(HttpStatus.FORBIDDEN, "Access denied", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleAny(Exception ex, HttpServletRequest req) {
        // Last-resort catch. Log the full exception (with a UUID for cross-reference).
        // Return a generic message; the UUID is in the response so support can find the log.
        String incidentId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}]", incidentId, ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error (incident: " + incidentId + ")", req, null);
    }

    private ResponseEntity<ErrorEnvelope> error(HttpStatus status, String message, HttpServletRequest req, Object details) {
        ErrorEnvelope body = new ErrorEnvelope(
            Instant.now(), status.value(), status.getReasonPhrase(), message, req.getRequestURI(), details
        );
        return ResponseEntity.status(status).body(body);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...[truncated]";
    }
}
```

## Exception → HTTP code mapping

| Exception | HTTP | Notes |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `details` is `Map<fieldName, message>` |
| `ConstraintViolationException` | 400 | Same shape; for path-variable / query-param validation |
| `HttpMessageNotReadableException` | 400 | Malformed JSON; generic message |
| `EngineNotSupportedException` | 404 | "Engine 'X' is not supported" |
| `AccessDeniedException` | 403 | "Access denied" — **don't leak which resource** |
| `EngineAuthException` | 403 | SSH auth failed; no password in the message |
| `EngineUnreachableException` | 502 | Network/SSH connect failed |
| `EngineScriptException` | 502 | Script exit non-zero; `details` has exitCode + truncated stderr |
| `TimeoutException` | 504 | `Future.get(timeout, unit)` hit the bound |
| `OptimisticLockingFailureException` | 409 | Concurrent edit; suggest retry |
| `DataIntegrityViolationException` | 409 | Unique constraint, FK constraint, etc. |
| `Exception` (catch-all) | 500 | With an incident UUID for cross-reference |

## What the handler does NOT do

- It does not return stack traces in the response. Stack traces are server-side only.
- It does not include the exception class name in the response (except indirectly via the `error` reason phrase). Generic 500 → "Internal server error."
- It does not leak the resource path of a forbidden resource. A USER requesting `/api/audit-logs` gets `"Access denied"`, not `"You don't have access to /api/audit-logs"`. The two responses are identical.
- It does not log `password`, `token`, `Authorization` header, or any sensitive field. The `EngineAuthException` handler logs the engine code and the exception class — never the credentials.

## What controllers do

- Throw the right exception. `throw new EngineNotSupportedException(code)` from `OrderEngineFactory` on a code miss. `throw new AccessDeniedException("Engine not assigned")` from a service that filters by `assignedEngines`.
- Don't catch exceptions and rewrap them with custom bodies. The handler is the single source of error shape.
- Don't return `ResponseEntity.status(...).body(Map.of(...))` with a custom shape. The handler will translate the exception if you let it bubble.

## Anti-patterns

- **Don't write a custom error response object in a controller.** Use exceptions.
- **Don't catch generic `Exception` in a controller.** Let it bubble; the handler will catch it.
- **Don't log the full exception in the handler without an incident UUID.** The 500 response has the UUID; the log has the UUID; the two can be cross-referenced.
- **Don't return the same shape for 401 and 403 with different messages.** `401 Unauthorized` is "no/invalid credentials"; `403 Forbidden` is "credentials valid but you can't do this." A USER visiting `/api/audit-logs` is 403, not 401.
- **Don't include SQL error codes in the 500 message.** The handler logs them; the response doesn't see them.
- **Don't include the engine server's stderr verbatim.** Truncate to 2KB. The full stderr is in the SSH session log; the API response carries the truncated version.
