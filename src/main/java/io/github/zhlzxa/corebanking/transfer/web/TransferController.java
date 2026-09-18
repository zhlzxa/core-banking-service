package io.github.zhlzxa.corebanking.transfer.web;

import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transfer.TransferQueryService;
import io.github.zhlzxa.corebanking.transfer.TransferService;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferQueryService transferQueryService;

    public TransferController(TransferService transferService, TransferQueryService transferQueryService) {
        this.transferService = transferService;
        this.transferQueryService = transferQueryService;
    }

    /**
     * Executes an internal transfer. A retry with the same {@code requestId} and the same
     * instruction returns the original transfer with the same status code, so clients can retry
     * safely after a timeout.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody TransferRequest request) {
        AuditContext audit =
                new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), AuditChannel.API);
        BankTransaction transaction = transferService.transfer(audit, request.toCommand(caller.userId()));
        return ResponseEntity.created(URI.create("/transfers/" + transaction.id()))
                .body(TransferResponse.from(transaction));
    }

    /** Returns a transfer that debited or credited one of the caller's accounts. */
    @GetMapping("/{transactionId}")
    public TransferResponse getTransfer(
            @AuthenticationPrincipal BankPrincipal caller, @PathVariable long transactionId) {
        return TransferResponse.from(transferQueryService.getTransfer(caller.userId(), transactionId));
    }
}
