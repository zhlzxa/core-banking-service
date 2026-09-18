package io.github.zhlzxa.corebanking.cash.web;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.cash.CashCommand;
import io.github.zhlzxa.corebanking.cash.TellerCashService;
import io.github.zhlzxa.corebanking.cash.WithdrawalApproval;
import io.github.zhlzxa.corebanking.cash.WithdrawalResult;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.web.ApiErrors;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cash operations performed by tellers at a branch counter. */
@Tag(name = "Teller cash", description = "Cash at a branch counter; TELLER role")
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

    public record ApprovalResponse(
            long approvalId,
            String requestId,
            String status,
            long accountId,
            String amount,
            String currency,
            long makerUserId,
            @Nullable Long checkerUserId,
            @Nullable Long transactionId,
            Instant expiresAt) {

        static ApprovalResponse from(WithdrawalApproval approval) {
            return new ApprovalResponse(
                    approval.id(),
                    approval.requestId(),
                    approval.status().name(),
                    approval.accountId(),
                    MoneyFormatter.format(approval.amount(), approval.currency()),
                    approval.currency(),
                    approval.makerUserId(),
                    approval.checkerUserId(),
                    approval.transactionId(),
                    approval.expiresAt());
        }
    }

    @Operation(summary = "Credit cash received at the counter")
    @ApiResponse(responseCode = "201", description = "Deposit completed")
    @ApiErrors({
        ErrorCode.INVALID_AMOUNT_SCALE,
        ErrorCode.CURRENCY_NOT_SUPPORTED,
        ErrorCode.ACCOUNT_NOT_FOUND,
        ErrorCode.CURRENCY_MISMATCH,
        ErrorCode.DESTINATION_ACCOUNT_CLOSED,
        ErrorCode.IDEMPOTENCY_KEY_REUSED
    })
    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public CashResponse deposit(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody CashRequest request) {
        BankTransaction transaction = tellerCashService.deposit(audit(caller), request.toCommand(caller.userId()));
        return CashResponse.from(transaction, request.accountId());
    }

    /**
     * Pays out cash, answering 201 with the transaction. If the amount needs a second teller's
     * approval, nothing is paid out yet and the answer is 202 with the approval to be decided.
     */
    @Operation(summary = "Pay out cash; large amounts need a second teller's approval")
    @ApiResponse(responseCode = "201", description = "Cash paid out")
    @ApiResponse(responseCode = "202", description = "Awaiting a second teller's approval; nothing paid out yet")
    @ApiErrors({
        ErrorCode.INVALID_AMOUNT_SCALE,
        ErrorCode.CURRENCY_NOT_SUPPORTED,
        ErrorCode.ACCOUNT_NOT_FOUND,
        ErrorCode.INSUFFICIENT_BALANCE,
        ErrorCode.CURRENCY_MISMATCH,
        ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE,
        ErrorCode.IDEMPOTENCY_KEY_REUSED
    })
    @PostMapping("/withdrawals")
    public ResponseEntity<?> withdraw(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody CashRequest request) {
        WithdrawalResult result = tellerCashService.withdraw(audit(caller), request.toCommand(caller.userId()));
        return switch (result) {
            case WithdrawalResult.Completed completed ->
                ResponseEntity.status(HttpStatus.CREATED)
                        .body(CashResponse.from(completed.transaction(), request.accountId()));
            case WithdrawalResult.AwaitingApproval pending ->
                ResponseEntity.accepted()
                        .location(URI.create(
                                "/teller/approvals/" + pending.approval().id()))
                        .body(ApprovalResponse.from(pending.approval()));
        };
    }

    @Operation(summary = "Get a withdrawal approval")
    @ApiErrors({ErrorCode.APPROVAL_NOT_FOUND})
    @GetMapping("/approvals/{approvalId}")
    public ApprovalResponse getApproval(@PathVariable long approvalId) {
        return ApprovalResponse.from(tellerCashService.getApproval(approvalId));
    }

    @Operation(summary = "Approve and pay out a pending withdrawal")
    @ApiResponse(responseCode = "201", description = "Cash paid out")
    @ApiErrors({
        ErrorCode.APPROVAL_NOT_FOUND,
        ErrorCode.FOUR_EYES_REQUIRED,
        ErrorCode.APPROVAL_NOT_PENDING,
        ErrorCode.APPROVAL_EXPIRED,
        ErrorCode.INSUFFICIENT_BALANCE,
        ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE
    })
    @PostMapping("/approvals/{approvalId}/approve")
    @ResponseStatus(HttpStatus.CREATED)
    public CashResponse approve(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long approvalId) {
        BankTransaction withdrawal = tellerCashService.approve(audit(caller), caller.userId(), approvalId);
        return CashResponse.from(withdrawal, withdrawal.fromAccountId());
    }

    @Operation(summary = "Decline a pending withdrawal")
    @ApiErrors({
        ErrorCode.APPROVAL_NOT_FOUND,
        ErrorCode.FOUR_EYES_REQUIRED,
        ErrorCode.APPROVAL_NOT_PENDING,
        ErrorCode.APPROVAL_EXPIRED
    })
    @PostMapping("/approvals/{approvalId}/reject")
    public ApprovalResponse reject(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long approvalId) {
        return ApprovalResponse.from(tellerCashService.reject(audit(caller), caller.userId(), approvalId));
    }

    private static AuditContext audit(BankPrincipal caller) {
        return new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), caller.channel());
    }
}
