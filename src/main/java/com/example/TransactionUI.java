package com.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionUI extends JFrame {
    private static final String[] COLUMN_NAMES = {"ID", "Amount", "Created At", "Status", "Retry Count", "Processing Time"};
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JTable transactionTable;
    private final DefaultTableModel tableModel;
    private final TransactionProcessor processor;
    private final Random random = new Random();
    private final JPanel timingPanel;
    private final Map<String, JLabel> threadTimeLabels = new ConcurrentHashMap<>();
    private final JLabel recoveredTransactionsLabel;
    private final JLabel totalTransactionsLabel = new JLabel("Total: 0");
    private final JLabel completedTransactionsLabel = new JLabel("Completed: 0");
    private final JLabel failedTransactionsLabel = new JLabel("Failed: 0");

    public TransactionUI() {
        setTitle("Transaction Processing System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 800);
        setLayout(new BorderLayout());

        // Initialize components
        this.tableModel = new DefaultTableModel(COLUMN_NAMES, 0);
        this.transactionTable = createTransactionTable();
        this.timingPanel = createTimingPanel();
        this.recoveredTransactionsLabel = new JLabel("Recovered: 0");

        // Add components to main layout
        JPanel mainPanel = createMainPanel();
        add(mainPanel);

        // Recover and display transactions
        DatabaseManager databaseManager = new DatabaseManager();
        var recoveredTransactions = databaseManager.recoverTransactions();
        recoveredTransactions.forEach(this::addTransactionToTable);
        processor = new TransactionProcessor(this, recoveredTransactions);

        // Start periodic statistics updates
        startStatisticsUpdater();
    }

    private JTable createTransactionTable() {
        JTable table = new JTable(tableModel);
        for (int i = 0; i < COLUMN_NAMES.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
        return table;
    }

    private JPanel createTimingPanel() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Processing Times"));
        JLabel totalTimeLabel = new JLabel("Total Time: 0 ms");
        threadTimeLabels.put("Total", totalTimeLabel);
        panel.add(totalTimeLabel);
        return panel;
    }

    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        mainPanel.add(new JScrollPane(transactionTable), BorderLayout.CENTER);
        mainPanel.add(createRightPanel(), BorderLayout.EAST);
        mainPanel.add(createStatsPanel(), BorderLayout.NORTH);
        mainPanel.add(createControlPanel(), BorderLayout.SOUTH);

        return mainPanel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(timingPanel, BorderLayout.NORTH);
        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 10, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Transaction Statistics"));

        panel.add(totalTransactionsLabel);
        panel.add(completedTransactionsLabel);
        panel.add(failedTransactionsLabel);
        panel.add(recoveredTransactionsLabel);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JTextField countField = new JTextField(10);
        JButton generateButton = new JButton("Generate Transactions");

        generateButton.addActionListener(e -> handleGenerateTransactions(countField));
        panel.add(new JLabel("Number of Transactions:"));
        panel.add(countField);
        panel.add(generateButton);

        return panel;
    }

    private void handleGenerateTransactions(JTextField countField) {
        try {
            int count = Integer.parseInt(countField.getText());
            if (count <= 0) {
                showErrorDialog("Please enter a positive number");
                return;
            }
            generateTransactions(count);
            countField.setText("");
        } catch (NumberFormatException ex) {
            showErrorDialog("Please enter a valid number");
        }
    }

    private void generateTransactions(int count) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < count; i++) {
                    processor.addTransaction(generateRandomAmount());
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

    private BigDecimal generateRandomAmount() {
        var min = new BigDecimal(100);
        var max = new BigDecimal(9900);
        BigDecimal randomBigDecimal = min.add(BigDecimal.valueOf(Math.random()).multiply(max.subtract(min)));
        return randomBigDecimal.setScale(2, RoundingMode.CEILING);
    }

    public void addTransactionToTable(Transaction transaction) {
        SwingUtilities.invokeLater(() -> {
            String transactionId = transaction.getId().toString().substring(0, 8);

            // Check if transaction already exists
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(transactionId)) {
                    updateTransactionStatus(transaction);
                    return;
                }
            }

            // Add new transaction to table
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
            threadTimes.forEach((threadName, time) -> threadTimeLabels.computeIfAbsent(threadName, key -> {
                JLabel label = new JLabel();
                timingPanel.add(label);
                timingPanel.revalidate();
                return label;
            }).setText(String.format("%s: %d ms", threadName, time)));

            JLabel totalLabel = threadTimeLabels.get("Total");
            if (totalLabel != null) {
                totalLabel.setText(String.format("Total Time: %d ms", totalTime));
            }
        });
    }

    private void startStatisticsUpdater() {
        Timer timer = new Timer(1000, e -> SwingUtilities.invokeLater(() -> {
            int total = tableModel.getRowCount();
            int completed = 0;
            int failed = 0;

            for (int i = 0; i < total; i++) {
                String status = tableModel.getValueAt(i, 3).toString();
                if ("COMPLETED".equals(status)) completed++;
                if ("FAILED".equals(status)) failed++;
            }

            totalTransactionsLabel.setText("Total: " + total);
            completedTransactionsLabel.setText("Completed: " + completed);
            failedTransactionsLabel.setText("Failed: " + failed);
        }));
        timer.start();
    }

    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, "Invalid Input", JOptionPane.WARNING_MESSAGE);
    }
}