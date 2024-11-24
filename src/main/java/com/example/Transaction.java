package com.example;

import lombok.Data;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

enum TransactionStatus {
    NEW, PROCESSING, COMPLETED, FAILED, RETRY
}

@Data
public class Transaction implements Comparable<Transaction> {
    private final UUID id;
    private final BigDecimal amount;
    private LocalDateTime createdAt;
    private TransactionStatus status;
    private int retryCount;
    private long processingTime;
    private LocalDateTime processingStartTime;
    private String processingThread;
    private static final int MAX_RETRY_COUNT = 3;

    public Transaction(BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.status = TransactionStatus.NEW;
        this.retryCount = 0;
        this.processingTime = 0;
    }

    public Transaction(UUID transactionId, BigDecimal amount) {
        this.id = transactionId;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.status = TransactionStatus.NEW;
        this.retryCount = 0;
        this.processingTime = 0;
    }

    // Start processing the transaction
    public void startProcessing(String threadName) {
        this.processingStartTime = LocalDateTime.now();
        this.processingThread = threadName;
    }

    // End processing and calculate total processing time
    public void endProcessing() {
        if (processingStartTime != null) {
            this.processingTime += Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
            this.processingStartTime = null;
        }
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRY_COUNT;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    @Override
    public int compareTo(@NotNull Transaction other) {
        return Comparator.comparing(Transaction::getAmount).thenComparing(Transaction::getCreatedAt)
                .compare(this, other);
    }

    public long getProcessingTime() {
        // If currently processing, include current processing time
        if (processingStartTime != null) {
            return processingTime + Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
        }
        return processingTime;
    }
}