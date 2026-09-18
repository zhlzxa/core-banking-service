package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.BestEffortAuditRecorder;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Records requests that were stopped by authentication or authorization.
 *
 * <p>Repeated authentication failures and access denials are an early signal of credential stuffing
 * or privilege probing, so they belong in the audit trail alongside business events. Recording is
 * best effort: a failure to write the event is logged and never changes the 401 or 403 response.
 */
@Component
public class SecurityAuditRecorder {

    private final AuditEventFactory auditEventFactory;
    private final BestEffortAuditRecorder bestEffortAuditRecorder;

    public SecurityAuditRecorder(AuditEventFactory auditEventFactory, BestEffortAuditRecorder bestEffortAuditRecorder) {
        this.auditEventFactory = auditEventFactory;
        this.bestEffortAuditRecorder = bestEffortAuditRecorder;
    }

    /** The request carried no usable credentials. The caller is unknown. */
    public void authenticationFailed(HttpServletRequest request) {
        record(AuditActor.anonymous(), AuditAction.AUTHENTICATION_FAILED, request, ErrorCode.UNAUTHENTICATED);
    }

    /** An authenticated caller attempted an operation outside their permissions. */
    public void accessDenied(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuditActor actor =
                authentication != null && authentication.getPrincipal() instanceof AuditablePrincipal principal
                        ? principal.toAuditActor()
                        : AuditActor.anonymous();
        record(actor, AuditAction.AUTHORIZATION_DENIED, request, ErrorCode.ACCESS_DENIED);
    }

    private void record(AuditActor actor, AuditAction action, HttpServletRequest request, ErrorCode reason) {
        AuditContext context = new AuditContext(actor, CorrelationId.current().orElse(null), AuditChannel.API);
        bestEffortAuditRecorder.record(
                auditEventFactory.securityRejected(context, action, request.getRequestURI(), reason.name()));
    }
}
