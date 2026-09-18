package io.github.zhlzxa.corebanking.transfer.web;

import io.github.zhlzxa.corebanking.transfer.TransferCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request body of {@code POST /transfers}.
 *
 * <p>The amount should be sent as a JSON string (for example {@code "100.00"}) so that no client
 * ever represents it as a binary floating-point number.
 */
public record TransferRequest(
        // Must stay in sync with transactions.request_id VARCHAR(100).
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+")
        String requestId,

        @NotNull @Positive Long fromAccountId,
        @NotNull @Positive Long toAccountId,
        // Must stay in sync with NUMERIC(19, 4).
        @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
        BigDecimal amount,

        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {

    TransferCommand toCommand(long customerId) {
        return new TransferCommand(customerId, requestId, fromAccountId, toAccountId, amount, currency);
    }
}
