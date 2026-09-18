package io.github.zhlzxa.corebanking.fps;

import java.math.BigDecimal;

/**
 * A credit transfer instruction as sent to FPS.
 *
 * @param endToEndId the payment's stable identity at FPS; a resend carries the same value so that
 *     FPS treats it as a duplicate rather than a second payment
 */
public record FpsInstruction(
        String endToEndId,
        long debtorAccountId,
        String creditorBankCode,
        String creditorAccount,
        BigDecimal amount,
        String currency) {}
