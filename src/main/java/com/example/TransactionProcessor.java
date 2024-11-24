package com.example;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

class TransactionProcessor {
    private final PriorityBlockingQueue<Transaction> mainQueue;
    private final PriorityBlockingQueue<Transaction> retryQueue;
    private final ExecutorService mainExecutor;
    private final ExecutorService retryExecutor;
    private final DatabaseManager dbManager;
    private final TransactionUI gui;
    private final Map<String, Long> threadProcessingTimes;
    private final LocalDateTime systemStartTime;
    private final int MAX_THREADS = 5;

    public TransactionProcessor(TransactionUI ui, List<Transaction> recoveredTransactions, String dbUrl, String username, String password) {

        this.mainQueue = new PriorityBlockingQueue<>();
        this.retryQueue = new PriorityBlockingQueue<>();
        this.mainExecutor = Executors.newFixedThreadPool(MAX_THREADS, new ThreadFactory() {
            private int threadCount = 1;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("MainProcessor-" + threadCount++);
                return thread;
            }
        });
        this.retryExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
            private int threadCount = 1;

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("RetryProcessor-" + threadCount++);
                return thread;
            }
        });
        this.gui = ui;
        this.dbManager = new DatabaseManager(dbUrl, username, password);
        this.threadProcessingTimes = new ConcurrentHashMap<>();
        this.systemStartTime = LocalDateTime.now();

        for (Transaction t : recoveredTransactions) {
            if (t.getStatus() != TransactionStatus.RETRY) {
                mainQueue.add(t);
            } else {
                retryQueue.add(t);
            }
            dbManager.updateTransactionStatus(t);
        }

        // Start processing threads
        startProcessing();
    }

    public void startProcessing() {
        // Start main queue processors
        for (int i = 0; i < MAX_THREADS; i++) {
            mainExecutor.submit(new QueueProcessor(mainQueue, false));
        }

        // Start retry queue processors
        for (int i = 0; i < 2; i++) {
            retryExecutor.submit(new QueueProcessor(retryQueue, true));
        }
    }

    private class QueueProcessor implements Runnable {
        private final PriorityBlockingQueue<Transaction> queue;
        private final boolean isRetry;

        public QueueProcessor(PriorityBlockingQueue<Transaction> queue, boolean isRetry) {
            this.queue = queue;
            this.isRetry = isRetry;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Take the highest priority transaction
                    Transaction transaction = queue.take();
                    if (isRetry) {
                        Thread.sleep(5000);
                    }
                    processTransaction(transaction, isRetry);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processTransaction(Transaction transaction, boolean isRetry) {
        String threadName = Thread.currentThread().getName();
        transaction.startProcessing(threadName);

        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            dbManager.updateTransactionStatus(transaction);
            gui.updateTransactionStatus(transaction);

            simulateProcessing(transaction);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.endProcessing();

            threadProcessingTimes.merge(threadName, transaction.getProcessingTime(), Long::sum);

            dbManager.updateTransactionStatus(transaction);
            gui.updateTransactionStatus(transaction);
            gui.updateProcessingTimes(getThreadProcessingTimes(), getTotalProcessingTime());

        } catch (Exception e) {
            handleTransactionFailure(transaction);
        }
    }

    public void addTransaction(double amount) {
        Transaction transaction = new Transaction(amount);
        dbManager.saveTransaction(transaction);
        mainQueue.add(transaction);
        gui.addTransactionToTable(transaction);
    }

    private void simulateProcessing(Transaction transaction) throws Exception {
        Random random = new Random();
        double errorProbability = transaction.getRetryCount() > 0 ? 0.6 : 0.9;
        if (random.nextDouble() < errorProbability) {
            throw new Exception("Processing failed");
        }
        Thread.sleep(random.nextInt(5000));
    }

    private void handleTransactionFailure(Transaction transaction) {
        if (transaction.isCanRetry()) {
            transaction.incrementRetryCount();
            transaction.setStatus(TransactionStatus.RETRY);
            retryQueue.add(transaction);
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
        }
        transaction.endProcessing();
        threadProcessingTimes.merge(transaction.getProcessingThread(), transaction.getProcessingTime(), Long::sum);
        dbManager.updateTransactionStatus(transaction);
        gui.updateTransactionStatus(transaction);
        gui.updateProcessingTimes(getThreadProcessingTimes(), getTotalProcessingTime());
    }

    public Map<String, Long> getThreadProcessingTimes() {
        return new ConcurrentHashMap<>(threadProcessingTimes);
    }

    public long getTotalProcessingTime() {
        return Duration.between(systemStartTime, LocalDateTime.now()).toMillis();
    }

    public void shutdown() {
        mainExecutor.shutdown();
        retryExecutor.shutdown();
        dbManager.close(); // Close the database connection pool
        try {
            if (!mainExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                mainExecutor.shutdownNow();
            }
            if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}