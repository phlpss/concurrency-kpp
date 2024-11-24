package com.example;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;

class TransactionProcessor {
    private static final int MAX_THREADS = 5;
    private static final int RETRY_THREAD_COUNT = 2;
    private static final int RETRY_DELAY_MS = 5000;

    private final PriorityBlockingQueue<Transaction> mainQueue;
    private final PriorityBlockingQueue<Transaction> retryQueue;
    private final ExecutorService mainExecutor;
    private final ExecutorService retryExecutor;
    private final DatabaseManager dbManager;
    private final TransactionUI gui;
    private final Map<String, Long> threadProcessingTimes;
    private final LocalDateTime systemStartTime;

    public TransactionProcessor(TransactionUI ui, List<Transaction> recoveredTransactions) {
        this.mainQueue = new PriorityBlockingQueue<>();
        this.retryQueue = new PriorityBlockingQueue<>();
        this.mainExecutor = createExecutor("MainProcessor", MAX_THREADS);
        this.retryExecutor = createExecutor("RetryProcessor", RETRY_THREAD_COUNT);
        this.dbManager = new DatabaseManager();
        this.gui = ui;
        this.threadProcessingTimes = new ConcurrentHashMap<>();
        this.systemStartTime = LocalDateTime.now();

        initializeQueues(recoveredTransactions);
        startProcessing();
    }

    private ExecutorService createExecutor(String threadNamePrefix, int threadCount) {
        return Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private int threadCounter = 1;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, threadNamePrefix + "-" + threadCounter++);
            }
        });
    }

    private void initializeQueues(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() == TransactionStatus.RETRY) {
                retryQueue.add(transaction);
            } else {
                mainQueue.add(transaction);
            }
            dbManager.updateTransactionStatus(transaction);
        }
    }

    public void startProcessing() {
        for (int i = 0; i < MAX_THREADS; i++) {
            mainExecutor.submit(new QueueProcessor(mainQueue, false));
        }
        for (int i = 0; i < RETRY_THREAD_COUNT; i++) {
            retryExecutor.submit(new QueueProcessor(retryQueue, true));
        }
    }

    class QueueProcessor implements Runnable {
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
                    Transaction transaction = queue.take();
                    if (isRetry) {
                        Thread.sleep(RETRY_DELAY_MS);
                    }
                    processTransaction(transaction);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void processTransaction(Transaction transaction) {
        String threadName = Thread.currentThread().getName();
        transaction.startProcessing(threadName);

        try {
            transaction.setStatus(TransactionStatus.PROCESSING);
            updateTransactionInSystem(transaction);

            simulateProcessing(transaction);

            transaction.setStatus(TransactionStatus.COMPLETED);
            transaction.endProcessing();

            threadProcessingTimes.merge(threadName, transaction.getProcessingTime(), Long::sum);
            updateTransactionInSystem(transaction);
        } catch (Exception e) {
            handleTransactionFailure(transaction);
        }
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
        if (transaction.canRetry()) {
            transaction.incrementRetryCount();
            transaction.setStatus(TransactionStatus.RETRY);
            retryQueue.add(transaction);
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
        }
        finalizeTransaction(transaction);
    }

    private void finalizeTransaction(Transaction transaction) {
        transaction.endProcessing();
        threadProcessingTimes.merge(transaction.getProcessingThread(), transaction.getProcessingTime(), Long::sum);
        updateTransactionInSystem(transaction);
    }

    private void updateTransactionInSystem(Transaction transaction) {
        dbManager.updateTransactionStatus(transaction);
        gui.updateTransactionStatus(transaction);
        gui.updateProcessingTimes(getThreadProcessingTimes(), getTotalProcessingTime());
    }

    public void addTransaction(BigDecimal amount) {
        Transaction transaction = new Transaction(amount);
        dbManager.saveTransaction(transaction);
        mainQueue.add(transaction);
        gui.addTransactionToTable(transaction);
    }

    public Map<String, Long> getThreadProcessingTimes() {
        return new ConcurrentHashMap<>(threadProcessingTimes);
    }

    public long getTotalProcessingTime() {
        return Duration.between(systemStartTime, LocalDateTime.now()).toMillis();
    }
}
