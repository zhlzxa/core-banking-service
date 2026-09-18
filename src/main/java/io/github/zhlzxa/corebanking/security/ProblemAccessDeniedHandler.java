package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.web.ProblemDetails;
import io.github.zhlzxa.corebanking.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers requests rejected by authorization with 403 and a problem body, and records the denial in
 * the audit trail. Handles both URL rules and method-level {@code @PreAuthorize} checks, which
 * propagate here from the controller layer.
 */
@Component
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler bearerHandler = new BearerTokenAccessDeniedHandler();
    private final ProblemResponseWriter writer;
    private final SecurityAuditRecorder securityAuditRecorder;

    public ProblemAccessDeniedHandler(ProblemResponseWriter writer, SecurityAuditRecorder securityAuditRecorder) {
        this.writer = writer;
        this.securityAuditRecorder = securityAuditRecorder;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        securityAuditRecorder.accessDenied(request);
        bearerHandler.handle(request, response, ex);
        writer.write(
                request,
                response,
                ProblemDetails.of(ErrorCode.ACCESS_DENIED, "The caller is not permitted to perform this operation"));
    }
}
