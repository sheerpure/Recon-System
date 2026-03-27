package com.fintech.recon_system.model;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a financial transaction.
 * Manually implemented accessors to ensure compatibility with Maven builds 
 * without relying on Lombok annotation processing during compilation.
 */
@Entity
@Table(name = "transactions")
@NoArgsConstructor  // Required by Hibernate for entity instantiation
@AllArgsConstructor // Useful for creating instances in tests or data initialization
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Unique reference from the source system (e.g., nameOrig from PaySim)
    private String referenceId;

    // Type of transaction (e.g., CASH_IN, CASH_OUT, DEBIT, PAYMENT, TRANSFER)
    private String transactionType;

    // Numerical amount involved in the transaction
    private BigDecimal amount;

    // Currency code (e.g., TWD, USD)
    private String currency;

    // Processing status (e.g., PENDING_REVIEW, PROCESSED)
    private String status;

    // Risk classification for Anti-Money Laundering (e.g., CLEAN, FRAUD_DETECTED)
    private String amlAlert;

    // The actual date/time when the transaction occurred in the source system
    private LocalDateTime transactionDate;

    // Timestamp of when this record was created in the reconciliation database
    private LocalDateTime createdAt;

    /**
     * Entity lifecycle hook to automatically set timestamps before persisting.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.transactionDate == null) {
            this.transactionDate = LocalDateTime.now();
        }
    }

    // --- Standard Accessors (Getters and Setters) ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAmlAlert() { return amlAlert; }
    public void setAmlAlert(String amlAlert) { this.amlAlert = amlAlert; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}