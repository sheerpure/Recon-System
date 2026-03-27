package com.fintech.recon_system.controller;

import java.util.List;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream; 
import java.time.LocalDateTime;

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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller; 

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.model.AuditLog;
import com.fintech.recon_system.repository.TransactionRepository;
import com.fintech.recon_system.repository.AuditLogRepository;
import com.fintech.recon_system.service.ReconciliationService;
import com.fintech.recon_system.util.CSVHelper;
import com.fintech.recon_system.util.TransactionSpecification; 

import lombok.extern.slf4j.Slf4j;
import lombok.Data;

/**
 * TransactionController: Handles all financial data operations.
 * Supports file ingestion, individual/batch auditing, and automated reporting.
 */
@Slf4j
@Controller 
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
     * [Search] Returns paginated transaction data.
     */
    @GetMapping
    @ResponseBody 
    public Page<Transaction> getFilteredTransactions(
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Specification<Transaction> spec = TransactionSpecification.filterTransactions(
                referenceId, type, minAmount, maxAmount, status);
                
        return repository.findAll(spec, pageable);
    }

    /**
     * [Upload] Ingests CSV datasets and resets current state.
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<?> uploadKaggleData(@RequestParam("file") MultipartFile file) {
        try {
            repository.deleteAll(); 
            auditLogRepository.deleteAll();
            CSVHelper.parseAndSave(file.getInputStream(), repository);
            return ResponseEntity.ok("Dataset processed successfully.");
        } catch (Exception e) {
            log.error("❌ Ingestion Failure: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
    }

    /**
     * 🛡️ [Single Update] Updates status for a specific record.
     * Essential for the 'check' and 'cross' buttons in the dashboard.
     */
    @Transactional
    @PatchMapping("/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateSingleStatus(
            @PathVariable Long id, 
            @RequestParam("status") String status) {
        try {
            Transaction tx = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Transaction not found"));

            String oldStatus = tx.getStatus();
            tx.setStatus(status);
            if ("PROCESSED".equals(status)) {
                tx.setAmlAlert(null); 
            }

            AuditLog logEntry = new AuditLog();
            logEntry.setAction("SINGLE_UPDATE");
            logEntry.setTargetId(tx.getReferenceId());
            logEntry.setOperator("SYSTEM_ADMIN");
            logEntry.setDetails("Audit action: " + oldStatus + " -> " + status);
            auditLogRepository.save(logEntry);

            repository.save(tx);
            return ResponseEntity.ok("Transaction updated.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Update failed.");
        }
    }

    /**
     * 🛡️ [Batch Update] Atomic status update for selected records.
     */
    @Transactional
    @PatchMapping("/batch-status")
    @ResponseBody
    public ResponseEntity<?> updateBatchStatus(@RequestBody BatchStatusRequest request) {
        try {
            List<Transaction> transactions = repository.findAllById(request.getIds());
            for (Transaction tx : transactions) {
                String oldStatus = tx.getStatus();
                tx.setStatus(request.getNewStatus());
                if ("PROCESSED".equals(request.getNewStatus())) {
                    tx.setAmlAlert(null);
                }

                AuditLog logEntry = new AuditLog();
                logEntry.setAction("BATCH_UPDATE");
                logEntry.setTargetId(tx.getReferenceId());
                logEntry.setOperator("SYSTEM_ADMIN");
                logEntry.setDetails("Batch audit: " + oldStatus + " -> " + request.getNewStatus());
                auditLogRepository.save(logEntry);
            }
            repository.saveAll(transactions);
            return ResponseEntity.ok("Batch update completed.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Batch update failed.");
        }
    }

    /**
     * [Export] Generates Excel reports.
     */
    @GetMapping("/export")
    @ResponseBody
    public ResponseEntity<InputStreamResource> exportFilteredReport(
            @RequestParam(required = false) String referenceId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String status) {
        
        try {
            Specification<Transaction> spec = TransactionSpecification.filterTransactions(
                    referenceId, type, minAmount, maxAmount, status);
            List<Transaction> filteredData = repository.findAll(spec);
            ByteArrayInputStream in = reconService.exportTransactionsToExcel(filteredData);
            String filename = "Recon_Report_" + System.currentTimeMillis() + ".xlsx";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(new InputStreamResource(in));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @Data
    public static class BatchStatusRequest {
        private List<Long> ids;
        private String newStatus;
    }
}