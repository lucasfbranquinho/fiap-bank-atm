package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.AccountStatus;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do repositório de contas com <strong>JDBC nativo</strong> sobre
 * SQLite.
 *
 * <p>
 * Esta classe respeita o contrato ditado pelo domínio ({@link AccountRepository}
 * e, por herança, {@code ATMRepository<Account>}) e é a única do sistema que
 * conhece SQL.
 *
 * <p>
 * Regras de segurança aplicadas:
 * <ul>
 * <li>Todas as instruções usam {@link PreparedStatement} com injeção de
 * parâmetros pelos métodos {@code set*}, eliminando a superfície de ataque de
 * SQL Injection;</li>
 * <li>Nenhuma instrução é montada por concatenação de Strings;</li>
 * <li>Nenhum ORM é utilizado: o mapeamento objeto-relacional é feito
 * manualmente a partir do {@link ResultSet}.</li>
 * </ul>
 */
public class AccountRepositoryJdbcImpl implements AccountRepository {

    private static final String SELECT_ACCOUNT_COLUMNS = """
            SELECT id, agency, number, balance, status, pin, daily_withdrawal_limit,
                   failed_attempts, created_at, updated_at
              FROM tb_account
            """;

    private static final String SELECT_BY_NUMBER = SELECT_ACCOUNT_COLUMNS + " WHERE number = ?";

    private static final String SELECT_BY_ID = SELECT_ACCOUNT_COLUMNS + " WHERE id = ?";

    private static final String SELECT_ALL = SELECT_ACCOUNT_COLUMNS + " ORDER BY agency, number";

    private static final String SELECT_TRANSACTIONS_BY_ACCOUNT = """
            SELECT id, account_id, type, amount, description, created_at
              FROM tb_transaction
             WHERE account_id = ?
             ORDER BY created_at DESC
            """;

    private static final String UPSERT_ACCOUNT = """
            INSERT INTO tb_account (id, agency, number, balance, status, pin,
                                    daily_withdrawal_limit, failed_attempts, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET balance         = excluded.balance,
                                          status          = excluded.status,
                                          failed_attempts = excluded.failed_attempts,
                                          updated_at      = excluded.updated_at
            """;

