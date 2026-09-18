package io.github.zhlzxa.corebanking.security;

/**
 * Authorization expressions used with {@code @PreAuthorize}. Each combines the OAuth scope the
 * client application must hold with the bank role the person must have; resource ownership is
 * checked separately by the service against the data being accessed.
 */
public final class Permissions {

    /** Move money out of one's own accounts. */
    public static final String CUSTOMER_TRANSFER = "hasAuthority('SCOPE_bank.transfer') and hasRole('CUSTOMER')";

    /** View one's own accounts, balances, history and transfers. */
    public static final String CUSTOMER_READ_ACCOUNTS =
            "hasAuthority('SCOPE_bank.accounts.read') and hasRole('CUSTOMER')";

    /** View one's own saved payees. */
    public static final String CUSTOMER_READ_PAYEES = "hasAuthority('SCOPE_bank.payees.read') and hasRole('CUSTOMER')";

    /** Add, rename and remove one's own saved payees. */
    public static final String CUSTOMER_MANAGE_PAYEES =
            "hasAuthority('SCOPE_bank.payees.write') and hasRole('CUSTOMER')";

    /** Take in and pay out cash at a branch counter on behalf of customers. */
    public static final String TELLER_CASH = "hasAuthority('SCOPE_bank.cash') and hasRole('TELLER')";

    /** Pay out cash at a self-service terminal. */
    public static final String ATM_WITHDRAW = "hasAuthority('SCOPE_bank.atm.withdraw') and hasRole('ATM')";

    /**
     * Freeze, unfreeze, close accounts and change their limits. Administrators manage accounts but
     * cannot move customer money: there is deliberately no transfer permission for this role.
     */
    public static final String ADMIN_MANAGE_ACCOUNTS = "hasAuthority('SCOPE_bank.accounts.admin') and hasRole('ADMIN')";

    private Permissions() {}
}
