package com.fintech.recon_system.controller;

import java.util.List;
import java.io.ByteArrayInputStream; 
import org.springframework.core.io.InputStreamResource; 
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;   
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;


import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.model.AuditLog;
import com.fintech.recon_system.repository.TransactionRepository;
import com.fintech.recon_system.repository.AuditLogRepository;
import com.fintech.recon_system.service.ReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import com.fintech.recon_system.util.CSVHelper;

import lombok.extern.slf4j.Slf4j;
import lombok.Data;

/**
 * REST Controller for financial transactions.
 * Supports Asynchronous Ingestion, Server-side Pagination, Audit Logging, and Batch Processing.
 */
@Slf4j
@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    private final TransactionRepository repository;
    private final ReconciliationService reconService;
    private final AuditLogRepository auditLogRepository;

    public TransactionController(TransactionRepository repository, 
                                 ReconciliationService reconService,
                                 AuditLogRepository auditLogRepository) {
        this.repository = repository;
        this.reconService = reconService;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Fetches transactions with pagination support.
     * @param page Zero-based page index.
     * @param size Number of records per page.
     * @return Paginated transaction data sorted by newest first.
     */
    @GetMapping
    public Page<Transaction> getRecentTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return repository.findAll(pageable);
    }

    /**
     * Handles asynchronous large-scale CSV ingestion.
     * Returns immediately while processing continues in the background.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadKaggleData(@RequestParam("file") MultipartFile file) {
        log.info("📥 [STREAMING START] Processing large CSV: {}", file.getOriginalFilename());
        
        try {
            // Clear old data before ingestion to achieve "overwrite" effect
            repository.deleteAll(); 
            auditLogRepository.deleteAll();

            // Call the streaming parser
            CSVHelper.parseAndSave(file.getInputStream(), repository);
            
            return ResponseEntity.status(HttpStatus.OK).body("Large dataset uploaded and processed successfully.");
        } catch (Exception e) {
            log.error("❌ Critical Ingestion Failure: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ingestion failed: " + e.getMessage());
        }
    }

    /**
     * Updates a single transaction status and records the action in Audit Logs.
     */
    @PatchMapping("/{id}/status")
    @Transactional 
    public ResponseEntity<Transaction> updateStatus(
            @PathVariable Long id, 
            @RequestParam String newStatus) {
    
        return repository.findById(id).map(tx -> {
            String oldStatus = tx.getStatus();
            tx.setStatus(newStatus);
            
            if ("PROCESSED".equals(newStatus)) {
                tx.setAmlAlert("CLEAN_APPROVED");
            }

            Transaction saved = repository.save(tx);

            // Create compliance audit trail
            AuditLog logEntry = new AuditLog();
            logEntry.setAction(newStatus.equals("PROCESSED") ? "APPROVE" : "REJECT");
            logEntry.setTargetId(tx.getReferenceId());
            logEntry.setOperator("ADMIN_SYSTEM"); 
            logEntry.setDetails("Manual Audit Action: " + oldStatus + " -> " + newStatus);
            auditLogRepository.save(logEntry);

            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Performs a batch status update on multiple transactions.
     * Ensures atomicity through @Transactional.
     */
    @Transactional
    @PatchMapping("/batch-status")
    public ResponseEntity<?> updateBatchStatus(@RequestBody BatchStatusRequest request) {
        log.info("🚀 [BATCH ACTION] Updating {} transactions to status: {}", 
                 request.getIds().size(), request.getNewStatus());
        
        try {
            List<Transaction> transactions = repository.findAllById(request.getIds());
            
            transactions.forEach(tx -> {
                String oldStatus = tx.getStatus();
                tx.setStatus(request.getNewStatus());
                
                // Log audit for each transaction in the batch
                AuditLog logEntry = new AuditLog();
                logEntry.setAction("BATCH_" + request.getNewStatus());
                logEntry.setTargetId(tx.getReferenceId());
                logEntry.setOperator("ADMIN_UI");
                logEntry.setDetails("Batch change from " + oldStatus + " to " + request.getNewStatus());
                auditLogRepository.save(logEntry);
            });

            repository.saveAll(transactions);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("❌ [BATCH ERROR] Failed to update transactions: ", e);
            return ResponseEntity.internalServerError().body("Batch update failed.");
        }
    }

    /**
     * Generates and exports an Excel report based on current filter.
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportReport(@RequestParam(required = false) String filter) {
        try {
            List<Transaction> data;
            
            if ("risk".equals(filter)) {
                data = repository.findByAmlAlert("CONFIRMED_FRAUD_PATTERN");
            } else if ("pending".equals(filter)) {
                data = repository.findByStatus("PENDING_REVIEW");
            } else {
                data = repository.findAll(); 
            }

            ByteArrayInputStream in = reconService.exportTransactionsToExcel(data);
            String filename = "Recon_Report_" + (filter != null && !filter.isEmpty() ? filter : "All") + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(in));
                    
        } catch (Exception e) {
            log.error("❌ [EXPORT ERROR] Failed to generate report: ", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * DTO for Batch Processing Requests.
     */
    @Data
    public static class BatchStatusRequest {
        private List<Long> ids;
        private String newStatus;
    }
}