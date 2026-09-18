package io.github.zhlzxa.corebanking.transfer.web;

import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transfer.TransferService;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    /**
     * Executes an internal transfer. A retry with the same {@code requestId} and the same
     * instruction returns the original transfer with the same status code, so clients can retry
     * safely after a timeout.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody TransferRequest request) {
        AuditContext audit =
                new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), AuditChannel.API);
        BankTransaction transaction = transferService.transfer(audit, request.toCommand(caller.userId()));
        return TransferResponse.from(transaction);
    }
}
