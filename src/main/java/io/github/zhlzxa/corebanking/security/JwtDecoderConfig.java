package io.github.zhlzxa.corebanking.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

/**
 * Builds the decoder that verifies access tokens.
 *
 * <p>Whatever the key source, every token must pass the same checks: a valid RS256 signature, the
 * configured issuer, this API in the audience, and a validity period that includes now. Keeping
 * the checks in one place guarantees that local and test environments verify tokens exactly like
 * production does.
 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(OidcProperties properties) {
        OAuth2TokenValidator<Jwt> validator = tokenValidator(properties);
        if (properties.publicKeyLocation() != null) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(properties.publicKeyLocation()))
                    .build();
            decoder.setJwtValidator(validator);
            return decoder;
        }
        // Discovery is deferred to the first request so that the service can start while the
        // identity provider is temporarily unavailable.
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuer());
            decoder.setJwtValidator(validator);
            return decoder;
        });
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(OidcProperties properties) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(properties.audience()));
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
    }

    private static RSAPublicKey readPublicKey(Resource location) {
        try (InputStream in = location.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(pem);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read token verification key from " + location, e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Invalid token verification key in " + location, e);
        }
    }
}
