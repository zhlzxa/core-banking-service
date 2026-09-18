package io.github.zhlzxa.corebanking.payee.web;

import io.github.zhlzxa.corebanking.payee.Payee;
import org.jspecify.annotations.Nullable;

/**
 * A saved payee. The account number is masked in every response: the client that saved it already
 * knows it, and a response that ends up in a log or a screenshot should not expose it.
 */
public record PayeeResponse(
        long payeeId,
        String nickname,
        String maskedAccountNumber,
        @Nullable String bankCode,
        String currency,
        long version) {

    private static final int VISIBLE_DIGITS = 4;

    static PayeeResponse from(Payee payee) {
        return new PayeeResponse(
                payee.getId(),
                payee.getNickname(),
                mask(payee.getAccountNumber()),
                payee.getBankCode(),
                payee.getCurrency(),
                payee.getVersion());
    }

    static String mask(String accountNumber) {
        int visible = Math.min(VISIBLE_DIGITS, Math.max(0, accountNumber.length() - VISIBLE_DIGITS));
        return "*".repeat(accountNumber.length() - visible) + accountNumber.substring(accountNumber.length() - visible);
    }
}
