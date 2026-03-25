package com.fintech.recon_system.dto;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;

/**
 * Data Transfer Object for mapping raw CSV rows from bank statements.
 * Separates external data format from internal domain logic.
 */
@Data
public class TransactionCsvDto {

    /** Maps to 'Date' column in CSV. Expected format: yyyy-MM-dd */
    @CsvBindByName(column = "Date")
    private String transactionDate;

    /** Maps to 'Type' column (e.g., DEPOSIT, WITHDRAWAL) */
    @CsvBindByName(column = "Type")
    private String transactionType;

    /** Maps to 'Amount' column. Handled as String first to allow for cleansing */
    @CsvBindByName(column = "Amount")
    private String amount;

    /** Maps to 'Reference' column, used for unique identification */
    @CsvBindByName(column = "Reference")
    private String referenceId;

    /** Maps to 'Currency' column, defaults to TWD if empty */
    @CsvBindByName(column = "Currency")
    private String currency;
}