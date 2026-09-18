package io.github.zhlzxa.corebanking.cash.web;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.cash.CashCommand;
import io.github.zhlzxa.corebanking.cash.TellerCashService;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cash operations performed by tellers at a branch counter. */
@RestController
@RequestMapping("/teller")
public class CashController {

    private final TellerCashService tellerCashService;

    public CashController(TellerCashService tellerCashService) {
        this.tellerCashService = tellerCashService;
    }

    public record CashRequest(
            // Must stay in sync with transactions.request_id VARCHAR(100).
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String requestId,

            @NotNull @Positive Long accountId,

            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
            BigDecimal amount,

            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {

        CashCommand toCommand(long operatorId) {
            return new CashCommand(operatorId, requestId, accountId, amount, currency);
        }
    }

    public record CashResponse(
            long transactionId,
            String requestId,
            String type,
            String status,
            long accountId,
            String amount,
            String currency,
            Instant createdAt) {

        static CashResponse from(BankTransaction transaction, long accountId) {
            return new CashResponse(
                    transaction.id(),
                    transaction.requestId(),
                    transaction.type().name(),
                    transaction.status().name(),
                    accountId,
                    MoneyFormatter.format(transaction.amount(), transaction.currency()),
                    transaction.currency(),
                    transaction.createdAt());
        }
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public CashResponse deposit(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody CashRequest request) {
        BankTransaction transaction = tellerCashService.deposit(audit(caller), request.toCommand(caller.userId()));
        return CashResponse.from(transaction, request.accountId());
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public CashResponse withdraw(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody CashRequest request) {
        BankTransaction transaction = tellerCashService.withdraw(audit(caller), request.toCommand(caller.userId()));
        return CashResponse.from(transaction, request.accountId());
    }

    private static AuditContext audit(BankPrincipal caller) {
        return new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), caller.channel());
    }
}
