package io.github.zhlzxa.corebanking.transfer.web;

import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transfer.TransferQueryService;
import io.github.zhlzxa.corebanking.transfer.TransferService;
import io.github.zhlzxa.corebanking.web.ApiErrors;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Transfers", description = "Transfers between accounts held at the bank")
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
    @Operation(summary = "Transfer between accounts; idempotent per requestId")
    @ApiResponse(responseCode = "201", description = "Transfer completed, or the original result of a retried request")
    @ApiErrors({
        ErrorCode.INVALID_TRANSFER,
        ErrorCode.INVALID_AMOUNT_SCALE,
        ErrorCode.ACCOUNT_NOT_FOUND,
        ErrorCode.INSUFFICIENT_BALANCE,
        ErrorCode.CURRENCY_MISMATCH,
        ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE,
        ErrorCode.DESTINATION_ACCOUNT_CLOSED,
        ErrorCode.TRANSFER_LIMIT_EXCEEDED,
        ErrorCode.IDEMPOTENCY_KEY_REUSED
    })
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
    @Operation(summary = "Get a transfer involving one of the caller's accounts")
    @ApiErrors({ErrorCode.TRANSFER_NOT_FOUND})
    @GetMapping("/{transactionId}")
    public TransferResponse getTransfer(
            @AuthenticationPrincipal BankPrincipal caller, @PathVariable long transactionId) {
        return TransferResponse.from(transferQueryService.getTransfer(caller.userId(), transactionId));
    }
}
