package com.atm.db;

import com.atm.model.Account;
import com.atm.model.ATMState;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public class DataInitializer {

    public static void initializeTestData() {
        EntityManager em = JpaManager.getEntityManager();
        
        try {
            // Check if data already exists
            TypedQuery<Long> countQuery = em.createQuery("SELECT COUNT(a) FROM Account a", Long.class);
            Long count = countQuery.getSingleResult();
            
            if (count > 0) {
                System.out.println("Test data already exists, skipping initialization.");
                return;
            }
            
            em.getTransaction().begin();
            
            // Create test accounts
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
            
            // Create ATM State with plenty of supplies for demo
            ATMState atmState = new ATMState();
            // Distribution: 100×$100 + 150×$50 + 200×$20 = $10,000 + $7,500 + $4,000 = $21,500
            atmState.setCash(21500.0);  // Must match banknotes total!
            atmState.setPaper(0);       // Set to 0 for out-of-paper testing
            atmState.setInk(100);       // Increased for demo
            atmState.setNotes100(100);  // 100 x $100 notes = $10,000
            atmState.setNotes50(150);   // 150 x $50 notes = $7,500
            atmState.setNotes20(200);   // 200 x $20 notes = $4,000
            atmState.setFirmwareVersion("v1.0");
            
            em.persist(acc1);
            em.persist(acc2);
            em.persist(atmState);
            
            em.getTransaction().commit();
            System.out.println("\n=== Test data initialized successfully! ===");
            System.out.println("Customer 1 - Card: 1111, PIN: 1111 (Balance: $5000)");
            System.out.println("Customer 2 - Card: 2222, PIN: 2222 (Balance: $3000)");
            System.out.println("ATM State - Cash: $21,500, Paper: 100, Ink: 100, Firmware: v1.0");
            System.out.println("Banknotes - $100: 100 ($10,000), $50: 150 ($7,500), $20: 200 ($4,000)\n");
            
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            System.err.println("Error initializing test data: " + e.getMessage());
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}

