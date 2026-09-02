package com.BPL_Order_Engine_Admin.manager.audit;

import com.BPL_Order_Engine_Admin.manager.auth.UserPrincipal;
import com.BPL_Order_Engine_Admin.manager.engine.EngineEntity;
import com.BPL_Order_Engine_Admin.manager.engine.EngineRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * v0.3 audit AOP (SPEC §7.2 / audit-log-coverage).
 *
 * <p>Wraps any method annotated with {@link Audited}:
 * <ol>
 *   <li>Extracts actor (username + role) from the security context.</li>
 *   <li>Resolves the target engine from the URI if
 *       {@code targetEngineFromPath = true}.</li>
 *   <li>Invokes the method.</li>
 *   <li>On success: writes an {@link AuditLog} with the
 *       {@code details} object (or whatever the SpEL expression
 *       produced).</li>
 *   <li>On failure: writes a row with
 *       {@code details: { error: <class>, message: <truncated> }}.
 *       If the exception implements {@link AuditableFailure}, the
 *       {@code details} map it returns replaces the default — this
 *       is how {@code BAD_CURRENT_PASSWORD} and similar reason-coded
 *       failures flow through the aspect instead of via inline
 *       {@code auditLogRepository.save} calls in the service.</li>
 * </ol>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int MAX_DETAILS_BYTES = 2048;

    private final AuditLogRepository auditLogRepository;
    private final EngineRepository engineRepository;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();

    public AuditAspect(
            AuditLogRepository auditLogRepository,
            EngineRepository engineRepository,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.engineRepository = engineRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        Object result;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            // Failure path: prefer the exception's own details map
            // (AuditableFailure) over the generic { error, message }
            // shape. The exception class is always included so the
            // reader can see what was thrown.
            Map<String, Object> details = new LinkedHashMap<>();
            if (t instanceof AuditableFailure af) {
                details.putAll(af.auditDetails());
            }
            details.put("error", t.getClass().getSimpleName());
            details.put("message", truncate(safeMessage(t), 256));
            write(audited, pjp, details, /* success = */ false);
            throw t;
        }
        // Success path: evaluate the SpEL details expression.
        Map<String, Object> details = evaluateDetails(pjp, audited, result);
        write(audited, pjp, details, /* success = */ true);
        return result;
    }

    private Map<String, Object> evaluateDetails(ProceedingJoinPoint pjp, Audited audited, Object result) {
        if (audited.details() == null || audited.details().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            Expression expr = parser.parseExpression(audited.details());
            EvaluationContext ctx = new StandardEvaluationContext();
            // Bind method args by name.
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] names = sig.getParameterNames();
            Object[] args = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length; i++) {
                    ctx.setVariable(names[i], args[i]);
                }
            }
            ctx.setVariable("result", result);
            Object evaluated = expr.getValue(ctx);
            if (evaluated instanceof Map<?, ?> m) {
                return new LinkedHashMap<>((Map<String, Object>) m);
            }
            return Map.of("value", evaluated);
        } catch (Exception e) {
            log.warn("Failed to evaluate SpEL details '{}'", audited.details(), e);
            return new LinkedHashMap<>();
        }
    }

    private void write(Audited audited, ProceedingJoinPoint pjp, Map<String, Object> details, boolean success) {
        try {
            String username = "<unknown>";
            String role = "USER";
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
                username = up.getUsername();
                role = up.getUser().getRoleType().name();
            }
            String targetCode = null;
            if (audited.targetEngineFromPath()) {
                targetCode = extractCodeFromUri();
            }
            String detailsJson = truncateJson(details);
            AuditLog row = new AuditLog();
            row.setActorUsername(username);
            row.setActorRole(role);
            row.setAction(audited.action());
            row.setTargetEngineCode(targetCode);
            row.setDetails(detailsJson);
            auditLogRepository.save(row);
        } catch (Exception e) {
            log.warn("Failed to write audit row for action {}", audited.action(), e);
        }
    }

    /** Extract {code} from /api/engines/{code}/... */
    private String extractCodeFromUri() {
        try {
            ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String path = attrs.getRequest().getRequestURI();
            String[] parts = path.split("/");
            // /api/engines/{code}/...
            if (parts.length >= 4 && "engines".equals(parts[2])) {
                return parts[3];
            }
        } catch (Exception ignored) {
            /* no request context */
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…[truncated]";
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private String truncateJson(Map<String, Object> details) {
        try {
            String json = objectMapper.writeValueAsString(details);
            if (json.length() <= MAX_DETAILS_BYTES) {
                return json;
            }
            log.warn("Audit details truncated ({} bytes -> {})", json.length(), MAX_DETAILS_BYTES);
            return json.substring(0, MAX_DETAILS_BYTES) + "…[truncated]";
        } catch (JacksonException e) {
            log.warn("Failed to serialize audit details", e);
            return "{}";
        }
    }
}
