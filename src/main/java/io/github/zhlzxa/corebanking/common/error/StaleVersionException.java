package io.github.zhlzxa.corebanking.common.error;

/**
 * The client based its change on an outdated version of a record. Reported like a lost optimistic
 * lock, so that clients handle both cases in the same way: reload, then decide whether to reapply.
 */
public class StaleVersionException extends BusinessException {

    public StaleVersionException() {
        super(ErrorCode.CONCURRENT_MODIFICATION, "The resource was modified by another request; reload and retry");
    }
}
