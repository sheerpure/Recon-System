package com.fintech.recon_system.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction Domain Entity.
 * Represents a single financial record within the Reconciliation System.
 * Enhanced to support Kaggle PaySim data structures.
 */
@Entity
@Table(name = "transactions")
@Data
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Maps to 'nameOrig' or 'TradeID'
    private String referenceId;

    // Maps to 'type' in Kaggle dataset (e.g., PAYMENT, TRANSFER)
    private String transactionType;

    private BigDecimal amount;

    // Default: TWD or USD based on source
    private String currency;

    // Status: PENDING_REVIEW, PROCESSED, REJECTED
    private String status;

    // AML Flag: CLEAN, LARGE_TX_ALERT, CONFIRMED_FRAUD_PATTERN
    private String amlAlert;

    private LocalDateTime transactionDate;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.transactionDate == null) {
            this.transactionDate = LocalDateTime.now();
        }
    }
}