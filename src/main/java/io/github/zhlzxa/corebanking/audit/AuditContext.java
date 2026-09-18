package io.github.zhlzxa.corebanking.audit;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Request-scoped facts every audit event needs, captured at the edge of the system and passed to
 * the services explicitly. Services never reconstruct the actor from request content.
 */
public record AuditContext(AuditActor actor, @Nullable String correlationId, AuditChannel channel) {

    public AuditContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(channel, "channel");
    }
}
