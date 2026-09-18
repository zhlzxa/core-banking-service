package io.github.zhlzxa.corebanking.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.zhlzxa.corebanking.account.Account;
import io.github.zhlzxa.corebanking.account.AccountNotFoundException;
import io.github.zhlzxa.corebanking.account.AccountRepository;
import io.github.zhlzxa.corebanking.account.AccountStatus;
import io.github.zhlzxa.corebanking.account.DailyTransferUsageRepository;
import io.github.zhlzxa.corebanking.audit.AuditAction;
import io.github.zhlzxa.corebanking.audit.AuditActor;
import io.github.zhlzxa.corebanking.audit.AuditChannel;
import io.github.zhlzxa.corebanking.audit.AuditContext;
import io.github.zhlzxa.corebanking.audit.AuditEvent;
import io.github.zhlzxa.corebanking.audit.AuditEventFactory;
import io.github.zhlzxa.corebanking.audit.AuditEventRepository;
import io.github.zhlzxa.corebanking.audit.AuditOutcome;
import io.github.zhlzxa.corebanking.audit.BestEffortAuditRecorder;
import io.github.zhlzxa.corebanking.audit.IndependentAuditRecorder;
import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.common.time.BusinessCalendar;
import io.github.zhlzxa.corebanking.ledger.LedgerEntry;
import io.github.zhlzxa.corebanking.ledger.LedgerRepository;
import io.github.zhlzxa.corebanking.posting.AccountLocks;
import io.github.zhlzxa.corebanking.posting.AccountRuleViolationException;
import io.github.zhlzxa.corebanking.posting.CurrencyMismatchException;
import io.github.zhlzxa.corebanking.posting.IdempotencyConflictException;
import io.github.zhlzxa.corebanking.posting.IdempotentTransactions;
import io.github.zhlzxa.corebanking.posting.InsufficientBalanceException;
import io.github.zhlzxa.corebanking.posting.LedgerPoster;
import io.github.zhlzxa.corebanking.transaction.BankTransaction;
import io.github.zhlzxa.corebanking.transaction.TransactionRepository;
import io.github.zhlzxa.corebanking.transaction.TransactionStatus;
import io.github.zhlzxa.corebanking.transaction.TransactionType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final long TX_ID = 42;
    private static final AuditContext AUDIT =
            new AuditContext(new AuditActor(1001L, "https://issuer", "alice", "CUSTOMER"), "corr-1", AuditChannel.API);

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerRepository ledgerRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private IndependentAuditRecorder independentAuditRecorder;

    private BestEffortAuditRecorder bestEffortAuditRecorder;

    @Mock
    private DailyTransferUsageRepository dailyTransferUsageRepository;

    @Spy
    private BusinessCalendar businessCalendar = new BusinessCalendar(
            Clock.fixed(Instant.parse("2026-09-18T17:30:00Z"), ZoneOffset.UTC), ZoneId.of("Asia/Hong_Kong"));

    @Spy
    private AuditEventFactory auditEventFactory =
            new AuditEventFactory(Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC));

    private TransferService transferService;

    /**
     * The real posting components are wired around the mocked repositories, so these tests verify
     * the complete sequence of storage operations a transfer performs.
     */
    @BeforeEach
    void createService() {
        bestEffortAuditRecorder = new BestEffortAuditRecorder(independentAuditRecorder);
        transferService = new TransferService(
                new AccountLocks(accountRepository),
                new IdempotentTransactions(transactionRepository),
                new LedgerPoster(accountRepository, ledgerRepository, transactionRepository),
                dailyTransferUsageRepository,
                businessCalendar,
                auditEventRepository,
                auditEventFactory,
                bestEffortAuditRecorder);
    }

    @Test
    void rejectsTransferToSameAccountBeforeTouchingStorage() {
        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 1, "100.00")))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(accountRepository, transactionRepository, ledgerRepository);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "0.00")))
                .isInstanceOf(InvalidTransferException.class);

        verifyNoInteractions(accountRepository, transactionRepository, ledgerRepository);
    }

    @Test
    void completedTransferDebitsCreditsAndPostsBothLegs() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "500.00");
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        BankTransaction result = transferService.transfer(AUDIT, command(1, 2, "100.00"));

        assertThat(result.status()).isEqualTo(TransactionStatus.COMPLETED);
        InOrder order = inOrder(accountRepository, ledgerRepository, transactionRepository);
        order.verify(accountRepository).debit(1, AMOUNT);
        order.verify(accountRepository).credit(2, AMOUNT);
        order.verify(ledgerRepository).append(LedgerEntry.debit(TX_ID, 1, AMOUNT, "HKD"));
        order.verify(ledgerRepository).append(LedgerEntry.credit(TX_ID, 2, AMOUNT, "HKD"));
        order.verify(transactionRepository).updateStatus(TX_ID, TransactionStatus.COMPLETED);
        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).append(audited.capture());
        assertThat(audited.getValue().action()).isEqualTo(AuditAction.TRANSFER_COMPLETED);
        assertThat(audited.getValue().transactionId()).isEqualTo(TX_ID);
        verifyNoInteractions(independentAuditRecorder);
    }

    @Test
    void locksAccountsInAscendingIdOrderRegardlessOfDirection() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "500.00");
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        transferService.transfer(AUDIT, command(2, 1, "100.00"));

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

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(accountRepository, never()).debit(anyLong(), any());
        verify(accountRepository, never()).credit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
        verify(transactionRepository, never()).updateStatus(anyLong(), any());
        verifyNoInteractions(auditEventRepository);
        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(independentAuditRecorder).record(audited.capture());
        assertThat(audited.getValue().outcome()).isEqualTo(AuditOutcome.REJECTED);
        assertThat(audited.getValue().reasonCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(audited.getValue().metadata()).containsEntry("amount", "100.00");
    }

    @Test
    void failureToAuditARejectionNeverMasksTheRejection() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "0.00");
        givenAccount(2, "HKD", "0.00");
        doThrow(new IllegalStateException("audit store down"))
                .when(independentAuditRecorder)
                .record(any());

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
                .isInstanceOf(InsufficientBalanceException.class);
    }

    @Test
    void unexpectedFailureIsAuditedAsFailed() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00");
        doThrow(new IllegalStateException("disk full")).when(ledgerRepository).append(any());

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(independentAuditRecorder).record(audited.capture());
        assertThat(audited.getValue().outcome()).isEqualTo(AuditOutcome.FAILED);
        assertThat(audited.getValue().reasonCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void frozenSourceCannotSend() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00", AccountStatus.FROZEN, null);
        givenAccount(2, "HKD", "0.00");

        assertRuleViolation(command(1, 2, "100.00"), ErrorCode.SOURCE_ACCOUNT_NOT_ACTIVE);
    }

    @Test
    void frozenDestinationCanStillReceive() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00", AccountStatus.FROZEN, null);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        transferService.transfer(AUDIT, command(1, 2, "100.00"));

        verify(accountRepository).credit(2, AMOUNT);
    }

    @Test
    void closedDestinationCannotReceive() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00", AccountStatus.CLOSED, null);

        assertRuleViolation(command(1, 2, "100.00"), ErrorCode.DESTINATION_ACCOUNT_CLOSED);
    }

    @Test
    void amountAbovePerTransactionLimitIsRejected() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00", AccountStatus.ACTIVE, new BigDecimal("99.99"));
        givenAccount(2, "HKD", "0.00");

        assertRuleViolation(command(1, 2, "100.00"), ErrorCode.TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void dailyLimitIsConsumedForTheHongKongBusinessDay() {
        givenClaimedRequest();
        givenAccountWithDailyLimit(1, "500.00");
        givenAccount(2, "HKD", "0.00");
        when(dailyTransferUsageRepository.tryConsume(1, LocalDate.of(2026, 9, 19), AMOUNT, new BigDecimal("500.00")))
                .thenReturn(true);
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        transferService.transfer(AUDIT, command(1, 2, "100.00"));

        verify(accountRepository).debit(1, AMOUNT);
    }

    @Test
    void exhaustedDailyLimitStopsTheTransfer() {
        givenClaimedRequest();
        givenAccountWithDailyLimit(1, "500.00");
        givenAccount(2, "HKD", "0.00");
        when(dailyTransferUsageRepository.tryConsume(anyLong(), any(), any(), any()))
                .thenReturn(false);

        assertRuleViolation(command(1, 2, "100.00"), ErrorCode.TRANSFER_LIMIT_EXCEEDED);
    }

    @Test
    void accountsWithoutDailyLimitDoNotTrackUsage() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00");
        when(transactionRepository.findById(TX_ID)).thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        transferService.transfer(AUDIT, command(1, 2, "100.00"));

        verifyNoInteractions(dailyTransferUsageRepository);
    }

    @Test
    void amountWithMorePrecisionThanTheCurrencyIsRejectedBeforeAnyLock() {
        TransferCommand tooPrecise = new TransferCommand(ownerOf(1), "req-1", 1, 2, new BigDecimal("10.005"), "HKD");

        assertThatThrownBy(() -> transferService.transfer(AUDIT, tooPrecise))
                .isInstanceOfSatisfying(
                        AccountRuleViolationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_AMOUNT_SCALE));
        verifyNoInteractions(accountRepository, transactionRepository);
    }

    @Test
    void unknownCurrencyIsAnInvalidTransfer() {
        TransferCommand unknown = new TransferCommand(ownerOf(1), "req-1", 1, 2, BigDecimal.TEN, "XYZ");

        assertThatThrownBy(() -> transferService.transfer(AUDIT, unknown)).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void currencyMismatchIsRejected() {
        givenClaimedRequest();
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "USD", "0.00");

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
                .isInstanceOf(CurrencyMismatchException.class);

        verifyNoInteractions(ledgerRepository);
    }

    @Test
    void unknownAccountIsReportedBeforeTheRequestIdIsClaimed() {
        givenAccount(1, "HKD", "1000.00");
        when(accountRepository.findByIdForUpdate(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(transactionRepository, ledgerRepository);
    }

    @Test
    void sourceAccountOfAnotherCustomerIsReportedAsNotFound() {
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00");
        TransferCommand fromSomeoneElsesAccount =
                new TransferCommand(ownerOf(2), "req-1", 1, 2, new BigDecimal("100.00"), "HKD");

        assertThatThrownBy(() -> transferService.transfer(AUDIT, fromSomeoneElsesAccount))
                .isInstanceOf(AccountNotFoundException.class);

        verifyNoInteractions(transactionRepository, ledgerRepository);
    }

    @Test
    void bankInternalAccountIsNotAValidDestination() {
        givenAccount(1, "HKD", "1000.00");
        when(accountRepository.findByIdForUpdate(2))
                .thenReturn(Optional.of(
                        new Account(2, null, "HKD", BigDecimal.ZERO, AccountStatus.ACTIVE, null, null, null)));

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "100.00")))
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

        BankTransaction result = transferService.transfer(AUDIT, command(1, 2, "100.0"));

        assertThat(result).isEqualTo(original);
        verify(accountRepository, never()).debit(anyLong(), any());
        verify(accountRepository, never()).credit(anyLong(), any());
        verifyNoInteractions(ledgerRepository, auditEventRepository, independentAuditRecorder);
    }

    @Test
    void reusingRequestIdForDifferentInstructionIsAConflict() {
        givenAccount(1, "HKD", "1000.00");
        givenAccount(2, "HKD", "0.00");
        when(transactionRepository.insertPendingIfAbsent(any())).thenReturn(Optional.empty());
        when(transactionRepository.findByRequestId("req-1"))
                .thenReturn(Optional.of(stored(TransactionStatus.COMPLETED)));

        assertThatThrownBy(() -> transferService.transfer(AUDIT, command(1, 2, "250.00")))
                .isInstanceOf(IdempotencyConflictException.class);

        verify(accountRepository, never()).debit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
    }

    /** Each test account is owned by its own customer, whose user id is derived from the account id. */
    private static long ownerOf(long accountId) {
        return 1000 + accountId;
    }

    private void givenClaimedRequest() {
        when(transactionRepository.insertPendingIfAbsent(any())).thenReturn(Optional.of(TX_ID));
    }

    private void givenAccount(long id, String currency, String balance, AccountStatus status, BigDecimal limit) {
        when(accountRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(
                        new Account(id, ownerOf(id), currency, new BigDecimal(balance), status, null, limit, null)));
    }

    private void givenAccountWithDailyLimit(long id, String dailyLimit) {
        when(accountRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(new Account(
                        id,
                        ownerOf(id),
                        "HKD",
                        new BigDecimal("1000.00"),
                        AccountStatus.ACTIVE,
                        null,
                        null,
                        new BigDecimal(dailyLimit))));
    }

    private void assertRuleViolation(TransferCommand command, ErrorCode expected) {
        assertThatThrownBy(() -> transferService.transfer(AUDIT, command))
                .isInstanceOfSatisfying(
                        AccountRuleViolationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(expected));
        verify(accountRepository, never()).debit(anyLong(), any());
        verifyNoInteractions(ledgerRepository);
    }

    private void givenAccount(long id, String currency, String balance) {
        when(accountRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(new Account(
                        id, ownerOf(id), currency, new BigDecimal(balance), AccountStatus.ACTIVE, null, null, null)));
    }

    private static TransferCommand command(long from, long to, String amount) {
        return new TransferCommand(ownerOf(from), "req-1", from, to, new BigDecimal(amount), "HKD");
    }

    private static BankTransaction stored(TransactionStatus status) {
        return new BankTransaction(
                TX_ID,
                "req-1",
                ownerOf(1),
                TransactionType.TRANSFER,
                status,
                1,
                2,
                new BigDecimal("100.0000"),
                "HKD",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
