package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.web.ProblemDetails;
import io.github.zhlzxa.corebanking.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers unauthenticated requests with 401 and a problem body. The standard bearer-token entry
 * point still sets the {@code WWW-Authenticate} header required by RFC 6750. The reason for the
 * failure (expired, wrong audience, unknown user, ...) is deliberately not disclosed. Every
 * rejection is recorded in the audit trail.
 */
@Component
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter writer;
    private final SecurityAuditRecorder securityAuditRecorder;

    public ProblemAuthenticationEntryPoint(ProblemResponseWriter writer, SecurityAuditRecorder securityAuditRecorder) {
        this.writer = writer;
        this.securityAuditRecorder = securityAuditRecorder;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        securityAuditRecorder.authenticationFailed(request);
        bearerEntryPoint.commence(request, response, ex);
        writer.write(request, response, ProblemDetails.of(ErrorCode.UNAUTHENTICATED, "Authentication is required"));
    }
}
