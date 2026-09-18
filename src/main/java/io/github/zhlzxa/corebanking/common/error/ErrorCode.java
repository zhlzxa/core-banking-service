package io.github.zhlzxa.corebanking.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, client-facing error codes.
 *
 * <p>The enum constant name is part of the public API contract: clients branch on it, and the same
 * value is recorded as the reason code of rejected business events. Constants may be added but must
 * never be renamed or repurposed.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Malformed request"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "Invalid transfer"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "Insufficient balance"),
    CURRENCY_MISMATCH(HttpStatus.CONFLICT, "Currency mismatch"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

    private final HttpStatus httpStatus;
    private final String title;

    ErrorCode(HttpStatus httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }
}
