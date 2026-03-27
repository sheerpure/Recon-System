package com.fintech.recon_system.controller;

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.repository.AuditLogRepository;
import com.fintech.recon_system.repository.TransactionRepository;
import com.fintech.recon_system.service.ReconciliationService; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Professional Web Controller for the Financial Reconciliation Dashboard.
 * Handles paginated transaction displays, multi-criteria filtering, and secure report exports.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class TransactionWebController {

    private final TransactionRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final ReconciliationService reconService;

    /**
     * Renders the primary dashboard view with integrated search and real-time analytics.
     * Uses dynamic pagination to handle large-scale financial datasets efficiently.
     */
    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String refId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        // Step 1: Input Sanitization - Treat empty strings as null for SQL optimization
        String cleanRefId = (refId != null && !refId.trim().isEmpty()) ? refId.trim() : null;

        // Step 2: Pagination Configuration - Defaults to 'Newest First' for audit visibility
        Pageable pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending());
        Page<Transaction> transactionPage;

        // Step 3: Branching Search Logic (Prioritizing Advanced Search > Filters > Default View)
        if (cleanRefId != null || minAmount != null || maxAmount != null) {
            log.info("🔎 Action: Advanced Search Execution | refId: {}, min: {}, max: {}", cleanRefId, minAmount, maxAmount);
            transactionPage = repository.advancedSearch(cleanRefId, minAmount, maxAmount, filter, pageable);
        } else if ("risk".equals(filter)) {
            // Updated to fetch all transactions that are not classified as 'CLEAN'
            transactionPage = repository.findByAmlAlertNot("CLEAN", pageable);
        } else if ("pending".equals(filter)) {
            // Filters for transactions requiring manual auditor intervention
            transactionPage = repository.findByStatus("PENDING_REVIEW", pageable);
        } else {
            transactionPage = repository.findAll(pageable);
        }

        // Step 4: UI Content Mapping
        model.addAttribute("transactions", transactionPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", transactionPage.getTotalPages());
        model.addAttribute("totalItems", transactionPage.getTotalElements());
        model.addAttribute("currentFilter", filter);
        
        // Persist search parameters back to the view for sticky form values
        model.addAttribute("refId", refId); 
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);

        // Step 5: High-Level Analytics Aggregation
        BigDecimal totalVolume = repository.sumTotalAmount();
        model.addAttribute("totalVolume", totalVolume != null ? totalVolume : BigDecimal.ZERO);

        // SYNC FIX: Counting all non-clean transactions to match 'High Risk' indicators
        long totalRiskDetected = repository.countByAmlAlertNot("CLEAN");
        model.addAttribute("fraudCount", totalRiskDetected);

        // Statistical summary of items awaiting compliance approval
        model.addAttribute("pendingCount", repository.countByStatus("PENDING_REVIEW"));

        // Retrieve the most recent compliance audit logs for the activity feed
        model.addAttribute("auditLogs", auditLogRepository.findTop10ByOrderByTimestampDesc());

        // Step 6: Data Visualization Mapping (Chart.js Integration)
        // Group transactions by type for Portfolio Distribution (Pie Chart)
        List<Object[]> typeStats = repository.countTransactionsByType();
        model.addAttribute("typeLabels", typeStats.stream().map(s -> s[0]).toList());
        model.addAttribute("typeData", typeStats.stream().map(s -> s[1]).toList());

        // Group transactions by risk category for Risk Profiling (Bar Chart)
        List<Object[]> riskStats = repository.countByAmlAlert();
        model.addAttribute("riskLabels", riskStats.stream().map(s -> s[0]).toList());
        model.addAttribute("riskData", riskStats.stream().map(s -> s[1]).toList());

        return "index";
    }

    /**
     * Facilitates secure Excel report generation for auditors and regulators.
     * Supports filtered data exports based on the active UI view.
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportReport(@RequestParam(required = false) String filter) throws Exception {
        log.info("📊 Action: Initiating report generation for scope: {}", filter);
        List<Transaction> data;
        
        if ("risk".equals(filter)) {
            // Exports all suspicious/flagged entries for enhanced due diligence (EDD)
            data = repository.findByAmlAlertNot("CLEAN");
        } else if ("pending".equals(filter)) {
            data = repository.findByStatus("PENDING_REVIEW");
        } else {
            data = repository.findAll(); 
        }

        ByteArrayInputStream in = reconService.exportTransactionsToExcel(data);
        
        HttpHeaders headers = new HttpHeaders();
        String filename = "recon_report_" + (filter != null && !filter.isEmpty() ? filter : "all") + ".xlsx";
        headers.add("Content-Disposition", "attachment; filename=" + filename);

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }
}