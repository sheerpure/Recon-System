package com.fintech.recon_system.repository;

import com.fintech.recon_system.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Enterprise-grade Repository for Financial Transactions.
 * Replaces fixed 'Top 50' methods with dynamic Pagination (Pageable) 
 * to handle million-row datasets like Kaggle PaySim safely.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {


    @Query("SELECT t.transactionType, COUNT(t) FROM Transaction t GROUP BY t.transactionType")
    List<Object[]> countTransactionsByType();

    @Query("SELECT t.amlAlert, COUNT(t) FROM Transaction t GROUP BY t.amlAlert")
    List<Object[]> countByAmlAlert();

    // 1. Basic Count Methods
    long countByAmlAlert(String amlAlert);
    
    long countByStatus(String status);

    // 2. Aggregate Sum Method (Using JPQL)
    @Query("SELECT SUM(t.amount) FROM Transaction t")
    BigDecimal sumTotalAmount();

    // 3. List Retrieval for Excel Export
    List<Transaction> findByAmlAlert(String amlAlert);
    
    List<Transaction> findByStatus(String status);

    // 4. Pagination Support
    Page<Transaction> findByAmlAlert(String amlAlert, Pageable pageable);
    
    Page<Transaction> findByStatus(String status, Pageable pageable);

    /**
     * 🚀 Advanced Dynamic Search
     * Handles multi-criteria filtering for the dashboard.
     */
        @Query("SELECT t FROM Transaction t WHERE " +
        "(:refId IS NULL OR LOWER(t.referenceId) LIKE LOWER(CONCAT('%', :refId, '%'))) AND " +
        "(:min IS NULL OR t.amount >= :min) AND " +
        "(:max IS NULL OR t.amount <= :max) AND " +
        "( :filter IS NULL OR :filter = '' OR " + 
        "  (:filter = 'risk' AND t.amlAlert = 'CONFIRMED_FRAUD_PATTERN') OR " +
        "  (:filter = 'pending' AND t.status = 'PENDING_REVIEW') )")
        Page<Transaction> advancedSearch(
        @Param("refId") String refId, 
        @Param("min") BigDecimal min, 
        @Param("max") BigDecimal max, 
        @Param("filter") String filter, 
        Pageable pageable);
}