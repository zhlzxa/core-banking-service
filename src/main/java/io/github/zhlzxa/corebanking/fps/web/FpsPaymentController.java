package io.github.zhlzxa.corebanking.fps.web;

import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.fps.FpsPayment;
import io.github.zhlzxa.corebanking.fps.FpsPaymentCommand;
import io.github.zhlzxa.corebanking.fps.FpsPaymentService;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Payments to accounts at other banks through FPS. */
@Tag(name = "FPS payments", description = "Payments to accounts at other banks through FPS")
@RestController
@RequestMapping("/fps/payments")
public class FpsPaymentController {

    private final FpsPaymentService paymentService;

    public FpsPaymentController(FpsPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public record FpsPaymentRequest(
            // Must stay in sync with transactions.request_id VARCHAR(100).
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._-]+")
            String requestId,

            @NotNull @Positive Long fromAccountId,
            @NotNull @Pattern(regexp = "[0-9]{3}") String creditorBankCode,
            // Must stay in sync with transactions.creditor_account VARCHAR(34).
            @NotNull @Pattern(regexp = "[A-Za-z0-9-]{1,34}") String creditorAccount,

            @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
            BigDecimal amount,

            @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {}

    /**
     * A payment and its current state. {@code status} is {@code PROCESSING} while the outcome at FPS
     * is not yet known; the money has left the account in that state and is not available.
     */
    public record FpsPaymentResponse(
            long transactionId,
            String requestId,
            String status,
            String externalStatus,
            String endToEndId,
            long fromAccountId,
            String creditorBankCode,
            String creditorAccount,
            String amount,
            String currency,
            Instant createdAt) {

        static FpsPaymentResponse from(FpsPayment payment) {
            return new FpsPaymentResponse(
                    payment.id(),
                    payment.requestId(),
                    payment.status().name(),
                    payment.externalStatus().name(),
                    payment.endToEndId(),
                    payment.fromAccountId(),
                    payment.creditorBankCode(),
                    payment.creditorAccount(),
                    MoneyFormatter.format(payment.amount(), payment.currency()),
                    payment.currency(),
                    payment.createdAt());
        }
    }

    /**
     * Answers 201 when the outcome is final (completed or returned) and 202 when the payment is still
     * being processed; poll the {@code Location} for the outcome.
     */
    @Operation(summary = "Pay an account at another bank")
    @ApiResponse(responseCode = "201", description = "Outcome final: completed, or returned by FPS")
    @ApiResponse(responseCode = "202", description = "Still processing; poll the Location for the outcome")
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
    @PostMapping
    public ResponseEntity<FpsPaymentResponse> pay(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody FpsPaymentRequest request) {
        AuditContext audit =
                new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), caller.channel());
        FpsPayment payment = paymentService.pay(
                audit,
                new FpsPaymentCommand(
                        caller.userId(),
                        request.requestId(),
                        request.fromAccountId(),
                        request.creditorBankCode(),
                        request.creditorAccount(),
                        request.amount(),
                        request.currency()));
        HttpStatus status = payment.status() == TransactionStatus.PROCESSING ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .location(URI.create("/fps/payments/" + payment.id()))
                .body(FpsPaymentResponse.from(payment));
    }

    @Operation(summary = "Get the current state of an FPS payment")
    @ApiErrors({ErrorCode.PAYMENT_NOT_FOUND})
    @GetMapping("/{paymentId}")
    public FpsPaymentResponse getPayment(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long paymentId) {
        return FpsPaymentResponse.from(paymentService.getPayment(caller.userId(), paymentId));
    }
}
