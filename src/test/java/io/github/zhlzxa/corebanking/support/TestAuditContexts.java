package io.github.zhlzxa.corebanking.support;

import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;

/** Audit contexts for tests that call services directly rather than through HTTP. */
public final class TestAuditContexts {

    private TestAuditContexts() {}

    public static AuditContext customer(long userId) {
        return new AuditContext(
                new AuditActor(userId, TestJwts.ISSUER, "user-" + userId, "CUSTOMER"),
                "test-correlation",
                AuditChannel.API);
    }
}
