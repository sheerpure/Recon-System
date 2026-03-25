package com.fintech.recon_system.util;

import org.springframework.data.jpa.domain.Specification;
import com.fintech.recon_system.model.Transaction;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for building dynamic JPA Specifications for Transaction entities.
 * This allows multi-criteria filtering (Search Engine) without writing complex SQL.
 */
public class TransactionSpecification {

    /**
     * Builds a dynamic specification based on provided filter criteria.
     * * @param referenceId Partial match for the destination/reference ID
     * @param type Exact match for transaction type (e.g., TRANSFER, CASH_OUT)
     * @param minAmount Minimum transaction amount threshold
     * @param maxAmount Maximum transaction amount threshold
     * @param status Exact match for internal audit status
     * @return A JPA Specification to be used with TransactionRepository
     */
    public static Specification<Transaction> filterTransactions(
            String referenceId, String type, BigDecimal minAmount, BigDecimal maxAmount, String status) {
        
        return (root, query, cb) -> {
            // List to hold all active filtering conditions
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filter by Reference ID (Partial/Like match)
            if (referenceId != null && !referenceId.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("referenceId")), 
                               "%" + referenceId.toLowerCase() + "%"));
            }

            // 2. Filter by Transaction Type (Exact match)
            if (type != null && !type.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("transactionType"), type));
            }

            // 3. Filter by Minimum Amount (Greater than or equal)
            if (minAmount != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), minAmount));
            }

            // 4. Filter by Maximum Amount (Less than or equal)
            if (maxAmount != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), maxAmount));
            }

            // 5. Filter by Audit Status (Exact match)
            if (status != null && !status.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Combine all predicates with an AND operator
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}