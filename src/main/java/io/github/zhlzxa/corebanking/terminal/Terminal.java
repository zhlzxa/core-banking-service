package io.github.zhlzxa.corebanking.terminal;

/**
 * A self-service machine registered with the bank.
 *
 * @param id the bank's terminal identifier, printed on the machine
 * @param identitySubject the OAuth2 client identity the machine authenticates with
 */
public record Terminal(String id, String identityIssuer, String identitySubject, String branchCode, boolean active) {}
