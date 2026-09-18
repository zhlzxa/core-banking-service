package io.github.zhlzxa.corebanking.account.web;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountAdministrationService;
import io.github.zhlzxa.corebanking.account.StatusReason;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.common.money.MoneyFormatter;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Back-office account operations. Requires the ADMIN role and the bank.accounts.admin scope. */
@RestController
@RequestMapping("/admin/accounts/{accountId}")
public class AccountAdministrationController {

    private final AccountAdministrationService administrationService;

    public AccountAdministrationController(AccountAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    public record FreezeRequest(@NotNull StatusReason reason) {}

    public record LimitsRequest(
            @Nullable @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
            BigDecimal perTransactionLimit,

            @Nullable @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
            BigDecimal dailyLimit) {}

    /** Full view of an account for back-office staff, including controls. */
    public record AdminAccountResponse(
            long accountId,
            @Nullable Long ownerUserId,
            String currency,
            String balance,
            String status,
            @Nullable String statusReason,
            @Nullable String perTransactionLimit,
            @Nullable String dailyLimit) {

        static AdminAccountResponse from(Account account) {
            return new AdminAccountResponse(
                    account.id(),
                    account.ownerUserId(),
                    account.currency(),
                    MoneyFormatter.format(account.balance(), account.currency()),
                    account.status().name(),
                    account.statusReason() == null
                            ? null
                            : account.statusReason().name(),
                    format(account.perTransactionLimit(), account.currency()),
                    format(account.dailyTransferLimit(), account.currency()));
        }

        private static @Nullable String format(@Nullable BigDecimal amount, String currency) {
            return amount == null ? null : MoneyFormatter.format(amount, currency);
        }
    }

    @PostMapping("/freeze")
    public AdminAccountResponse freeze(
            @AuthenticationPrincipal BankPrincipal caller,
            @PathVariable long accountId,
            @Valid @RequestBody FreezeRequest request) {
        return AdminAccountResponse.from(administrationService.freeze(audit(caller), accountId, request.reason()));
    }

    @PostMapping("/unfreeze")
    public AdminAccountResponse unfreeze(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long accountId) {
        return AdminAccountResponse.from(administrationService.unfreeze(audit(caller), accountId));
    }

    @PostMapping("/close")
    public AdminAccountResponse close(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long accountId) {
        return AdminAccountResponse.from(administrationService.close(audit(caller), accountId));
    }

    @PutMapping("/limits")
    public AdminAccountResponse changeLimits(
            @AuthenticationPrincipal BankPrincipal caller,
            @PathVariable long accountId,
            @Valid @RequestBody LimitsRequest request) {
        return AdminAccountResponse.from(administrationService.changeLimits(
                audit(caller), accountId, request.perTransactionLimit(), request.dailyLimit()));
    }

    private static AuditContext audit(BankPrincipal caller) {
        return new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), AuditChannel.API);
    }
}
