package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * An authenticated caller, person or machine, as it appears in the audit trail. The channel is
 * derived from the kind of caller, never from the request.
 */
public interface AuditablePrincipal extends AuthenticatedPrincipal {

    AuditActor toAuditActor();

    AuditChannel channel();
}
