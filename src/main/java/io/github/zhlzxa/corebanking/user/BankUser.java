package io.github.zhlzxa.corebanking.user;

/**
 * A person known to the bank.
 *
 * @param id internal identifier used for every relationship inside the bank
 * @param identityIssuer issuer of the external identity (OIDC {@code iss})
 * @param identitySubject stable subject of the external identity (OIDC {@code sub})
 */
public record BankUser(
        long id, String identityIssuer, String identitySubject, String displayName, UserRole role, UserStatus status) {}
