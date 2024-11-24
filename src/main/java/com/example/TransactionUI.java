package com.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionUI extends JFrame {
    private final JTable transactionTable;
    private final DefaultTableModel tableModel;
    private final TransactionProcessor processor;
    private final Random random = new Random();
    private final JPanel timingPanel;
    private final Map<String, JLabel> threadTimeLabels;
    private final JLabel recoveredTransactionsLabel;
    private final DatabaseManager databaseManager;

    private static final String[] COLUMN_NAMES = {
            "ID", "Amount", "Created At", "Status", "Retry Count", "Processing Time"
    };

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TransactionUI(String dbUrl, String username, String password) {
        setTitle("Transaction Processing System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLayout(new BorderLayout());

        // Database Manager Initialization
        this.databaseManager = new DatabaseManager(dbUrl, username, password);

        // UI Components Initialization
        this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0);
        this.transactionTable = new JTable(tableModel);
        configureTransactionTable();

        // Initialize the threadTimeLabels before the createTimingPanel call
        this.threadTimeLabels = new ConcurrentHashMap<>();
        this.timingPanel = createTimingPanel();

        this.recoveredTransactionsLabel = new JLabel("Recovered: 0");

        JPanel controlPanel = createControlPanel();
        JPanel statsPanel = createStatsPanel();

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(new JScrollPane(transactionTable), BorderLayout.CENTER);
        mainPanel.add(timingPanel, BorderLayout.EAST);
        mainPanel.add(statsPanel, BorderLayout.NORTH);
        mainPanel.add(controlPanel, BorderLayout.SOUTH);

        add(mainPanel);

        // Load recovered transactions and start processing
        var recoveredTransactions = databaseManager.recoverTransactions();
        recoveredTransactions.forEach(this::addTransactionToTable);
        this.processor = new TransactionProcessor(this, recoveredTransactions, dbUrl, username, password);

        // Start periodic UI updates
        startStatisticsUpdater(statsPanel);
    }

    private void configureTransactionTable() {
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            transactionTable.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
    }

    private JPanel createTimingPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Processing Times"));
        JLabel totalTimeLabel = new JLabel("Total Time: 0 ms");
        threadTimeLabels.put("Total", totalTimeLabel);
        panel.add(totalTimeLabel);
        return panel;
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JTextField countField = new JTextField(10);
        JButton generateButton = new JButton("Generate Transactions");

        generateButton.addActionListener(e -> handleGenerateButtonClick(countField));
        controlPanel.add(new JLabel("Number of Transactions:"));
        controlPanel.add(countField);
        controlPanel.add(generateButton);
        return controlPanel;
    }

    private void handleGenerateButtonClick(JTextField countField) {
        try {
            int count = Integer.parseInt(countField.getText());
            if (count <= 0) {
                showMessage("Please enter a positive number", "Invalid Input", JOptionPane.WARNING_MESSAGE);
                return;
            }
            generateTransactions(count);
            countField.setText("");
        } catch (NumberFormatException ex) {
            showMessage("Please enter a valid number", "Invalid Input", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel createStatsPanel() {
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Transaction Statistics"));

        JLabel totalTransactionsLabel = new JLabel("Total: 0");
        JLabel completedTransactionsLabel = new JLabel("Completed: 0");
        JLabel failedTransactionsLabel = new JLabel("Failed: 0");

        statsPanel.add(totalTransactionsLabel);
        statsPanel.add(completedTransactionsLabel);
        statsPanel.add(failedTransactionsLabel);
        statsPanel.add(recoveredTransactionsLabel);

        return statsPanel;
    }

    private void startStatisticsUpdater(JPanel statsPanel) {
        Timer timer = new Timer(1000, e -> updateStatistics(statsPanel));
        timer.start();
    }

    private void updateStatistics(JPanel statsPanel) {
        int total = tableModel.getRowCount();
        int completed = 0;
        int failed = 0;

        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String status = tableModel.getValueAt(i, 3).toString();
            if ("COMPLETED".equals(status)) completed++;
            if ("FAILED".equals(status)) failed++;
        }

        ((JLabel) statsPanel.getComponent(0)).setText("Total: " + total);
        ((JLabel) statsPanel.getComponent(1)).setText("Completed: " + completed);
        ((JLabel) statsPanel.getComponent(2)).setText("Failed: " + failed);
    }

    private void generateTransactions(int count) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < count; i++) {
                    double amount = generateRandomAmount();
                    processor.addTransaction(amount);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                return null;
            }
        };
        worker.execute();
    }

    private double generateRandomAmount() {
        return 100 + random.nextDouble() * 9900;
    }

    public void addTransactionToTable(Transaction transaction) {
        SwingUtilities.invokeLater(() -> {
            String transactionId = transaction.getId().toString().substring(0, 8);
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(transactionId)) {
                    updateTransactionStatus(transaction);
                    return;
                }
            }

            tableModel.addRow(new Object[]{
                    transactionId,
                    String.format("%.2f", transaction.getAmount()),
                    transaction.getCreatedAt().format(DATE_FORMATTER),
                    transaction.getStatus(),
                    transaction.getRetryCount(),
                    transaction.getProcessingTime() + " ms"
            });
        });
    }

    public void updateTransactionStatus(Transaction transaction) {
        SwingUtilities.invokeLater(() -> {
            String transactionId = transaction.getId().toString().substring(0, 8);
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(transactionId)) {
                    tableModel.setValueAt(transaction.getStatus(), i, 3);
                    tableModel.setValueAt(transaction.getRetryCount(), i, 4);
                    tableModel.setValueAt(transaction.getProcessingTime() + " ms", i, 5);
                    return;
                }
            }
        });
    }

    public void updateProcessingTimes(Map<String, Long> threadTimes, long totalTime) {
        SwingUtilities.invokeLater(() -> {
            threadTimes.forEach((threadName, time) -> {
                threadTimeLabels.computeIfAbsent(threadName, key -> {
                    JLabel label = new JLabel(String.format("%s: %d ms", threadName, time));
                    timingPanel.add(label);
                    timingPanel.revalidate();
                    return label;
                }).setText(String.format("%s: %d ms", threadName, time));
            });

            threadTimeLabels.get("Total").setText(String.format("Total Time: %d ms", totalTime));
        });
    }

    private void showMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }
}