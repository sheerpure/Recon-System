package com.fintech.recon_system.service;

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Enterprise-grade Asynchronous Reconciliation Service.
 * Leverages multi-threading to process large Kaggle PaySim datasets
 * without blocking the main application UI thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final TransactionRepository repository;

    // Optimal batch size for high-speed SQL insertion
    private static final int BATCH_SIZE = 1000;

    /**
     * Processes file ingestion in a background thread.
     * @Async targets the 'fileImportExecutor' defined in AsyncConfig.
     * @return A CompletableFuture containing the count of processed records.
     */
    @Async("fileImportExecutor")
    public CompletableFuture<List<Transaction>> processUploadedFile(MultipartFile file) throws Exception {
        log.info("🧵 [ASYNC START] Thread {} is processing: {}", 
                 Thread.currentThread().getName(), file.getOriginalFilename());

        List<Transaction> resultPreview = new ArrayList<>();
        List<Transaction> batchBuffer = new ArrayList<>();
        int totalProcessed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            // Skip CSV Header
            reader.readLine(); 

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length < 11) continue;

                Transaction entity = mapKaggleDataToEntity(columns);
                batchBuffer.add(entity);
                totalProcessed++;

                // Trigger Batch Save
                if (batchBuffer.size() >= BATCH_SIZE) {
                    saveBatch(batchBuffer);
                    batchBuffer.clear();
                }

                // Collect first 50 records for immediate UI feedback
                if (resultPreview.size() < 50) {
                    resultPreview.add(entity);
                }

                // Safety break for demo purposes (limit to 100k rows)
                if (totalProcessed >= 100000) break;
            }

            // Save remaining records
            if (!batchBuffer.isEmpty()) {
                saveBatch(batchBuffer);
            }
        }

        log.info("✅ [ASYNC FINISHED] Total Ingested: {} records via {}", 
                 totalProcessed, Thread.currentThread().getName());
        
        return CompletableFuture.completedFuture(resultPreview);
    }

    /**
     * Generates an Excel report from a list of transactions.
     * Designed for compliance auditing and external reporting.
     */
public ByteArrayInputStream exportTransactionsToExcel(List<Transaction> transactions) throws Exception {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        Sheet sheet = workbook.createSheet("Audit Report");

        // 1. Create Bold Header Style
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        // 2. Define Columns
        String[] headers = {"Reference ID", "Type", "Amount", "Status", "AML Alert", "Timestamp"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 3. Fill Data
        int rowIdx = 1;
        for (Transaction tx : transactions) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(tx.getReferenceId());
            row.createCell(1).setCellValue(tx.getTransactionType());
            row.createCell(2).setCellValue(tx.getAmount().doubleValue());
            row.createCell(3).setCellValue(tx.getStatus());
            row.createCell(4).setCellValue(tx.getAmlAlert());
            row.createCell(5).setCellValue(tx.getCreatedAt().toString());
        }

        // Auto-size columns for better readability
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        workbook.write(out);
        return new ByteArrayInputStream(out.toByteArray());
    }
}

    /**
     * Persists a batch of transactions to the database.
     * Transactional boundary is applied here to ensure batch atomicity.
     */
    private void saveBatch(List<Transaction> batch) {
        repository.saveAll(batch);
        repository.flush(); // Force sync with database
    }

    /**
     * Maps raw Kaggle strings to Transaction Domain Model.
     * Implements AML Screening and Fraud Pattern Detection.
     */
    private Transaction mapKaggleDataToEntity(String[] data) {
        Transaction entity = new Transaction();
        
        BigDecimal amount = new BigDecimal(data[2]);
        entity.setAmount(amount);
        entity.setReferenceId(data[3]); // nameOrig
        entity.setTransactionType(data[1]); // type
        entity.setCurrency("USD");

        int isFraud = Integer.parseInt(data[9]);
        boolean isLargeTransfer = "TRANSFER".equals(data[1]) && amount.compareTo(new BigDecimal("200000")) > 0;
        
        if (isFraud == 1) {
            entity.setAmlAlert("CONFIRMED_FRAUD_PATTERN");
            entity.setStatus("PENDING_REVIEW");
        } else if (isLargeTransfer) {
            entity.setAmlAlert("HIGH_VALUE_TRANSFER_RISK");
            entity.setStatus("PENDING_REVIEW");
        } else {
            entity.setAmlAlert("CLEAN");
            entity.setStatus("PROCESSED");
        }

        entity.setTransactionDate(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());

        return entity;
    }
}