package io.github.zhlzxa.corebanking.cash.web;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.cash.AtmCashService;
import io.github.zhlzxa.corebanking.cash.CashCommand;
import io.github.zhlzxa.corebanking.cash.web.CashController.CashRequest;
import io.github.zhlzxa.corebanking.cash.web.CashController.CashResponse;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.security.TerminalPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.web.ApiErrors;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operations available to self-service terminals. Only terminals can call these endpoints. */
@Tag(name = "ATM", description = "Self-service terminals; registered terminals only")
@RestController
@RequestMapping("/atm")
public class AtmController {

    private final AtmCashService atmCashService;

    public AtmController(AtmCashService atmCashService) {
        this.atmCashService = atmCashService;
    }

    @Operation(summary = "Dispense cash")
    @ApiResponse(responseCode = "201", description = "Cash dispensed")
    @ApiErrors({
        ErrorCode.INVALID_AMOUNT_SCALE,
        ErrorCode.CURRENCY_NOT_SUPPORTED,
        ErrorCode.ACCOUNT_NOT_FOUND,
        ErrorCode.INSUFFICIENT_BALANCE,
        ErrorCode.CURRENCY_MISMATCH,
        ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE,
        ErrorCode.TRANSFER_LIMIT_EXCEEDED,
        ErrorCode.IDEMPOTENCY_KEY_REUSED
    })
    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public CashResponse withdraw(
            @AuthenticationPrincipal TerminalPrincipal terminal, @Valid @RequestBody CashRequest request) {
        AuditContext audit = new AuditContext(
                terminal.toAuditActor(), CorrelationId.current().orElse(null), terminal.channel());
        CashCommand command =
                new CashCommand(null, request.requestId(), request.accountId(), request.amount(), request.currency());
        BankTransaction withdrawal = atmCashService.withdraw(audit, command);
        return CashResponse.from(withdrawal, request.accountId());
    }
}
