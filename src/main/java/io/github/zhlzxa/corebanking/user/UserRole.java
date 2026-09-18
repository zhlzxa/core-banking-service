package io.github.zhlzxa.corebanking.user;

/**
 * Role of a user within the bank. Roles are assigned by the bank and stored in its own database;
 * they are never taken from the identity provider's token.
 */
public enum UserRole {
    /** Retail customer acting on their own accounts. */
    CUSTOMER,
    /** Branch staff acting on behalf of customers. */
    TELLER,
    /** Back-office administrator. Does not imply permission to move customer money. */
    ADMIN
}
