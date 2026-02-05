package com.atm.service;

import com.atm.db.JpaManager;
import com.atm.model.Account;
import com.atm.model.ATMState;
import com.atm.model.Transaction;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ATMService {

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /* ================= LOGIN ================= */

    public Account login(String cardNumber, String pin) {
        EntityManager em = JpaManager.getEntityManager();
        try {
            TypedQuery<Account> q = em.createQuery(
                "SELECT a FROM Account a WHERE a.cardNumber = :card AND a.pin = :pin",
                Account.class
            );
            q.setParameter("card", cardNumber);
            q.setParameter("pin", pin);

            Account account = q.getSingleResult();
            account.setFailedAttempts(0); // Reset failed attempts on successful login
            
            em.getTransaction().begin();
            em.merge(account);
            em.getTransaction().commit();
            
            return account;
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }

    /* ================= ACCOUNT OPERATIONS ================= */

    public Account getAccountDetails(String accountId) {
        EntityManager em = JpaManager.getEntityManager();
        try {
            return em.find(Account.class, accountId);
        } finally {
            em.close();
        }
    }

    public double getBalance(Account account) {
        return account.getBalance();
    }

    /* ================= DEPOSIT ================= */

    public boolean deposit(Account account, double amount) {
        if (amount <= 0) {
            System.out.println("Invalid amount. Please enter a positive number.");
            return false;
        }

        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            Account managed = em.find(Account.class, account.getAccountId());
            ATMState atmState = getATMState(em);

            managed.setBalance(managed.getBalance() + amount);
            atmState.addCash(amount);

            Transaction t = new Transaction();
            t.setAccount(managed);
            t.setAmount(amount);
            t.setType("DEPOSIT");
            t.setTime(LocalDateTime.now());

            em.persist(t);
            em.merge(atmState);
            em.getTransaction().commit();

            System.out.println("Deposit successful.");
            account.setBalance(managed.getBalance());
            return true;
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Deposit failed: " + e.getMessage());
            return false;
        } finally {
            em.close();
        }
    }

    /* ================= WITHDRAW ================= */

    public boolean withdraw(Account account, double amount) {
        if (amount <= 0) {
            System.out.println("Invalid amount. Please enter a positive number.");
            return false;
        }

        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            Account managed = em.find(Account.class, account.getAccountId());
            ATMState atmState = getATMState(em);

            // Validate
            if (managed.getBalance() < amount) {
                System.out.println("Insufficient balance.");
                em.getTransaction().rollback();
                return false;
            }

            if (atmState.getCash() < amount) {
                System.out.println("ATM has insufficient cash.");
                em.getTransaction().rollback();
                return false;
            }

            if (atmState.getPaper() < 1) {
                System.out.println("ATM is out of paper.");
                em.getTransaction().rollback();
                return false;
            }

            if (atmState.getInk() < 1) {
                System.out.println("ATM is out of ink.");
                em.getTransaction().rollback();
                return false;
            }

            // Process withdrawal
            managed.setBalance(managed.getBalance() - amount);
            atmState.setCash(atmState.getCash() - amount);

            Transaction t = new Transaction();
            t.setAccount(managed);
            t.setAmount(amount);
            t.setType("WITHDRAW");
            t.setTime(LocalDateTime.now());

            em.persist(t);
            em.merge(atmState);
            em.getTransaction().commit();

            System.out.println("Withdrawal successful.");
            account.setBalance(managed.getBalance());
            return true;
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Withdrawal failed: " + e.getMessage());
            return false;
        } finally {
            em.close();
        }
    }

    /* ================= TRANSFER ================= */

    public boolean transfer(Account from, String toCardNumber, double amount) {
        if (amount <= 0) {
            System.out.println("Invalid amount. Please enter a positive number.");
            return false;
        }

        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            Account sender = em.find(Account.class, from.getAccountId());

            TypedQuery<Account> q = em.createQuery(
                "SELECT a FROM Account a WHERE a.cardNumber = :card",
                Account.class
            );
            q.setParameter("card", toCardNumber);

            Account receiver;
            try {
                receiver = q.getSingleResult();
            } catch (NoResultException e) {
                System.out.println("Target account not found.");
                em.getTransaction().rollback();
                return false;
            }

            if (sender.getBalance() < amount) {
                System.out.println("Insufficient balance.");
                em.getTransaction().rollback();
                return false;
            }

            // Process transfer
            sender.setBalance(sender.getBalance() - amount);
            receiver.setBalance(receiver.getBalance() + amount);

            Transaction t1 = new Transaction();
            t1.setAccount(sender);
            t1.setAmount(amount);
            t1.setType("TRANSFER_OUT");
            t1.setTime(LocalDateTime.now());

            Transaction t2 = new Transaction();
            t2.setAccount(receiver);
            t2.setAmount(amount);
            t2.setType("TRANSFER_IN");
            t2.setTime(LocalDateTime.now());

            em.persist(t1);
            em.persist(t2);
            em.getTransaction().commit();

            System.out.println("Transfer successful.");
            from.setBalance(sender.getBalance());
            return true;
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Transfer failed: " + e.getMessage());
            return false;
        } finally {
            em.close();
        }
    }

    /* ================= RECEIPT ================= */

    public void printReceipt(String type, double amount, double balance) {
        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);

            boolean outOfPaper = state.getPaper() <= 0;
            boolean outOfInk = state.getInk() <= 0;

            if (outOfPaper || outOfInk) {
                em.getTransaction().rollback();
                System.out.println("Transaction successful.");
                if (outOfPaper && outOfInk) {
                    System.out.println("Sorry, we cannot print the receipt. The ATM is out of paper and ink.");
                } else if (outOfPaper) {
                    System.out.println("Sorry, we cannot print the receipt. The ATM is out of paper.");
                } else {
                    System.out.println("Sorry, we cannot print the receipt. The ATM is out of ink.");
                }
                return;
            }

            // Decrement paper and ink when printing receipt
            state.setPaper(state.getPaper() - 1);
            state.setInk(state.getInk() - 1);

            em.merge(state);
            em.getTransaction().commit();
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Error updating receipt supplies: " + e.getMessage());
        } finally {
            em.close();
        }

        System.out.println("\n--------- RECEIPT ---------");
        System.out.printf("Type   : %s\n", type);
        System.out.printf("Amount : %.2f\n", amount);
        System.out.printf("Balance: %.2f\n", balance);
        System.out.printf("Date   : %s\n", LocalDateTime.now().format(dateFormatter));
        System.out.println("---------------------------\n");
    }

    /* ================= TECHNICIAN OPERATIONS ================= */

    public void viewATMStatus() {
        EntityManager em = JpaManager.getEntityManager();
        try {
            ATMState state = getATMState(em);
            System.out.println("\n===== ATM STATUS =====");
            System.out.printf("Cash: $%.2f\n", state.getCash());
            System.out.printf("Paper: %d\n", state.getPaper());
            System.out.printf("Ink: %d\n", state.getInk());
            System.out.printf("Firmware: %s\n", state.getFirmwareVersion());
            System.out.println("Banknotes:");
            System.out.printf("  $20 notes: %d\n", state.getNotes20());
            System.out.printf("  $50 notes: %d\n", state.getNotes50());
            System.out.printf("  $100 notes: %d\n", state.getNotes100());
            System.out.println("====================\n");
        } finally {
            em.close();
        }
    }

    public void refillPaper(int amount) {
        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);
            state.refillPaper(amount);
            em.merge(state);
            em.getTransaction().commit();
            System.out.printf("Paper refilled by %d. Total: %d\n", amount, state.getPaper());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Refill failed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void refillInk(int amount) {
        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);
            state.refillInk(amount);
            em.merge(state);
            em.getTransaction().commit();
            System.out.printf("Ink refilled by %d. Total: %d\n", amount, state.getInk());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Refill failed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void addCashToATM(int notes20, int notes50, int notes100) {
        if (notes20 < 0 || notes50 < 0 || notes100 < 0) {
            System.out.println("Invalid amount. Banknotes cannot be negative.");
            return;
        }

        double totalCash = (notes20 * 20) + (notes50 * 50) + (notes100 * 100);
        
        if (totalCash == 0) {
            System.out.println("Please add at least one banknote.");
            return;
        }

        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);
            state.setCash(state.getCash() + totalCash);
            state.setNotes20(state.getNotes20() + notes20);
            state.setNotes50(state.getNotes50() + notes50);
            state.setNotes100(state.getNotes100() + notes100);
            em.merge(state);
            em.getTransaction().commit();
            System.out.printf("\n=== CASH ADDED ===");
            System.out.printf("$20 notes added: %d\n", notes20);
            System.out.printf("$50 notes added: %d\n", notes50);
            System.out.printf("$100 notes added: %d\n", notes100);
            System.out.printf("Total cash added: $%.2f\n", totalCash);
            System.out.printf("ATM total cash: $%.2f\n==================\n", state.getCash());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Operation failed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void collectCash(int notes20, int notes50, int notes100) {
        if (notes20 < 0 || notes50 < 0 || notes100 < 0) {
            System.out.println("Invalid banknote quantities. Please enter positive numbers.");
            return;
        }

        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);
            
            // Check if requested banknotes are available
            if (state.getNotes20() < notes20) {
                System.out.println("Not enough $20 notes. Available: " + state.getNotes20());
                em.getTransaction().rollback();
                return;
            }
            if (state.getNotes50() < notes50) {
                System.out.println("Not enough $50 notes. Available: " + state.getNotes50());
                em.getTransaction().rollback();
                return;
            }
            if (state.getNotes100() < notes100) {
                System.out.println("Not enough $100 notes. Available: " + state.getNotes100());
                em.getTransaction().rollback();
                return;
            }
            
            // Calculate total amount
            double totalAmount = (notes20 * 20) + (notes50 * 50) + (notes100 * 100);
            
            // Update ATM state
            state.setCash(state.getCash() - totalAmount);
            state.setNotes20(state.getNotes20() - notes20);
            state.setNotes50(state.getNotes50() - notes50);
            state.setNotes100(state.getNotes100() - notes100);
            
            em.merge(state);
            em.getTransaction().commit();
            
            System.out.printf("\n=== CASH COLLECTED ===\n");
            System.out.printf("$20 notes collected: %d\n", notes20);
            System.out.printf("$50 notes collected: %d\n", notes50);
            System.out.printf("$100 notes collected: %d\n", notes100);
            System.out.printf("Total collected: $%.2f\n", totalAmount);
            System.out.printf("Remaining ATM cash: $%.2f\n==================\n", state.getCash());
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Collection failed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    public void updateFirmware(String version) {
        EntityManager em = JpaManager.getEntityManager();
        em.getTransaction().begin();

        try {
            ATMState state = getATMState(em);
            state.setFirmwareVersion(version);
            em.merge(state);
            em.getTransaction().commit();
            System.out.printf("Firmware updated to: %s\n", version);
        } catch (Exception e) {
            em.getTransaction().rollback();
            System.out.println("Update failed: " + e.getMessage());
        } finally {
            em.close();
        }
    }

    /* ================= HELPER METHODS ================= */

    private ATMState getATMState(EntityManager em) {
        TypedQuery<ATMState> q = em.createQuery("SELECT a FROM ATMState a", ATMState.class);
        return q.getSingleResult();
    }
}
