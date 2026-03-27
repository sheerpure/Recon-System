package com.fintech.recon_system.service;

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.repository.TransactionRepository;
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

@Service
public class ReconciliationService {

    private final TransactionRepository repository;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReconciliationService.class);
    private static final int BATCH_SIZE = 1000;

    // Manual Constructor injection (Replaces @RequiredArgsConstructor)
    public ReconciliationService(TransactionRepository repository) {
        this.repository = repository;
    }

    @Async("fileImportExecutor")
    public CompletableFuture<List<Transaction>> processUploadedFile(MultipartFile file) throws Exception {
        log.info("🧵 [ASYNC START] Processing: {}", file.getOriginalFilename());

        List<Transaction> resultPreview = new ArrayList<>();
        List<Transaction> batchBuffer = new ArrayList<>();
        int totalProcessed = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            reader.readLine(); // Skip CSV Header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length < 11) continue;

                Transaction entity = mapKaggleDataToEntity(columns);
                batchBuffer.add(entity);
                totalProcessed++;

                if (batchBuffer.size() >= BATCH_SIZE) {
                    saveBatch(batchBuffer);
                    batchBuffer.clear();
                }

                if (resultPreview.size() < 50) {
                    resultPreview.add(entity);
                }

                if (totalProcessed >= 100000) break;
            }

            if (!batchBuffer.isEmpty()) {
                saveBatch(batchBuffer);
            }
        }

        log.info("✅ [ASYNC FINISHED] Total Ingested: {} records", totalProcessed);
        return CompletableFuture.completedFuture(resultPreview);
    }

    public ByteArrayInputStream exportTransactionsToExcel(List<Transaction> transactions) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Audit Report");

            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);

            String[] headers = {"Reference ID", "Type", "Amount", "Status", "AML Alert", "Timestamp"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Transaction tx : transactions) {
                Row row = sheet.createRow(rowIdx++);
                // Manually calling Getters from the Transaction model
                row.createCell(0).setCellValue(tx.getReferenceId());
                row.createCell(1).setCellValue(tx.getTransactionType());
                row.createCell(2).setCellValue(tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0);
                row.createCell(3).setCellValue(tx.getStatus());
                row.createCell(4).setCellValue(tx.getAmlAlert());
                row.createCell(5).setCellValue(tx.getCreatedAt() != null ? tx.getCreatedAt().toString() : "N/A");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void saveBatch(List<Transaction> batch) {
        repository.saveAll(batch);
        repository.flush();
    }

    private Transaction mapKaggleDataToEntity(String[] data) {
        Transaction entity = new Transaction();
        
        BigDecimal amount = new BigDecimal(data[2]);
        BigDecimal oldBalanceOrg = new BigDecimal(data[4]);
        String type = data[1];
        int isReportedFraud = Integer.parseInt(data[9]);

        // Using manual Setters
        entity.setAmount(amount);
        entity.setReferenceId(data[3]); 
        entity.setTransactionType(type);
        entity.setCurrency("USD");
        entity.setTransactionDate(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());

        boolean confirmedFraud = (isReportedFraud == 1);
        boolean isDrainingAccount = oldBalanceOrg.compareTo(BigDecimal.ZERO) > 0 && 
            amount.divide(oldBalanceOrg, 2, java.math.RoundingMode.HALF_UP).compareTo(new BigDecimal("0.95")) > 0;

        if (confirmedFraud) {
            entity.setAmlAlert("CONFIRMED_FRAUD_INCIDENT");
            entity.setStatus("REJECTED_AUTO");
        } else if (isDrainingAccount && ("CASH_OUT".equals(type) || "TRANSFER".equals(type))) {
            entity.setAmlAlert("SUSPICIOUS_ACCOUNT_DRAINING");
            entity.setStatus("PENDING_REVIEW");
        } else {
            entity.setAmlAlert("CLEAN");
            entity.setStatus("PROCESSED");
        }

        return entity;
    }
}