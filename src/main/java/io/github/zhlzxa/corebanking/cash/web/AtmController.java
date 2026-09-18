package io.github.zhlzxa.corebanking.cash.web;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.cash.AtmCashService;
import io.github.zhlzxa.corebanking.cash.CashCommand;
import io.github.zhlzxa.corebanking.cash.web.CashController.CashRequest;
import io.github.zhlzxa.corebanking.cash.web.CashController.CashResponse;
import io.github.zhlzxa.corebanking.security.TerminalPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Operations available to self-service terminals. Only terminals can call these endpoints. */
@RestController
@RequestMapping("/atm")
public class AtmController {

    private final AtmCashService atmCashService;

    public AtmController(AtmCashService atmCashService) {
        this.atmCashService = atmCashService;
    }

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
