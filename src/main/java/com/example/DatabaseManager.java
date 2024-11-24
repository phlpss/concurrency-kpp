package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class DatabaseManager {
    private final HikariDataSource dataSource;
    String dbUrl = "jdbc:postgresql://localhost:5432/Transactions";
    String username = "sa";
    String password = "pa";

    public DatabaseManager() {
        this.dataSource = configureDataSource(dbUrl, username, password);
        initDatabase();
    }

    private HikariDataSource configureDataSource(String dbUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10); // Adjust based on app requirements
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setConnectionTimeout(30000);
        config.setMaxLifetime(1800000);
        return new HikariDataSource(config);
    }

    private void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS transactions (
                id UUID PRIMARY KEY,
                amount FLOAT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                status VARCHAR(20) NOT NULL,
                retry_count INT DEFAULT 0
            )
            """;

        try (var conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    public List<Transaction> recoverTransactions() {
        String sql = """
            SELECT id, amount, created_at, status, retry_count
            FROM transactions
            WHERE status IN ('NEW', 'PROCESSING', 'RETRY')
            """;

        List<Transaction> transactionsToRecover = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                UUID id = rs.getObject("id", UUID.class);
                BigDecimal amount = rs.getBigDecimal("amount");
                Timestamp createdAt = rs.getTimestamp("created_at");
                String status = rs.getString("status");
                int retryCount = rs.getInt("retry_count");

                Transaction transaction = new Transaction(id, amount);
                transaction.setCreatedAt(createdAt.toLocalDateTime());
                transaction.setStatus(TransactionStatus.valueOf(status));
                transaction.setRetryCount(retryCount);

                transactionsToRecover.add(transaction);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to recover transactions", e);
        }

        return transactionsToRecover;
    }

    public void saveTransaction(Transaction transaction) {
        String sql = """
            INSERT INTO transactions (id, amount, created_at, status, retry_count)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // Enable transaction management

            pstmt.setObject(1, transaction.getId());
            pstmt.setBigDecimal(2, transaction.getAmount());
            pstmt.setTimestamp(3, Timestamp.valueOf(transaction.getCreatedAt()));
            pstmt.setString(4, transaction.getStatus().name());
            pstmt.setInt(5, transaction.getRetryCount());

            pstmt.executeUpdate();
            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save transaction", e);
        }
    }

    public void updateTransactionStatus(Transaction transaction) {
        String sql = """
            UPDATE transactions
            SET status = ?, retry_count = ?
            WHERE id = ?
            """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            pstmt.setString(1, transaction.getStatus().name());
            pstmt.setInt(2, transaction.getRetryCount());
            pstmt.setObject(3, transaction.getId());

            pstmt.executeUpdate();
            conn.commit();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to update transaction status", e);
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}