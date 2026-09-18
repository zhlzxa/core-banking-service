package io.github.zhlzxa.corebanking.security;

import io.github.zhlzxa.corebanking.user.BankUser;
import io.github.zhlzxa.corebanking.user.UserRepository;
import io.github.zhlzxa.corebanking.user.UserStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

/**
 * Turns a validated JWT into a {@link BankAuthenticationToken}.
 *
 * <p>Signature, issuer, audience and expiry have already been verified by the JWT decoder when this
 * runs. The converter then maps the external identity to a user registered with the bank and
 * derives authorities from two independent sources:
 *
 * <ul>
 *   <li>{@code SCOPE_*} from the token: what the client application was allowed to request;
 *   <li>{@code ROLE_*} from the bank's database: what the person is allowed to do.
 * </ul>
 *
 * <p>Tokens for identities unknown to the bank, or for users who are locked or disabled, are
 * rejected with an authentication failure (HTTP 401).
 */
@Component
public class BankJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(BankJwtAuthenticationConverter.class);

    private final UserRepository userRepository;
    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();

    public BankJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        if (jwt.getIssuer() == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new BadCredentialsException("Token has no issuer or subject");
        }
        String issuer = jwt.getIssuer().toString();
        BankUser user = userRepository
                .findByExternalIdentity(issuer, jwt.getSubject())
                .orElseThrow(() -> {
                    log.info("Rejected token for an identity that is not registered with the bank");
                    return new BadCredentialsException("Unknown identity");
                });
        if (user.status() == UserStatus.LOCKED) {
            throw new LockedException("User is locked");
        }
        if (!user.status().canAuthenticate()) {
            throw new DisabledException("User is not active");
        }

        Collection<GrantedAuthority> authorities = new ArrayList<>(scopeConverter.convert(jwt));
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
        BankPrincipal principal = new BankPrincipal(user.id(), issuer, jwt.getSubject(), user.role());
        return new BankAuthenticationToken(principal, jwt, List.copyOf(authorities));
    }
}
