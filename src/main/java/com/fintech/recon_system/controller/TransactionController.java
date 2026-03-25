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
 * Enhanced Controller for Financial Transactions.
 * Supports Dynamic Search, Batch Processing, Excel Export, and HTML Risk Reporting.
 */
@Slf4j
@Controller // Changed to @Controller to support HTML view rendering
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
     *[Advanced Search] Returns JSON data with pagination.
     */
    @GetMapping
    @ResponseBody // Must use @ResponseBody because the class is now @Controller
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
     * [CSV Upload] Processes large files in streaming mode.
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
     * [HTML Risk Report] Renders a professional web-based audit report.
     * Accessible via: GET /api/transactions/report/html
     */
    @GetMapping("/report/html")
    public String getHighRiskHtmlReport(Model model) {
        log.info("🎨 Generating HTML Risk Report...");
        
        // Define high-risk criteria (e.g., TRANSFER > 200,000)
        BigDecimal riskThreshold = new BigDecimal("200000");
        List<Transaction> highRisk = repository.findAll(
                TransactionSpecification.filterTransactions(null, "TRANSFER", riskThreshold, null, null));
        
        model.addAttribute("highRiskTransactions", highRisk);
        model.addAttribute("totalAudited", repository.count());
        model.addAttribute("totalRiskAmount", highRisk.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("reportId", "R" + System.currentTimeMillis() % 100000);
        model.addAttribute("generatedAt", LocalDateTime.now());

        return "risk_report"; // Refers to src/main/resources/templates/risk_report.html
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
                if ("PROCESSED".equals(request.getNewStatus())) tx.setAmlAlert("CLEAN_APPROVED");

                AuditLog logEntry = new AuditLog();
                logEntry.setAction("BATCH_UPDATE");
                logEntry.setTargetId(tx.getReferenceId());
                logEntry.setOperator("SYSTEM_ADMIN");
                logEntry.setDetails("Status change: " + oldStatus + " -> " + request.getNewStatus());
                auditLogRepository.save(logEntry);
            }
            repository.saveAll(transactions);
            return ResponseEntity.ok("Batch update completed.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Batch update failed.");
        }
    }

    /**
     * 📥 [Excel Export] Generates Excel file based on current filters.
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