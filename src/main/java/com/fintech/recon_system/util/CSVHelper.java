package com.fintech.recon_system.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import lombok.extern.slf4j.Slf4j;

import com.fintech.recon_system.model.Transaction;
import com.fintech.recon_system.repository.TransactionRepository;

@Slf4j
public class CSVHelper {
    public static String TYPE = "text/csv";

    /**
     * Parses CSV file and saves transactions in batches to prevent OutOfMemoryError.
     * This method uses streaming to handle large datasets like PaySim (Kaggle).
     * * @param is Input stream of the CSV file
     * @param repository TransactionRepository for database operations
     */
    public static void parseAndSave(InputStream is, TransactionRepository repository) {
        // Use try-with-resources to ensure reader and parser are closed properly
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            List<Transaction> batchList = new ArrayList<>();
            int count = 0;
            int batchSize = 1000; // Optimal batch size for JPA saveAll

            // Iterating over csvParser directly (Streaming mode)
            // Do NOT use csvParser.getRecords() for large files
            for (CSVRecord csvRecord : csvParser) {
                Transaction transaction = new Transaction();
                
                // Map PaySim CSV fields to Transaction entity
                transaction.setTransactionType(csvRecord.get("type")); 
                transaction.setAmount(new BigDecimal(csvRecord.get("amount")));
                transaction.setReferenceId(csvRecord.get("nameDest")); // Using destination ID as reference
                
                // Convert PaySim 'isFraud' (0/1) to internal AML alert status
                String isFraud = csvRecord.get("isFraud");
                transaction.setAmlAlert("1".equals(isFraud) ? "FRAUD_DETECTED" : "CLEAN");

                // Initialize internal audit status
                transaction.setStatus("PENDING_REVIEW");

                batchList.add(transaction);
                count++;

                // Execute batch save every 1000 records to free up memory
                if (count % batchSize == 0) {
                    repository.saveAll(batchList);
                    batchList.clear(); // Clear the list to release heap space
                    log.info("Ingestion progress: {} records processed...", count);
                }
            }

            // Save remaining records
            if (!batchList.isEmpty()) {
                repository.saveAll(batchList);
                log.info("Ingestion completed. Total records: {}", count);
            }

        } catch (IOException e) {
            log.error("CSV Ingestion Error: {}", e.getMessage());
            throw new RuntimeException("Failed to parse CSV file: " + e.getMessage());
        }
    }
}