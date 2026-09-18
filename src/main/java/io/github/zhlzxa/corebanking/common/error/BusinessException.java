package io.github.zhlzxa.corebanking.common.error;

/**
 * Base type for expected business rule violations.
 *
 * <p>Business exceptions are unchecked so that they roll back the surrounding transaction under
 * Spring's default rules. The message is returned to API clients and therefore must be a fixed,
 * safe sentence: it must never contain internal identifiers, SQL, or echoed client input.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
