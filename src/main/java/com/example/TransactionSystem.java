package com.example;

import javax.swing.*;

public class TransactionSystem {
    public static void main(String[] args) {
        // Database configuration
        String dbUrl = "jdbc:postgresql://localhost:5432/Transactions";
        String username = "sa";
        String password = "pa";

        // Launch the GUI
        SwingUtilities.invokeLater(() -> {
            TransactionUI gui = new TransactionUI(dbUrl, username, password);
            gui.setVisible(true);
        });
    }
}