package com.fintech.recon_system.controller;

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.repository.AuditLogRepository;
import com.fintech.recon_system.repository.TransactionRepository;
import com.fintech.recon_system.service.ReconciliationService; 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // 🚀 SLF4J Import

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
 * Web Controller for the Financial Dashboard.
 * Enhanced with Advanced Multi-criteria Search and Excel Export.
 */
@Slf4j // 🚀 Lombok annotation for 'log' variable
@Controller
@RequiredArgsConstructor
public class TransactionWebController {

    private final TransactionRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final ReconciliationService reconService;

    /**
     * Renders the Paginated Dashboard with Advanced Search capability.
     */
    @GetMapping("/")
    public String dashboard(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String refId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        // Step 1: Sanitize input (Treat empty string as null)
        String cleanRefId = (refId != null && !refId.trim().isEmpty()) ? refId.trim() : null;

        // Step 2: Initialize Pagination (Newest First)
        Pageable pageable = PageRequest.of(page, 20, Sort.by("createdAt").descending());
        Page<Transaction> transactionPage;

        // Step 3: Branching Logic (Search > Filter > All)
        if (cleanRefId != null || minAmount != null || maxAmount != null) {
            log.info("🔎 Action: Advanced Search | refId: {}, min: {}, max: {}", cleanRefId, minAmount, maxAmount);
            transactionPage = repository.advancedSearch(cleanRefId, minAmount, maxAmount, filter, pageable);
        } else if ("risk".equals(filter)) {
            transactionPage = repository.findByAmlAlert("CONFIRMED_FRAUD_PATTERN", pageable);
        } else if ("pending".equals(filter)) {
            transactionPage = repository.findByStatus("PENDING_REVIEW", pageable); // 🚀 Added missing pending filter
        } else {
            transactionPage = repository.findAll(pageable);
        }

        // Step 4: Populate Model for UI
        model.addAttribute("transactions", transactionPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", transactionPage.getTotalPages());
        model.addAttribute("totalItems", transactionPage.getTotalElements());
        model.addAttribute("currentFilter", filter);
        
        // Pass params back to UI to keep input values after refresh
        model.addAttribute("refId", refId); 
        model.addAttribute("minAmount", minAmount);
        model.addAttribute("maxAmount", maxAmount);

        // Step 5: Populate Global Analytics 
        BigDecimal totalVolume = repository.sumTotalAmount();
        model.addAttribute("totalVolume", totalVolume != null ? totalVolume : BigDecimal.ZERO);
        model.addAttribute("fraudCount", repository.countByAmlAlert("CONFIRMED_FRAUD_PATTERN"));
        model.addAttribute("pendingCount", repository.countByStatus("PENDING_REVIEW"));
        model.addAttribute("auditLogs", auditLogRepository.findTop10ByOrderByTimestampDesc());

        // Step 6:(Chart Data)
        // (Pie Chart)
        List<Object[]> typeStats = repository.countTransactionsByType();
        model.addAttribute("typeLabels", typeStats.stream().map(s -> s[0]).toList());
        model.addAttribute("typeData", typeStats.stream().map(s -> s[1]).toList());

        // (Bar Chart)
        List<Object[]> riskStats = repository.countByAmlAlert();
        model.addAttribute("riskLabels", riskStats.stream().map(s -> s[0]).toList());
        model.addAttribute("riskData", riskStats.stream().map(s -> s[1]).toList());

        return "index";
    }

    /**
     * Exports transaction data to Excel based on active filter.
     */
    @GetMapping("/export")
    public ResponseEntity<InputStreamResource> exportReport(@RequestParam(required = false) String filter) throws Exception {
        log.info("📊 Action: Exporting report for filter: {}", filter);
        List<Transaction> data;
        
        if ("risk".equals(filter)) {
            data = repository.findByAmlAlert("CONFIRMED_FRAUD_PATTERN");
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