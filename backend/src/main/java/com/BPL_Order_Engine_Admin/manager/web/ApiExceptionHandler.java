package com.BPL_Order_Engine_Admin.manager.web;

import com.BPL_Order_Engine_Admin.manager.engine.EngineAuthException;
import com.BPL_Order_Engine_Admin.manager.engine.EngineNotSupportedException;
import com.BPL_Order_Engine_Admin.manager.engine.EngineScriptException;
import com.BPL_Order_Engine_Admin.manager.engine.EngineUnreachableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * v0.3 {@code @ControllerAdvice} — every 4xx/5xx response in the app
 * comes from here (SPEC §0.1 / API.md §0.1 / §5).
 *
 * <p>The handler never returns a custom shape — the standard
 * envelope is the contract. Controllers throw the right exception;
 * the handler maps it. The single catch-all 500 includes an incident
 * UUID in the response so support can cross-reference the server log.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final int STDERR_MAX = 2048;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- 400: validation ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            fieldErrors.put(fe.getField(),
                fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Request validation failed",
            req, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(
            ConstraintViolationException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON body", req, null);
    }

    // ---- 401 / 403: security ----

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest req) {
        // Generic message — do not leak the resource path.
        return error(HttpStatus.FORBIDDEN, "Access denied", req, null);
    }

    // ---- 404 / 409: domain ----

    @ExceptionHandler(EngineNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleNotSupported(
            EngineNotSupportedException ex, HttpServletRequest req) {
        return error(HttpStatus.NOT_FOUND,
            "Engine '" + ex.getEngineCode() + "' is not supported", req, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            OptimisticLockingFailureException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "Concurrent modification, please retry", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        return error(HttpStatus.CONFLICT, "Data integrity violation", req, null);
    }

    // ---- 502 / 504: SSH + script ----

    @ExceptionHandler(EngineAuthException.class)
    public ResponseEntity<Map<String, Object>> handleEngineAuth(
            EngineAuthException ex, HttpServletRequest req) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    @ExceptionHandler(EngineUnreachableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreachable(
            EngineUnreachableException ex, HttpServletRequest req) {
        return error(HttpStatus.BAD_GATEWAY,
            "Engine '" + ex.getEngineCode() + "' is unreachable", req, null);
    }

    @ExceptionHandler(EngineScriptException.class)
    public ResponseEntity<Map<String, Object>> handleScript(
            EngineScriptException ex, HttpServletRequest req) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("engineCode", ex.getEngineCode());
        details.put("exitCode", ex.getExitCode());
        details.put("stderr", truncate(ex.getStderr()));
        return error(HttpStatus.BAD_GATEWAY,
            "Script exited with code " + ex.getExitCode(), req, details);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(
            TimeoutException ex, HttpServletRequest req) {
        return error(HttpStatus.GATEWAY_TIMEOUT, "Operation timed out", req, null);
    }

    // ---- 409: domain conflicts (re-stated) ----

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return error(status, ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(),
            req, null);
    }

    // ---- 500: catch-all ----

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, Object>> handleAny(Throwable ex, HttpServletRequest req) {
        String incidentId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}]", incidentId, ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error (incident: " + incidentId + ")", req, null);
    }

    // ---- helpers ----

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message, HttpServletRequest req, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", req.getRequestURI());
        if (details != null) {
            body.put("details", details);
        }
        return ResponseEntity.status(status).body(body);
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() <= STDERR_MAX ? s : s.substring(0, STDERR_MAX) + "...[truncated]";
    }
}
