package com.atm;

import com.atm.db.JpaManager;
import com.atm.model.Account;
import com.atm.model.ATMState;
import com.atm.service.ATMService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AppTest {

    private ATMService service;

    @BeforeEach
    public void setup() {
        service = new ATMService();
        resetTestData();
    }

    @Test
    public void customerLoginTest() {
        Account account = service.login("1111", "1111");
        assertNotNull(account);
        assertEquals("1111", account.getCardNumber());
    }

    @Test
    public void withdrawTestBalanceDecreases() {
        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 200.0);

        assertTrue(success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(4800.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void depositTestBalanceIncreases() {
        Account account = service.login("1111", "1111");
        boolean success = service.deposit(account, 250.0);

        assertTrue(success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5250.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void transferTestBalancesChange() {
        Account sender = service.login("1111", "1111");
        boolean success = service.transfer(sender, "2222", 500.0);

        assertTrue(success);

        Account refreshedSender = service.getAccountDetails(sender.getAccountId());
        Account receiver = findAccountByCard("2222");

        assertEquals(4500.0, refreshedSender.getBalance(), 0.01);
        assertEquals(3500.0, receiver.getBalance(), 0.01);
    }

    @Test
    public void balanceViewTest() {
        Account account = service.login("1111", "1111");
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
        assertEquals(5000.0, service.getBalance(refreshed), 0.01);
    }

    @Test
    public void technicianViewOnlyATMStatusTest() {
        ATMState before = getATMState();

        String output = captureOutput(service::viewATMStatus);

        ATMState after = getATMState();

        assertTrue(output.contains("ATM STATUS"));
        assertEquals(before.getCash(), after.getCash(), 0.01);
        assertEquals(before.getPaper(), after.getPaper());
        assertEquals(before.getInk(), after.getInk());
    }

    /* ================= INVALID LOGIN TESTS ================= */

    @Test
    public void invalidLoginWrongPinTest() {
        Account account = service.login("1111", "9999");
        assertNull(account);
    }

    @Test
    public void invalidLoginWrongCardTest() {
        Account account = service.login("9999", "1111");
        assertNull(account);
    }

    /* ================= WITHDRAWAL VALIDATION TESTS ================= */

    @Test
    public void withdrawTestInvalidAmountNegativeTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, -100.0);

        assertEquals(false, success);
    }

    @Test
    public void withdrawTestInvalidAmountZeroTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 0.0);

        assertEquals(false, success);
    }

    @Test
    public void withdrawTestInsufficientBalanceTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 6000.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void withdrawTestInsufficientATMCashTest() {
        // Drain ATM cash
        service.collectAllCash();
        
        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 100.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void withdrawTestOutOfPaperTest() {
        ATMState state = getATMState();
        state.setPaper(0);
        updateATMState(state);

        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 100.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void withdrawTestOutOfInkTest() {
        ATMState state = getATMState();
        state.setInk(0);
        updateATMState(state);

        Account account = service.login("1111", "1111");
        boolean success = service.withdraw(account, 100.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    /* ================= DEPOSIT VALIDATION TESTS ================= */

    @Test
    public void depositTestInvalidAmountNegativeTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.deposit(account, -100.0);

        assertEquals(false, success);
    }

    @Test
    public void depositTestInvalidAmountZeroTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.deposit(account, 0.0);

        assertEquals(false, success);
    }

    @Test
    public void depositTestLargeAmountTest() {
        Account account = service.login("1111", "1111");
        boolean success = service.deposit(account, 5000.0);

        assertTrue(success);
        Account refreshed = service.getAccountDetails(account.getAccountId());
        assertEquals(10000.0, refreshed.getBalance(), 0.01);
    }

    /* ================= TRANSFER VALIDATION TESTS ================= */

    @Test
    public void transferTestInvalidAmountNegativeTest() {
        Account sender = service.login("1111", "1111");
        boolean success = service.transfer(sender, "2222", -100.0);

        assertEquals(false, success);
    }

    @Test
    public void transferTestInvalidAmountZeroTest() {
        Account sender = service.login("1111", "1111");
        boolean success = service.transfer(sender, "2222", 0.0);

        assertEquals(false, success);
    }

    @Test
    public void transferTestInsufficientBalanceTest() {
        Account sender = service.login("1111", "1111");
        boolean success = service.transfer(sender, "2222", 6000.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(sender.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void transferTestInvalidReceiverTest() {
        Account sender = service.login("1111", "1111");
        boolean success = service.transfer(sender, "9999", 500.0);

        assertEquals(false, success);
        Account refreshed = service.getAccountDetails(sender.getAccountId());
        assertEquals(5000.0, refreshed.getBalance(), 0.01);
    }

    @Test
    public void transferTestMultipleTransfersTest() {
        Account sender = service.login("1111", "1111");
        
        boolean success1 = service.transfer(sender, "2222", 500.0);
        assertTrue(success1);
        
        boolean success2 = service.transfer(sender, "2222", 300.0);
        assertTrue(success2);

        Account refreshedSender = service.getAccountDetails(sender.getAccountId());
        Account receiver = findAccountByCard("2222");

        assertEquals(4200.0, refreshedSender.getBalance(), 0.01);
        assertEquals(3800.0, receiver.getBalance(), 0.01);
    }

    /* ================= TECHNICIAN OPERATIONS TESTS ================= */

    @Test
    public void refillPaperTest() {
        service.refillPaper(10);
        ATMState state = getATMState();
        assertEquals(30, state.getPaper());
    }

    @Test
    public void refillInkTest() {
        service.refillInk(15);
        ATMState state = getATMState();
        assertEquals(35, state.getInk());
    }

    @Test
    public void addCashToATMTest() {
        service.addCashToATM(5000.0);
        ATMState state = getATMState();
        assertEquals(15000.0, state.getCash(), 0.01);
    }

    @Test
    public void addInvalidCashToATMTest() {
        ATMState before = getATMState();
        service.addCashToATM(-500.0);
        ATMState after = getATMState();
        assertEquals(before.getCash(), after.getCash(), 0.01);
    }

    @Test
    public void collectAllCashTest() {
        service.collectAllCash();
        ATMState state = getATMState();
        assertEquals(0.0, state.getCash(), 0.01);
    }

    @Test
    public void updateFirmwareTest() {
        service.updateFirmware("v2.0");
        ATMState state = getATMState();
        assertEquals("v2.0", state.getFirmwareVersion());
    }

    @Test
    public void atmStatusAfterMultipleOperationsTest() {
        // Get the initial ATM state before operations
        ATMState initialState = getATMState();
        double initialCash = initialState.getCash();
        int initialPaper = initialState.getPaper();
        
        // Perform various operations
        Account account = service.login("1111", "1111");
        service.withdraw(account, 200.0);
        service.deposit(account, 300.0);
        service.refillPaper(5);
        service.addCashToATM(1000.0);

        // Check ATM status is consistent
        ATMState state = getATMState();
        // Cash should be: initial - 200 (withdrawal) + 300 (deposit) + 1000 (added)
        assertEquals(initialCash - 200.0 + 300.0 + 1000.0, state.getCash(), 0.01);
        // Paper should be: initial + 5 (refilled)
        assertEquals(initialPaper + 5, state.getPaper());
        
        Account refreshed = service.getAccountDetails(account.getAccountId());
        // Account balance: 5000 - 200 + 300 = 5100
        assertEquals(5100.0, refreshed.getBalance(), 0.01);
    }

    private void resetTestData() {
        EntityManager em = JpaManager.getEntityManager();
        try {
            em.getTransaction().begin();
            em.createQuery("DELETE FROM Transaction").executeUpdate();
            em.createQuery("DELETE FROM Account").executeUpdate();
            em.createQuery("DELETE FROM ATMState").executeUpdate();
            em.getTransaction().commit();

            em.getTransaction().begin();

            Account acc1 = new Account();
            acc1.setAccountId("ACC001");
            acc1.setCardNumber("1111");
            acc1.setPin("1111");
            acc1.setBalance(5000.0);
            acc1.setFailedAttempts(0);

            Account acc2 = new Account();
            acc2.setAccountId("ACC002");
            acc2.setCardNumber("2222");
            acc2.setPin("2222");
            acc2.setBalance(3000.0);
            acc2.setFailedAttempts(0);

            ATMState atmState = new ATMState();
            atmState.setCash(10000.0);
            atmState.setPaper(20);
            atmState.setInk(20);
            atmState.setFirmwareVersion("v1.0");

            em.persist(acc1);
            em.persist(acc2);
            em.persist(atmState);

            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private Account findAccountByCard(String cardNumber) {
        EntityManager em = JpaManager.getEntityManager();
        try {
            return em.createQuery(
                    "SELECT a FROM Account a WHERE a.cardNumber = :card",
                    Account.class
                )
                .setParameter("card", cardNumber)
                .getSingleResult();
        } finally {
            em.close();
        }
    }

    private ATMState getATMState() {
        EntityManager em = JpaManager.getEntityManager();
        try {
            return em.createQuery("SELECT a FROM ATMState a", ATMState.class)
                .getSingleResult();
        } finally {
            em.close();
        }
    }

    private void updateATMState(ATMState state) {
        EntityManager em = JpaManager.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(state);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private String captureOutput(Runnable action) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
            return buffer.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }
    }
}
