package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.ledger.LedgerEntry;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final long TX_ID = 42;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void rejectsTransferToSameAccountBeforeTouchingStorage() {
        assertThatThrownBy(() -> transferService.transfer(command(1, 1, "100.00")))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(accountRepository, transactionRepository, ledgerRepository);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> transferService.transfer(command(1, 2, "0.00")))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(accountRepository, transactionRepository, ledgerRepository);
    }

    @Test
    void completedTransferDebitsCreditsAndPostsBothLegs() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "500.00");
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        BankTransaction result = transferService.transfer(command(1, 2, "100.00"));

        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        InOrder order = inOrder(accountRepository, ledgerRepository, transactionRepository);
        order.verify(accountRepository).debit(1, AMOUNT);
        order.verify(accountRepository).credit(2, AMOUNT);
        order.verify(ledgerRepository).append(LedgerEntry.debit(TX_ID, 1, AMOUNT, "HKD"));
        order.verify(ledgerRepository).append(LedgerEntry.credit(TX_ID, 2, AMOUNT, "HKD"));
        order.verify(transactionRepository).updateStatus(TX_ID, TransactionStatus.COMPLETED);
    }

    @Test
    void locksAccountsInAscendingIdOrderRegardlessOfDirection() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "500.00");
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        transferService.transfer(command(2, 1, "100.00"));

        InOrder order = inOrder(accountRepository);
        order.verify(accountRepository).findByIdForUpdate(1);
        order.verify(accountRepository).findByIdForUpdate(2);
        order.verify(accountRepository).debit(2, AMOUNT);
        order.verify(accountRepository).credit(1, AMOUNT);
    }

    @Test
    void insufficientBalanceStopsBeforeAnyMoneyMoves() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "99.99");
        givenAccount(2, "HKD", "0.00");

        assertThatThrownBy(() -> transferService.transfer(command(1, 2, "100.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(accountRepository, never()).debit(anyLong(), any());
        verify(accountRepository, never()).credit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
        verify(transactionRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    void currencyMismatchIsRejected() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "USD", "0.00");

        assertThatThrownBy(() -> transferService.transfer(command(1, 2, "100.00")))
                .isInstanceOf(CurrencyMismatchException.class);

        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void unknownAccountIsReportedBeforeTheRequestIdIsClaimed() {
        givenAccount(1, "HKD", "1000.00");
        when(accountRepository.findByIdForUpdate(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(command(1, 2, "100.00")))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(transactionRepository, ledgerRepository);
    }

    @Test
    void retryWithSameInstructionReturnsOriginalTransactionWithoutMovingMoney() {
        givenAccount(1, "HKD", "0.00");
        givenAccount(2, "HKD", "0.00");
        when(transactionRepository.insertPendingIfAbsent(any())).thenReturn(Optional.empty());
        BankTransaction original = stored(TransactionStatus.COMPLETED);
        when(transactionRepository.findByRequestId("req-1")).thenReturn(Optional.of(original));

        BankTransaction result = transferService.transfer(command(1, 2, "100.0"));

        assertThat(result).isEqualTo(original);
        verify(accountRepository, never()).debit(anyLong(), any());
        verify(accountRepository, never()).credit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void reusingRequestIdForDifferentInstructionIsAConflict() {
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00");
        when(transactionRepository.insertPendingIfAbsent(any())).thenReturn(Optional.empty());
        when(transactionRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        assertThatThrownBy(() -> transferService.transfer(command(1, 2, "250.00")))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(accountRepository, never()).debit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
    }

    private void givenClaimedRequest() {
        when(transactionRepository.insertPendingIfAbsent(any())).thenReturn(Optional.of(TX_ID));
    }

    private void givenAccount(long id, String currency, String balance) {
        when(accountRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(new Account(id, currency, new BigDecimal(balance))));
    }

    private static TransferCommand command(long from, long to, String amount) {
        return new TransferCommand("req-1", from, to, new BigDecimal(amount), "HKD");
    }

    private static BankTransaction stored(TransactionStatus status) {
        return new BankTransaction(
                TX_ID,
                "req-1",
                TransactionType.TRANSFER,
                status,
                1,
                2,
                new BigDecimal("100.0000"),
                "HKD",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
