package io.github.zhlzxa.corebanking.audit;

/**
 * Channel through which an action reached the bank. It is determined by the server from how the
 * caller authenticated, never taken from request content such as a User-Agent header.
 */
public enum AuditChannel {
    /** Public API used by customer-facing applications. */
    API
}
