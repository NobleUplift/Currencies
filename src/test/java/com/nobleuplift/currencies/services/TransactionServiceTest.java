package com.nobleuplift.currencies.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.nobleuplift.currencies.CurrenciesException;
import com.nobleuplift.currencies.CurrenciesRuntimeException;
import com.nobleuplift.currencies.entities.Account;
import com.nobleuplift.currencies.entities.Currency;
import com.nobleuplift.currencies.entities.Holding;
import com.nobleuplift.currencies.entities.Transaction;
import com.nobleuplift.currencies.entities.TransactionType;
import com.nobleuplift.currencies.entities.Unit;

class TransactionServiceTest {

    private FakeCurrencyRepository repository;
    private JdbcWriteSupport jdbc;
    private Ledger ledger;
    private AccountService accountService;
    private CurrencyFormatter formatter;
    private CurrencyService currencyService;
    private TransactionService transactionService;

    private Currency gbp;
    private Unit pound;
    private Unit penny;
    private Account alice;
    private Account bob;
    private Account bank;

    @BeforeEach
    void setUp() throws SQLException {
        repository = new FakeCurrencyRepository();
        jdbc = new JdbcWriteSupport();
        ledger = mock(Ledger.class);
        accountService = mock(AccountService.class);
        formatter = mock(CurrencyFormatter.class);
        currencyService = mock(CurrencyService.class);
        transactionService = new TransactionService(jdbc.connectionProvider, repository, ledger, accountService, formatter, currencyService);

        gbp = Fixtures.currency((short) 1, "GBP", "Pound Sterling", true);
        repository.addCurrency(gbp);
        penny = Fixtures.baseUnit((short) 2, gbp, "Penny", "p");
        pound = Fixtures.parentUnit((short) 1, gbp, "Pound", "£", true, 100, penny);
        repository.addUnit(pound);
        repository.addUnit(penny);

        alice = Fixtures.account(10, "Alice");
        bob = Fixtures.account(11, "Bob");
        bank = Fixtures.account(1, "Minecraft Central Bank"); // reserved: id 1
        repository.addAccount(alice);
        repository.addAccount(bob);
        repository.addAccount(bank);
    }

    // ---- balance ----

