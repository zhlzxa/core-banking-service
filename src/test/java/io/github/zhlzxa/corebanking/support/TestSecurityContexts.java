package io.github.zhlzxa.corebanking.support;

import io.github.zhlzxa.corebanking.security.BankAuthenticationToken;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.user.UserRole;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Security contexts for tests that call services directly rather than through HTTP. The resulting
 * authentication is shaped exactly like the one produced for a real bearer token.
 */
public final class TestSecurityContexts {

    private TestSecurityContexts() {}

    public static SecurityContext customer(long userId, String... scopes) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(TestJwts.ISSUER)
                .subject("user-" + userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        String[] authorities = new String[scopes.length + 1];
        for (int i = 0; i < scopes.length; i++) {
            authorities[i] = "SCOPE_" + scopes[i];
        }
        authorities[scopes.length] = "ROLE_CUSTOMER";
        BankPrincipal principal = new BankPrincipal(userId, TestJwts.ISSUER, "user-" + userId, UserRole.CUSTOMER);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new BankAuthenticationToken(
                principal, jwt, List.copyOf(AuthorityUtils.createAuthorityList(authorities))));
        return context;
    }
}
