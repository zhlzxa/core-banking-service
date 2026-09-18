package io.github.zhlzxa.corebanking.fps;

import java.math.BigDecimal;

/**
 * Instruction by a customer to pay an account at another bank through FPS.
 *
 * @param customerId authenticated customer; must own the source account
 * @param creditorBankCode clearing code of the receiving bank
 */
public record FpsPaymentCommand(
        long customerId,
        String requestId,
        long fromAccountId,
        String creditorBankCode,
        String creditorAccount,
        BigDecimal amount,
        String currency) {}
