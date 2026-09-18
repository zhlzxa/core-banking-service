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
    FOUR_EYES_REQUIRED(HttpStatus.FORBIDDEN, "Four-eyes approval required"),
    INVALID_TRANSFER(HttpStatus.BAD_REQUEST, "Invalid transfer"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid cursor"),
    INVALID_AMOUNT_SCALE(HttpStatus.BAD_REQUEST, "Invalid amount scale"),
    CURRENCY_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "Currency not supported"),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
    TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Transfer not found"),
    PAYEE_NOT_FOUND(HttpStatus.NOT_FOUND, "Payee not found"),
    APPROVAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Approval not found"),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "Insufficient balance"),
    CURRENCY_MISMATCH(HttpStatus.CONFLICT, "Currency mismatch"),
    SOURCE_ACCOUNT_NOT_ACTIVE(HttpStatus.CONFLICT, "Source account not active"),
    DESTINATION_ACCOUNT_CLOSED(HttpStatus.CONFLICT, "Destination account closed"),
    TRANSFER_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "Transfer limit exceeded"),
    INVALID_ACCOUNT_STATE(HttpStatus.CONFLICT, "Invalid account state"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency key reused"),
    PAYEE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Payee already exists"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Concurrent modification"),
    APPROVAL_NOT_PENDING(HttpStatus.CONFLICT, "Approval not pending"),
    APPROVAL_EXPIRED(HttpStatus.CONFLICT, "Approval expired"),
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
