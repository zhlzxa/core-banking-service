package io.github.zhlzxa.corebanking.payee.web;

import io.github.zhlzxa.corebanking.payee.NewPayee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request bodies of the payee endpoints. Limits match the columns of the {@code payees} table. */
final class PayeeRequests {

    private PayeeRequests() {}

    record AddPayeeRequest(
            @NotBlank @Size(max = 50) String nickname,
            @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,34}") String accountNumber,
            @Pattern(regexp = "[A-Z0-9]{3,20}") String bankCode,
            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {

        NewPayee toNewPayee() {
            return new NewPayee(nickname.strip(), accountNumber, bankCode, currency);
        }
    }

    /**
     * @param version the version returned when the payee was last read; the rename is rejected if
     *     the payee has changed since
     */
    record RenamePayeeRequest(
            @NotBlank @Size(max = 50) String nickname,
            @NotNull @PositiveOrZero Long version) {}
}
