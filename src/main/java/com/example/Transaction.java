package com.example;

import lombok.Data;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

enum TransactionStatus {
    NEW, PROCESSING, COMPLETED, FAILED, RETRY
}

@Data
public class Transaction implements Comparable<Transaction> {
    private final UUID id;
    private final double amount;
    private LocalDateTime createdAt;
    private TransactionStatus status;
    private int retryCount;
    private long processingTime;
    private LocalDateTime processingStartTime;
    private String processingThread;
    private static final int MAX_RETRY_COUNT = 3;

    // Original constructor for new transactions
    public Transaction(double amount) {
        this.id = UUID.randomUUID();
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
        this.status = TransactionStatus.NEW;
        this.retryCount = 0;
        this.processingTime = 0;
    }

    public Transaction(UUID transactionId, double amount) {
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

    public boolean isCanRetry() {
        return retryCount < MAX_RETRY_COUNT;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    // Compare transactions for priority queue ordering
    @Override
    public int compareTo(Transaction other) {
        // Higher amounts have higher priority (negative for descending order)
        int amountComparison = Double.compare(other.amount, this.amount);
        if (amountComparison != 0) {
            return amountComparison;
        }
        // If amounts are equal, older transactions have higher priority
        return this.createdAt.compareTo(other.createdAt);
    }

    public long getProcessingTime() {
        // If currently processing, include current processing time
        if (processingStartTime != null) {
            return processingTime + Duration.between(processingStartTime, LocalDateTime.now()).toMillis();
        }
        return processingTime;
    }
}