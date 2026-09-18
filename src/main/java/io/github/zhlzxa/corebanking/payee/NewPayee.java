package io.github.zhlzxa.corebanking.payee;

import org.jspecify.annotations.Nullable;

/**
 * Details of a destination account to save.
 *
 * @param bankCode clearing code of the destination bank, or {@code null} for accounts at this bank
 */
public record NewPayee(
        String nickname, String accountNumber, @Nullable String bankCode, String currency) {}
