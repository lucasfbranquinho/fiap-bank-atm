package com.fiap.bank.atm.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Responsável por criar o esquema do banco (DDL) e aplicar a carga inicial de
 * contas de teste (DML), conforme o dicionário de dados do FIAP Bank.
 *
 * <p>
 * Todas as instruções são executadas via {@link PreparedStatement} — nenhuma
 * instrução SQL é montada por concatenação de Strings.
 *
 * <p>
 * A carga inicial usa {@code ON CONFLICT DO NOTHING}, tornando a rotina
 * idempotente: o banco é semeado apenas na primeira execução e os dados
 * transacionados sobrevivem a todos os reinícios seguintes.
 */
public class DatabaseInitializer {

    private static final String CREATE_TABLE_ACCOUNT = """
            CREATE TABLE IF NOT EXISTS tb_account (
                id VARCHAR(36) PRIMARY KEY,
                agency VARCHAR(10) NOT NULL,
                number VARCHAR(20) NOT NULL UNIQUE,
                balance DECIMAL(15, 2) NOT NULL,
                status VARCHAR(20) NOT NULL,
                pin VARCHAR(4) NOT NULL,
                daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                failed_attempts INTEGER NOT NULL DEFAULT 0,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """;

    private static final String CREATE_TABLE_TRANSACTION = """
            CREATE TABLE IF NOT EXISTS tb_transaction (
                id VARCHAR(36) PRIMARY KEY,
                account_id VARCHAR(36) NOT NULL,
                type VARCHAR(20) NOT NULL,
                amount DECIMAL(15, 2) NOT NULL,
                description VARCHAR(100) NOT NULL,
                created_at TIMESTAMP NOT NULL,
                FOREIGN KEY (account_id) REFERENCES tb_account(id)
            )
            """;

    private static final String CREATE_INDEX_TRANSACTION_ACCOUNT = """
            CREATE INDEX IF NOT EXISTS ix_transaction_account ON tb_transaction (account_id)
            """;

    private static final String INSERT_ACCOUNT = """
            INSERT INTO tb_account (id, agency, number, balance, status, pin,
                                    daily_withdrawal_limit, failed_attempts, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String INSERT_TRANSACTION = """
            INSERT INTO tb_transaction (id, account_id, type, amount, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    /** Contas de teste do FIAP Bank (dicionário de dados - carga inicial). */
    private static final List<SeedAccount> SEED_ACCOUNTS = List.of(
            new SeedAccount("550e8400-e29b-41d4-a716-446655440000", "0001", "12345", "1234",
                    new BigDecimal("5000.00"), new BigDecimal("1500.00"), "ACTIVE"),
            new SeedAccount("550e8400-e29b-41d4-a716-446655440001", "0001", "67890", "5678",
                    new BigDecimal("1200.00"), new BigDecimal("1000.00"), "ACTIVE"),
            new SeedAccount("550e8400-e29b-41d4-a716-446655440002", "0002", "99999", "9999",
                    new BigDecimal("50.00"), new BigDecimal("500.00"), "ACTIVE"),
            new SeedAccount("550e8400-e29b-41d4-a716-446655440003", "0002", "11111", "1111",
                    new BigDecimal("0.00"), new BigDecimal("500.00"), "BLOCKED"));

    /** Histórico inicial das contas de teste. */
    private static final List<SeedTransaction> SEED_TRANSACTIONS = List.of(
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440000", "550e8400-e29b-41d4-a716-446655440000",
                    "DEPOSIT", new BigDecimal("2000.00"), "Depósito em dinheiro", 3),
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440001", "550e8400-e29b-41d4-a716-446655440000",
                    "TRANSFER_IN", new BigDecimal("500.00"), "Transf. de Conta 67890", 2),
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440002", "550e8400-e29b-41d4-a716-446655440000",
                    "WITHDRAWAL", new BigDecimal("100.00"), "Saque eletrônico", 1),
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440003", "550e8400-e29b-41d4-a716-446655440001",
                    "DEPOSIT", new BigDecimal("1500.00"), "Depósito inicial", 5),
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440004", "550e8400-e29b-41d4-a716-446655440001",
                    "TRANSFER_OUT", new BigDecimal("500.00"), "Transf. para Conta 12345", 2),
            new SeedTransaction("650e8400-e29b-41d4-a716-446655440005", "550e8400-e29b-41d4-a716-446655440002",
                    "DEPOSIT", new BigDecimal("50.00"), "Abertura de conta", 10));

    private final ConnectionFactory connectionFactory;

    public DatabaseInitializer(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Cria o esquema (caso ainda não exista) e aplica a carga inicial.
     */
    public void initialize() {
        Connection connection = connectionFactory.getConnection();
        try {
            connection.setAutoCommit(false);
            createSchema(connection);
            seedAccounts(connection);
            seedTransactions(connection);
            connection.commit();
        } catch (SQLException exception) {
            rollback(connection);
            throw new DatabaseException("Falha ao inicializar o banco de dados do FIAP Bank.", exception);
        } finally {
            connectionFactory.close(connection);
        }
    }

    private void createSchema(Connection connection) throws SQLException {
        execute(connection, CREATE_TABLE_ACCOUNT);
        execute(connection, CREATE_TABLE_TRANSACTION);
        execute(connection, CREATE_INDEX_TRANSACTION_ACCOUNT);
    }

    private void execute(Connection connection, String ddl) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ddl)) {
            statement.execute();
        }
    }

    private void seedAccounts(Connection connection) throws SQLException {
        LocalDateTime openedAt = LocalDateTime.now().minusDays(30);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ACCOUNT)) {
            for (SeedAccount account : SEED_ACCOUNTS) {
                statement.setString(1, account.id());
                statement.setString(2, account.agency());
                statement.setString(3, account.number());
                statement.setBigDecimal(4, account.balance());
                statement.setString(5, account.status());
                statement.setString(6, account.pin());
                statement.setBigDecimal(7, account.dailyWithdrawalLimit());
                statement.setInt(8, 0);
                statement.setTimestamp(9, Timestamp.valueOf(openedAt));
                statement.setTimestamp(10, Timestamp.valueOf(openedAt));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void seedTransactions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRANSACTION)) {
            for (SeedTransaction transaction : SEED_TRANSACTIONS) {
                statement.setString(1, transaction.id());
                statement.setString(2, transaction.accountId());
                statement.setString(3, transaction.type());
                statement.setBigDecimal(4, transaction.amount());
                statement.setString(5, transaction.description());
                statement.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now().minusDays(transaction.daysAgo())));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            throw new DatabaseException("Falha ao desfazer a inicialização do banco de dados.", exception);
        }
    }

    private record SeedAccount(String id, String agency, String number, String pin, BigDecimal balance,
            BigDecimal dailyWithdrawalLimit, String status) {
    }

    private record SeedTransaction(String id, String accountId, String type, BigDecimal amount, String description,
            int daysAgo) {
    }
}