    @Test
    void balanceThrowsWhenPlayerUnknown() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.balance("Nobody"));
        assertEquals("Account Nobody does not exist.", e.getMessage());
    }

    @Test
    void balanceWithoutAcronymSumsAllHoldings() throws CurrenciesException {
        repository.addHolding(alice.getId(), penny, 250);

        Map<Currency, Long> result = transactionService.balance("Alice");

        assertEquals(250L, result.get(gbp));
    }

    @Test
    void balanceOneArgDelegatesToTwoArgWithNullAcronym() throws CurrenciesException {
        repository.addHolding(alice.getId(), penny, 100);

        assertEquals(transactionService.balance("Alice", null), transactionService.balance("Alice"));
    }

    @Test
    void balanceWithAcronymThrowsWhenCurrencyUnknown() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.balance("Alice", "ZZZ"));
        assertEquals("Currency with acronym ZZZ does not exist.", e.getMessage());
    }

    @Test
    void balanceWithAcronymThrowsWhenAccountOwnsNothingOfThatCurrency() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.balance("Alice", "GBP"));
        assertEquals("Account Alice does not own any Pounds.", e.getMessage());
    }

    @Test
    void balanceWithAcronymReturnsHoldingsForThatCurrencyOnly() throws CurrenciesException {
        repository.addHolding(alice.getId(), penny, 400);

        Map<Currency, Long> result = transactionService.balance("Alice", "GBP");

        assertEquals(400L, result.get(gbp));
    }

    // ---- pay ----

    @Test
    void payStringOverloadResolvesCollaboratorsThenDelegates() throws CurrenciesException, SQLException {
        when(accountService.getAccountFromPlayer("Alice", true)).thenReturn(alice);
        when(accountService.getAccountFromPlayer("Bob", true)).thenReturn(bob);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(formatter.parseCurrency(gbp, "£1")).thenReturn(100L);
        repository.addHolding(alice.getId(), penny, 500);
        Transaction stub = new Transaction();
        when(ledger.privateTransferAmount(any(), eq(alice), eq(bob), eq(gbp), eq(100L))).thenReturn(stub);
        when(ledger.insertTransaction(any(), eq(stub))).thenReturn(9L);

        Transaction result = transactionService.pay("Alice", "Bob", "GBP", "£1");

        assertEquals(9L, result.getId());
        assertEquals(TransactionType.PAY.getId(), result.getTypeId());
    }

    @Test
    void payRejectsPayingYourself() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(alice, alice, gbp, 100L));
        assertEquals("You cannot pay yourself.", e.getMessage());
    }

    @Test
    void payRejectsFromReservedAccount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(bank, alice, gbp, 100L));
        assertEquals("Reserved accounts cannot pay.", e.getMessage());
    }

    @Test
    void payRejectsToReservedAccount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(alice, bank, gbp, 100L));
        assertEquals("Cannot pay a reserved account.", e.getMessage());
    }

    @Test
    void payRejectsNonPositiveAmount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(alice, bob, gbp, 0L));
        assertEquals("Cannot pay someone a negative amount.", e.getMessage());
    }

    @Test
    void payThrowsWhenAccountHasNoHoldingOfThatCurrency() {
        when(formatter.formatCurrency(gbp, 100L)).thenReturn("£1");

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(alice, bob, gbp, 100L));
        assertEquals("You have 0p. You cannot pay £1 to Bob.", e.getMessage());
    }

    @Test
    void payThrowsWhenBalanceInsufficient() {
        repository.addHolding(alice.getId(), penny, 50);
        when(formatter.formatCurrency(gbp, 100L)).thenReturn("£1");
        when(formatter.formatCurrency(gbp, 50L)).thenReturn("50p");

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.pay(alice, bob, gbp, 100L));
        assertEquals("Cannot pay £1 to Bob because it is greater than 50p, your current balance.", e.getMessage());
    }

    @Test
    void payHappyPathTransfersFundsAndCommits() throws CurrenciesException, SQLException {
        repository.addHolding(alice.getId(), penny, 500);
        Transaction stub = new Transaction();
        when(ledger.privateTransferAmount(any(), eq(alice), eq(bob), eq(gbp), eq(100L))).thenReturn(stub);
        when(ledger.insertTransaction(any(), eq(stub))).thenReturn(9L);

        Transaction result = transactionService.pay(alice, bob, gbp, 100L);

        verify(ledger).compactHoldings(any(), eq(alice));
        assertEquals(9L, result.getId());
        assertEquals(TransactionType.PAY.getId(), result.getTypeId());
        verify(jdbc.connection).commit();
    }

    // ---- bill ----

    @Test
    void billStringOverloadResolvesCollaboratorsThenDelegates() throws CurrenciesException, SQLException {
        when(accountService.getAccountFromPlayer("Bob", true)).thenReturn(bob);
        when(accountService.getAccountFromPlayer("Alice", true)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(formatter.parseCurrency(gbp, "£1")).thenReturn(100L);
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getLong(1)).thenReturn(200L);

        Transaction result = transactionService.bill("Bob", "Alice", "GBP", "£1");

        assertEquals(200L, result.getId());
        assertNull(result.isPaid());
    }

    @Test
    void billRejectsBillingYourself() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.bill(alice, alice, gbp, 100L));
        assertEquals("You cannot bill yourself.", e.getMessage());
    }

    @Test
    void billRejectsNonPositiveAmount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.bill(bob, alice, gbp, 0L));
        assertEquals("Cannot bill someone a negative amount.", e.getMessage());
    }

    @Test
    void billThrowsWhenCurrencyHasNoBaseUnit() {
        Currency noBase = Fixtures.currency((short) 9, "XXX", "No Base", true);
        repository.addCurrency(noBase);

        assertThrows(CurrenciesRuntimeException.class, () -> transactionService.bill(bob, alice, noBase, 100L));
    }

    @Test
    void billHappyPathCreatesPendingTransaction() throws CurrenciesException, SQLException {
        when(jdbc.generatedKeys.next()).thenReturn(true);
        when(jdbc.generatedKeys.getLong(1)).thenReturn(201L);

        Transaction result = transactionService.bill(bob, alice, gbp, 500L);

        assertEquals(201L, result.getId());
        assertEquals(alice, result.getSender());
        assertEquals(bob, result.getRecipient());
        assertEquals(TransactionType.BILL.getId(), result.getTypeId());
        assertNull(result.isPaid());
        verify(jdbc.connection).commit();
    }

    @Test
    void billThrowsWhenNoGeneratedKeyIsReturned() throws SQLException {
        when(jdbc.generatedKeys.next()).thenReturn(false);

        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.bill(bob, alice, gbp, 500L));
        assertEquals("Failed to insert bill transaction.", e.getMessage());
        verify(jdbc.connection).rollback();
    }

    // ---- processBill ----

    private Transaction pendingBill(long id, Account sender, Account recipient, long amount) {
        Transaction t = new Transaction();
        t.setId(id);
        t.setSender(sender);
        t.setRecipient(recipient);
        t.setUnit(penny);
        t.setTypeId(TransactionType.BILL.getId());
        t.setTransactionAmount(amount);
        t.setPaid(null);
        repository.addTransaction(t);
        return t;
    }

    @Test
    void processBillThrowsWhenAccountUnknown() {
        assertThrows(CurrenciesRuntimeException.class, () -> transactionService.processBill("Nobody", true));
    }

    @Test
    void processBillWithoutIdThrowsWhenMoreThanOnePendingBill() {
        pendingBill(1L, alice, bob, 100L);
        pendingBill(2L, alice, bob, 200L);

        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.processBill("Alice", true));
        assertEquals("You have more than one bill pending. Please specify the transaction ID. You can find it by running /transactions.",
                e.getMessage());
    }

    @Test
    void processBillWithoutIdThrowsWhenNoPendingBills() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.processBill("Alice", true));
        assertEquals("You have no bills pending. ", e.getMessage());
    }

    @Test
    void processBillWithIdThrowsWhenTransactionUnknown() {
        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.processBill("Alice", true, "999"));
        assertEquals("Transaction 999 does not exist.", e.getMessage());
    }

    @Test
    void processBillWithIdThrowsWhenCallerIsNotTheSender() {
        Transaction bill = pendingBill(5L, bob, alice, 100L);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.processBill("Alice", true, String.valueOf(bill.getId())));
        assertEquals("You can only pay/reject bills sent to yourself.", e.getMessage());
    }

    @Test
    void processBillThrowsWhenTransactionIsNotABill() {
        Transaction notABill = new Transaction();
        notABill.setId(6L);
        notABill.setSender(alice);
        notABill.setRecipient(bob);
        notABill.setUnit(penny);
        notABill.setTypeId(TransactionType.PAY.getId());
        notABill.setPaid(true);
        repository.addTransaction(notABill);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.processBill("Alice", true, "6"));
        assertEquals("Transaction is not a bill.", e.getMessage());
    }

    @Test
    void processBillThrowsWhenAlreadyPaid() {
        Transaction bill = pendingBill(7L, alice, bob, 100L);
        bill.setPaid(true);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.processBill("Alice", true, "7"));
        assertEquals("Bill has already been paid.", e.getMessage());
    }

    @Test
    void processBillThrowsWhenAlreadyRejected() {
        Transaction bill = pendingBill(7L, alice, bob, 100L);
        bill.setPaid(false);

        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.processBill("Alice", true, "7"));
        assertEquals("Bill has already been rejected.", e.getMessage());
    }

    @Test
    void processBillAcceptingTransfersFundsAndMarksPaid() throws CurrenciesException, SQLException {
        Transaction bill = pendingBill(8L, alice, bob, 100L);
        repository.addHolding(alice.getId(), penny, 500);
        when(ledger.privateTransferAmount(any(), eq(alice), eq(bob), eq(gbp), eq(100L))).thenReturn(new Transaction());

        Transaction result = transactionService.processBill("Alice", true, "8");

        verify(ledger).compactHoldings(any(), eq(alice));
        verify(ledger).privateTransferAmount(any(), eq(alice), eq(bob), eq(gbp), eq(100L));
        assertEquals(Boolean.TRUE, result.isPaid());
        assertEquals(bill, result);
        verify(jdbc.connection).commit();
    }

    @Test
    void processBillAcceptingThrowsWhenBalanceInsufficient() {
        pendingBill(8L, alice, bob, 100L);
        when(formatter.formatCurrency(gbp, 100L)).thenReturn("£1");

        CurrenciesException e = assertThrows(CurrenciesException.class, () -> transactionService.processBill("Alice", true, "8"));
        assertEquals("You have 0p. You cannot pay £1 to Bob.", e.getMessage());
    }

    @Test
    void processBillRejectingDoesNotTransferFunds() throws CurrenciesException, SQLException {
        pendingBill(8L, alice, bob, 100L);

        Transaction result = transactionService.processBill("Alice", false, "8");

        verify(ledger, never()).compactHoldings(any(), any());
        verify(ledger, never()).privateTransferAmount(any(), any(), any(), any(), anyLong());
        assertEquals(Boolean.FALSE, result.isPaid());
        verify(jdbc.connection).commit();
    }

    @Test
    void processBillTwoArgOverloadDefaultsToNoExplicitTransaction() throws CurrenciesException, SQLException {
        pendingBill(8L, alice, bob, 100L);
        repository.addHolding(alice.getId(), penny, 500);
        when(ledger.privateTransferAmount(any(), eq(alice), eq(bob), eq(gbp), eq(100L))).thenReturn(new Transaction());

        Transaction result = transactionService.processBill("Alice", true);

        assertEquals(8L, result.getId());
    }

    // ---- transactions ----

    @Test
    void transactionsThrowsWhenPlayerUnknown() {
        assertThrows(CurrenciesRuntimeException.class, () -> transactionService.transactions("Nobody"));
    }

    @Test
    void transactionsReturnsAccountHistory() throws CurrenciesException {
        Transaction t = pendingBill(1L, alice, bob, 100L);

        List<Transaction> result = transactionService.transactions("Alice", 1);

        assertTrue(result.contains(t));
    }

    @Test
    void transactionsOneArgDelegatesToPageOne() throws CurrenciesException {
        pendingBill(1L, alice, bob, 100L);

        assertEquals(transactionService.transactions("Alice", 1), transactionService.transactions("Alice"));
    }

    // ---- credit / debit ----

    @Test
    void creditRejectsReservedAccount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.credit(bank, gbp, 100L));
        assertEquals("Cannot credit a reserved account.", e.getMessage());
    }

    @Test
    void creditRejectsNonPositiveAmount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.credit(alice, gbp, 0L));
        assertEquals("Cannot credit someone a negative amount.", e.getMessage());
    }

    @Test
    void creditHappyPathTransfersFromCentralBank() throws CurrenciesException, SQLException {
        when(accountService.getMinecraftCentralBank()).thenReturn(bank);
        Transaction stub = new Transaction();
        when(ledger.privateTransferAmount(any(), eq(bank), eq(alice), eq(gbp), eq(100L))).thenReturn(stub);
        when(ledger.insertTransaction(any(), eq(stub))).thenReturn(3L);

        Transaction result = transactionService.credit(alice, gbp, 100L);

        assertEquals(3L, result.getId());
        assertEquals(TransactionType.CREDIT.getId(), result.getTypeId());
        verify(jdbc.connection).commit();
    }

    @Test
    void creditStringOverloadResolvesCollaborators() throws CurrenciesException, SQLException {
        when(accountService.getAccountFromPlayer("Alice", true)).thenReturn(alice);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(formatter.parseCurrency(gbp, "£1")).thenReturn(100L);
        when(accountService.getMinecraftCentralBank()).thenReturn(bank);
        Transaction stub = new Transaction();
        when(ledger.privateTransferAmount(any(), eq(bank), eq(alice), eq(gbp), eq(100L))).thenReturn(stub);
        when(ledger.insertTransaction(any(), eq(stub))).thenReturn(4L);

        Transaction result = transactionService.credit("Alice", "GBP", "£1");

        assertEquals(4L, result.getId());
    }

    @Test
    void debitRejectsReservedAccount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.debit(bank, gbp, 100L));
        assertEquals("Cannot debit a reserved account.", e.getMessage());
    }

    @Test
    void debitRejectsNonPositiveAmount() {
        CurrenciesException e = assertThrows(CurrenciesException.class,
                () -> transactionService.debit(alice, gbp, 0L));
        assertEquals("Cannot debit someone a negative amount.", e.getMessage());
    }

    @Test
    void debitHappyPathTransfersToCentralBank() throws CurrenciesException, SQLException {
        when(accountService.getMinecraftCentralBank()).thenReturn(bank);
        Transaction stub = new Transaction();
        when(ledger.privateTransferAmount(any(), eq(alice), eq(bank), eq(gbp), eq(100L))).thenReturn(stub);
        when(ledger.insertTransaction(any(), eq(stub))).thenReturn(5L);

        Transaction result = transactionService.debit(alice, gbp, 100L);

        assertEquals(5L, result.getId());
        assertEquals(TransactionType.DEBIT.getId(), result.getTypeId());
    }

    // ---- bankrupt ----

    @Test
    void bankruptThrowsWhenPlayerUnknown() {
        assertThrows(CurrenciesRuntimeException.class, () -> transactionService.bankrupt("Nobody"));
    }

    @Test
    void bankruptWithAmountTransfersEachHoldingToBankerThenCreditsBankruptAmount() throws CurrenciesException, SQLException {
        Account banker = Fixtures.account(2, "Minecraft Central Banker");
        repository.addAccount(banker);
        repository.addHolding(alice.getId(), penny, 300);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(formatter.parseCurrency(gbp, "£1")).thenReturn(100L);
        when(ledger.privateTransferAmount(any(), any(), any(), any(), anyLong())).thenReturn(new Transaction());
        when(ledger.insertTransaction(any(), any())).thenReturn(1L);

        List<Holding> result = transactionService.bankrupt("Alice", "GBP", "£1");

        assertEquals(1, result.size());
        verify(ledger).compactHoldings(any(), eq(alice));
        verify(ledger).privateTransferAmount(any(), eq(alice), eq(banker), eq(gbp), eq(300L));
        verify(ledger).privateTransferAmount(any(), eq(bank), eq(alice), eq(gbp), eq(100L));
        verify(ledger, times(2)).insertTransaction(any(), any());
        verify(jdbc.connection).commit();
    }

    @Test
    void bankruptWithAmountDeletesZeroHoldingsInsteadOfTransferring() throws CurrenciesException, SQLException {
        repository.addAccount(Fixtures.account(2, "Minecraft Central Banker"));
        repository.addHolding(alice.getId(), penny, 0);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(formatter.parseCurrency(gbp, "£0")).thenReturn(0L);
        when(ledger.privateTransferAmount(any(), any(), any(), any(), anyLong())).thenReturn(new Transaction());

        transactionService.bankrupt("Alice", "GBP", "£0");

        assertNull(repository.getHolding(alice.getId(), penny.getId()));
        verify(ledger, never()).privateTransferAmount(any(), eq(alice), any(), any(), anyLong());
    }

    @Test
    void bankruptWithAcronymOnlyZeroesJustThatCurrency() throws CurrenciesException, SQLException {
        repository.addAccount(Fixtures.account(2, "Minecraft Central Banker"));
        repository.addHolding(alice.getId(), penny, 150);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);
        when(ledger.privateTransferAmount(any(), any(), any(), any(), anyLong())).thenReturn(new Transaction());

        // bankrupt(player, acronym) is a void wrapper delegating to bankrupt(player, acronym, null);
        // call the 3-arg form directly with a null amount to exercise the same branch and get the result.
        List<Holding> result = transactionService.bankrupt("Alice", "GBP", null);

        assertEquals(1, result.size());
        verify(ledger).privateTransferAmount(any(), eq(alice), any(), eq(gbp), eq(150L));
        verify(formatter, never()).parseCurrency(any(), any());
    }

    @Test
    void bankruptTwoArgOverloadDelegatesToThreeArgWithNullAmount() throws CurrenciesException {
        // A zero-amount holding is deleted directly (not routed through the mocked Ledger), so it's
        // an observable side effect proving the delegation actually ran the real bankrupt() logic.
        repository.addAccount(Fixtures.account(2, "Minecraft Central Banker"));
        repository.addHolding(alice.getId(), penny, 0);
        when(currencyService.getCurrencyFromAcronym("GBP", true)).thenReturn(gbp);

        transactionService.bankrupt("Alice", "GBP");

        assertNull(repository.getHolding(alice.getId(), penny.getId()));
    }

    @Test
    void bankruptWithNoArgsZeroesEveryCurrency() throws CurrenciesException, SQLException {
        repository.addAccount(Fixtures.account(2, "Minecraft Central Banker"));
        repository.addHolding(alice.getId(), penny, 75);
        when(ledger.privateTransferAmount(any(), any(), any(), any(), anyLong())).thenReturn(new Transaction());

        // bankrupt(player) is a void wrapper delegating to bankrupt(player, null, null); call the
        // 3-arg form directly with null acronym/amount to exercise the same branch and get the result.
        List<Holding> result = transactionService.bankrupt("Alice", null, null);

        assertEquals(1, result.size());
        verify(ledger).privateTransferAmount(any(), eq(alice), any(), eq(gbp), eq(75L));
        verify(currencyService, never()).getCurrencyFromAcronym(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void bankruptOneArgOverloadDelegatesToThreeArgWithNullAcronymAndAmount() throws CurrenciesException {
        // Same reasoning as the two-arg delegation test: a zero-amount holding is deleted directly.
        repository.addAccount(Fixtures.account(2, "Minecraft Central Banker"));
        repository.addHolding(alice.getId(), penny, 0);

        transactionService.bankrupt("Alice");

        assertNull(repository.getHolding(alice.getId(), penny.getId()));
    }

    // ---- summateHoldings ----

    @Test
    void summateHoldingsReturnsEmptyMapForNoHoldings() {
        assertTrue(transactionService.summateHoldings(List.of()).isEmpty());
    }

    @Test
    void summateHoldingsAddsBaseUnitAmountsDirectly() {
        Holding h = new Holding();
        h.setUnit(penny);
        h.setAmount(50L);

        Map<Currency, Long> result = transactionService.summateHoldings(List.of(h));

        assertEquals(50L, result.get(gbp));
    }

    @Test
    void summateHoldingsConvertsNonBaseUnitsToBaseAmount() {
        Holding h = new Holding();
        h.setUnit(pound); // 1 Pound = 100 pence (baseMultiples)
        h.setAmount(2L);

        Map<Currency, Long> result = transactionService.summateHoldings(List.of(h));

        assertEquals(200L, result.get(gbp));
    }

    @Test
    void summateHoldingsAccumulatesAcrossMultipleHoldingsOfSameCurrency() {
        Holding poundHolding = new Holding();
        poundHolding.setUnit(pound);
        poundHolding.setAmount(1L); // 100 pence

        Holding pennyHolding = new Holding();
        pennyHolding.setUnit(penny);
        pennyHolding.setAmount(30L);

        Map<Currency, Long> result = transactionService.summateHoldings(List.of(poundHolding, pennyHolding));

        assertEquals(130L, result.get(gbp));
    }
}
