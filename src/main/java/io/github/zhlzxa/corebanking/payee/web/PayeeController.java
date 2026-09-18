package io.github.zhlzxa.corebanking.payee.web;

import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.payee.Payee;
import io.github.zhlzxa.corebanking.payee.PayeeService;
import io.github.zhlzxa.corebanking.payee.web.PayeeRequests.AddPayeeRequest;
import io.github.zhlzxa.corebanking.payee.web.PayeeRequests.RenamePayeeRequest;
import io.github.zhlzxa.corebanking.security.BankPrincipal;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's saved payees. */
@RestController
@RequestMapping("/payees")
public class PayeeController {

    private final PayeeService payeeService;

    public PayeeController(PayeeService payeeService) {
        this.payeeService = payeeService;
    }

    @GetMapping
    public List<PayeeResponse> list(@AuthenticationPrincipal BankPrincipal caller) {
        return payeeService.list(caller.userId()).stream()
                .map(PayeeResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PayeeResponse> add(
            @AuthenticationPrincipal BankPrincipal caller, @Valid @RequestBody AddPayeeRequest request) {
        Payee payee = payeeService.add(audit(caller), caller.userId(), request.toNewPayee());
        return ResponseEntity.created(URI.create("/payees/" + payee.getId())).body(PayeeResponse.from(payee));
    }

    @PatchMapping("/{payeeId}")
    public PayeeResponse rename(
            @AuthenticationPrincipal BankPrincipal caller,
            @PathVariable long payeeId,
            @Valid @RequestBody RenamePayeeRequest request) {
        Payee payee = payeeService.rename(
                audit(caller), caller.userId(), payeeId, request.nickname().strip(), request.version());
        return PayeeResponse.from(payee);
    }

    @DeleteMapping("/{payeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal BankPrincipal caller, @PathVariable long payeeId) {
        payeeService.remove(audit(caller), caller.userId(), payeeId);
    }

    private static AuditContext audit(BankPrincipal caller) {
        return new AuditContext(caller.toAuditActor(), CorrelationId.current().orElse(null), AuditChannel.API);
    }
}
