package com.example;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TransactionUI gui = new TransactionUI();
            gui.setVisible(true);
        });
    }
}