    private static final String INSERT_TRANSACTION = """
            INSERT INTO tb_transaction (id, account_id, type, amount, description, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String DELETE_TRANSACTIONS_BY_ACCOUNT = "DELETE FROM tb_transaction WHERE account_id = ?";

    private static final String DELETE_ACCOUNT = "DELETE FROM tb_account WHERE id = ?";

    private final ConnectionFactory connectionFactory;

    public AccountRepositoryJdbcImpl(ConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Connection factory cannot be null");
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        return findBy(SELECT_BY_NUMBER, accountNumber);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return findBy(SELECT_BY_ID, id.toString());
    }

    @Override
    public List<Account> findAll() {
        Connection connection = connectionFactory.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
                ResultSet resultSet = statement.executeQuery()) {

            List<Account> accounts = new ArrayList<>();
            while (resultSet.next()) {
                accounts.add(mapAccount(connection, resultSet));
            }
            return List.copyOf(accounts);

        } catch (SQLException exception) {
            throw new DatabaseException("Falha ao listar as contas do FIAP Bank.", exception);
        } finally {
            connectionFactory.close(connection);
        }
    }

    /**
     * Persiste o estado do agregado: atualiza (ou insere) a conta e grava os
     * lançamentos ainda não registrados, tudo dentro de uma única transação de
     * banco de dados.
     */
    @Override
    public void save(Account account) {
        Objects.requireNonNull(account, "Account cannot be null");

        Connection connection = connectionFactory.getConnection();
        try {
            connection.setAutoCommit(false);
            saveAccount(connection, account);
            saveTransactions(connection, account);
            connection.commit();
        } catch (SQLException exception) {
            rollback(connection);
            throw new DatabaseException("Falha ao salvar a conta " + account.getAccountNumber() + ".", exception);
        } finally {
            connectionFactory.close(connection);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Id cannot be null");

        Connection connection = connectionFactory.getConnection();
        try {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(DELETE_TRANSACTIONS_BY_ACCOUNT)) {
                statement.setString(1, id.toString());
                statement.executeUpdate();
            }

            int deletedRows;
            try (PreparedStatement statement = connection.prepareStatement(DELETE_ACCOUNT)) {
                statement.setString(1, id.toString());
                deletedRows = statement.executeUpdate();
            }

            connection.commit();
            return deletedRows > 0;

        } catch (SQLException exception) {
            rollback(connection);
            throw new DatabaseException("Falha ao remover a conta " + id + ".", exception);
        } finally {
            connectionFactory.close(connection);
        }
    }

    private Optional<Account> findBy(String sql, String parameter) {
        Connection connection = connectionFactory.getConnection();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, parameter);

            try (ResultSet resultSet = statement.executeQuery()) {
                // Ausência de registro nunca vira null: devolvemos Optional vazio.
                return resultSet.next()
                        ? Optional.of(mapAccount(connection, resultSet))
                        : Optional.empty();
            }

        } catch (SQLException exception) {
            throw new DatabaseException("Falha ao consultar a conta no banco de dados.", exception);
        } finally {
            connectionFactory.close(connection);
        }
    }

    private void saveAccount(Connection connection, Account account) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ACCOUNT)) {
            statement.setString(1, account.getId().toString());
            statement.setString(2, account.getAgency());
            statement.setString(3, account.getAccountNumber());
            statement.setBigDecimal(4, account.getBalance().getAmount());
            statement.setString(5, account.getStatus().name());
            statement.setString(6, account.getPin());
            statement.setBigDecimal(7, account.getDailyWithdrawalLimit().getAmount());
            statement.setInt(8, account.getFailedAttempts());
            statement.setTimestamp(9, Timestamp.valueOf(account.getCreatedAt()));
            statement.setTimestamp(10, Timestamp.valueOf(account.getUpdatedAt()));
            statement.executeUpdate();
        }
    }

    private void saveTransactions(Connection connection, Account account) throws SQLException {
        List<Transaction> transactions = account.getTransactions();
        if (transactions.isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRANSACTION)) {
            for (Transaction transaction : transactions) {
                statement.setString(1, transaction.getId().toString());
                statement.setString(2, transaction.getAccountId().toString());
                statement.setString(3, transaction.getType().name());
                statement.setBigDecimal(4, transaction.getAmount().getAmount());
                statement.setString(5, transaction.getDescription());
                statement.setTimestamp(6, Timestamp.valueOf(transaction.getTimestamp()));
                statement.addBatch();
            }
            // Lançamentos já gravados são ignorados pelo ON CONFLICT DO NOTHING,
            // tornando a gravação do histórico idempotente.
            statement.executeBatch();
        }
    }

    /**
     * Mapeia a linha corrente do {@link ResultSet} para o agregado de domínio,
     * recuperando também as transações vinculadas à conta.
     */
    private Account mapAccount(Connection connection, ResultSet resultSet) throws SQLException {
        UUID id = UUID.fromString(resultSet.getString("id"));

        return Account.restore(
                id,
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime(),
                resultSet.getString("agency"),
                resultSet.getString("number"),
                resultSet.getString("pin"),
                Money.of(resultSet.getBigDecimal("balance")),
                Money.of(resultSet.getBigDecimal("daily_withdrawal_limit")),
                AccountStatus.fromDatabase(resultSet.getString("status")),
                resultSet.getInt("failed_attempts"),
                findTransactionsByAccount(connection, id));
    }

    /**
     * Recupera o extrato da conta iterando o {@link ResultSet} do JDBC e
     * convertendo cada linha em uma entidade {@link Transaction}.
     */
    private List<Transaction> findTransactionsByAccount(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_TRANSACTIONS_BY_ACCOUNT)) {

            statement.setString(1, accountId.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (resultSet.next()) {
                    transactions.add(mapTransaction(resultSet));
                }
                return transactions;
            }
        }
    }

    private Transaction mapTransaction(ResultSet resultSet) throws SQLException {
        return new Transaction(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("account_id")),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                TransactionType.fromDatabase(resultSet.getString("type")),
                Money.of(resultSet.getBigDecimal("amount")),
                resultSet.getString("description"));
    }

    private void rollback(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException exception) {
            throw new DatabaseException("Falha ao desfazer a transação no banco de dados.", exception);
        }
    }
}
