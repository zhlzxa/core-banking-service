package io.github.zhlzxa.corebanking.account.web;

import io.github.zhlzxa.corebanking.account.AccountQueryService;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only views of the caller's own accounts. */
@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountQueryService accountQueryService;

    public AccountController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @GetMapping
    public List<AccountResponse> listAccounts(@AuthenticationPrincipal BankPrincipal caller) {
        return accountQueryService.listAccounts(caller.userId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{accountId}")
    public AccountResponse getAccount(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long accountId) {
        return AccountResponse.from(accountQueryService.getAccount(caller.userId(), accountId));
    }

    /**
     * Returns the account's transactions, newest first. Pass the {@code nextCursor} of a response as
     * {@code cursor} to fetch the following page.
     */
    @GetMapping("/{accountId}/transactions")
    public TransactionHistoryResponse history(
            @AuthenticationPrincipal BankPrincipal caller,
            @PathVariable long accountId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(AccountQueryService.MAX_PAGE_SIZE) int size,
            @RequestParam(required = false) @Size(max = 200) String cursor) {
        return TransactionHistoryResponse.from(accountQueryService.history(caller.userId(), accountId, cursor, size));
    }
}
