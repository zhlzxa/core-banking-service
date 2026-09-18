package io.github.zhlzxa.corebanking.security;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Trust configuration for access tokens issued by the external OpenID Connect provider.
 *
 * @param issuer expected {@code iss} claim; also used for key discovery when no public key is set
 * @param audience value that must be present in the {@code aud} claim, identifying this API
 * @param publicKeyLocation optional PEM-encoded RSA public key to verify signatures with, instead
 *     of discovering the provider's keys; intended for local environments without a provider
 */
@Validated
@ConfigurationProperties("corebanking.security.oidc")
public record OidcProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @Nullable Resource publicKeyLocation) {}
