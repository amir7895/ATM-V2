package com.atm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "atm_state")
public class ATMState extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cash")
    private double cash;

    @Column(name = "paper")
    private int paper;

    @Column(name = "ink")
    private int ink;

    @Column(name = "firmware_version")
    private String firmwareVersion;

    @Column(name = "notes_20")
    private int notes20;

    @Column(name = "notes_50")
    private int notes50;

    @Column(name = "notes_100")
    private int notes100;

    public ATMState() {}

    // ===== GETTERS =====

    public Long getId() {
        return id;
    }

    public double getCash() {
        return cash;
    }

    public int getPaper() {
        return paper;
    }

    public int getInk() {
        return ink;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public int getNotes20() {
        return notes20;
    }

    public int getNotes50() {
        return notes50;
    }

    public int getNotes100() {
        return notes100;
    }

    // ===== SETTERS =====

    public void setCash(double cash) {
        this.cash = cash;
    }

    public void setPaper(int paper) {
        this.paper = paper;
    }

    public void setInk(int ink) {
        this.ink = ink;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public void setNotes20(int notes20) {
        this.notes20 = notes20;
    }

    public void setNotes50(int notes50) {
        this.notes50 = notes50;
    }

    public void setNotes100(int notes100) {
        this.notes100 = notes100;
    }

    // ===== OPERATIONS =====

    public void addCash(double amount) {
        this.cash += amount;
    }

    public void collectCash(double amount) {
        this.cash -= amount;
    }

    public void refillPaper(int amount) {
        this.paper += amount;
    }

    public void refillInk(int amount) {
        this.ink += amount;
    }

    public void addBanknotes(int notes20, int notes50, int notes100) {
        this.notes20 += notes20;
        this.notes50 += notes50;
        this.notes100 += notes100;
        // Update total cash
        this.cash += (notes20 * 20) + (notes50 * 50) + (notes100 * 100);
    }

    public String getBanknoteStatus() {
        return String.format("$20 notes: %d | $50 notes: %d | $100 notes: %d", notes20, notes50, notes100);
    }
}
