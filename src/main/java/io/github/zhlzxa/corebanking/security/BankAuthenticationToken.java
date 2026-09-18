package io.github.zhlzxa.corebanking.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication for a request that presented a valid bearer token of a known, active user or
 * registered terminal.
 */
public class BankAuthenticationToken extends AbstractAuthenticationToken {

    private final AuditablePrincipal principal;
    private final Jwt jwt;

    public BankAuthenticationToken(
            AuditablePrincipal principal, Jwt jwt, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public AuditablePrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    public Jwt getToken() {
        return jwt;
    }
}
