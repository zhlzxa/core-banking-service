package io.github.zhlzxa.corebanking.support;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Issues signed JWTs for integration tests, standing in for the external identity provider.
 *
 * <p>Key pairs are generated in memory for every test run; no key material exists in the
 * repository. The application under test validates tokens with the trusted public key exactly as it
 * would validate tokens from a real provider, including issuer, audience and expiry checks.
 */
public final class TestJwts {

    public static final String ISSUER = TestDataFactory.ISSUER;
    public static final String AUDIENCE = "core-banking-api";

    private static final KeyPair TRUSTED_KEY = generateKeyPair();
    private static final KeyPair UNTRUSTED_KEY = generateKeyPair();
    private static final JwtEncoder TRUSTED_ENCODER = encoderFor(TRUSTED_KEY);
    private static final JwtEncoder UNTRUSTED_ENCODER = encoderFor(UNTRUSTED_KEY);
    private static final Path PUBLIC_KEY_FILE = writePublicKey(TRUSTED_KEY);

    private TestJwts() {}

    /** A valid token for {@code subject} carrying the given OAuth scopes. */
    public static String token(String subject, String... scopes) {
        return builder(subject).scopes(scopes).build();
    }

    public static Builder builder(String subject) {
        return new Builder(subject);
    }

    public static String publicKeyLocation() {
        return PUBLIC_KEY_FILE.toUri().toString();
    }

    public static final class Builder {

        private final String subject;
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private Instant issuedAt = Instant.now();
        private Duration lifetime = Duration.ofMinutes(5);
        private String scope = "";
        private boolean trusted = true;
        private final Map<String, Object> extraClaims = new LinkedHashMap<>();

        private Builder(String subject) {
            this.subject = subject;
        }

        public Builder scopes(String... scopes) {
            this.scope = String.join(" ", scopes);
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiredMinutesAgo(long minutes) {
            this.issuedAt = Instant.now().minus(Duration.ofMinutes(minutes + 5));
            this.lifetime = Duration.ofMinutes(5);
            return this;
        }

        public Builder claim(String name, Object value) {
            extraClaims.put(name, value);
            return this;
        }

        public Builder signedByUntrustedKey() {
            this.trusted = false;
            return this;
        }

        public String build() {
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .subject(subject)
                    .audience(List.of(audience))
                    .issuedAt(issuedAt)
                    .expiresAt(issuedAt.plus(lifetime))
                    .claim("scope", scope)
                    .claims(all -> all.putAll(extraClaims))
                    .build();
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
            JwtEncoder encoder = trusted ? TRUSTED_ENCODER : UNTRUSTED_ENCODER;
            return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtEncoder encoderFor(KeyPair keyPair) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    private static Path writePublicKey(KeyPair keyPair) {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes())
                        .encodeToString(keyPair.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        try {
            Path file = Files.createTempFile("test-issuer-public-key", ".pem");
            Files.writeString(file, pem);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
