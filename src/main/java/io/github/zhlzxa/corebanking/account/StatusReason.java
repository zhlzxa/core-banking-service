package io.github.zhlzxa.corebanking.account;

/** Why an account was frozen. Stored with the account and recorded in the audit trail. */
public enum StatusReason {
    COURT_ORDER,
    FRAUD_SUSPECTED,
    CUSTOMER_REQUEST,
    KYC_EXPIRED
}
