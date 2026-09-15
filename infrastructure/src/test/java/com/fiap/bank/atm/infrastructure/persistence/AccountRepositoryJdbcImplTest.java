package com.fiap.bank.atm.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes de integração do repositório JDBC contra um banco SQLite descartável.
 */
class AccountRepositoryJdbcImplTest {

    private static final String ACCOUNT_NUMBER = "12345";

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        connectionFactory = new ConnectionFactory(temporaryDirectory.resolve("atm-test.db").toString());
        new DatabaseInitializer(connectionFactory).initialize();
        accountRepository = new AccountRepositoryJdbcImpl(connectionFactory);
    }

    @Test
    @DisplayName("Deve recuperar a conta e o seu extrato a partir do banco de dados")
    void shouldLoadAccountWithTransactions() {
        Account account = accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow();

        assertEquals("0001", account.getAgency());
        assertEquals(new BigDecimal("5000.00"), account.getBalance().getAmount());
        assertEquals(3, account.getTransactions().size());
    }

    @Test
    @DisplayName("Deve devolver Optional vazio (nunca null) quando a conta não existir")
    void shouldReturnEmptyOptionalForUnknownAccount() {
        assertTrue(accountRepository.findByAccountNumber("00000").isEmpty());
        assertTrue(accountRepository.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("Deve manter o saldo e o novo lançamento após reabrir a conexão com o banco")
    void shouldPersistOperationsBetweenConnections() {
        Account account = accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow();
        account.withdraw(Money.of(100.00));
        accountRepository.save(account);

        // Simula o reinício da aplicação: novo repositório, nova conexão.
        AccountRepository restartedRepository = new AccountRepositoryJdbcImpl(connectionFactory);
        Account reloaded = restartedRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow();

        assertEquals(new BigDecimal("4900.00"), reloaded.getBalance().getAmount());
        assertEquals(4, reloaded.getTransactions().size());
        assertEquals(new BigDecimal("100.00"), reloaded.getTotalWithdrawnToday().getAmount());
    }

    @Test
    @DisplayName("Deve persistir o bloqueio da conta após três tentativas de senha incorretas")
    void shouldPersistAccountBlocking() {
        Account account = accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow();

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                account.authenticate("0000");
            } catch (RuntimeException expected) {
                accountRepository.save(account);
            }
        }

        assertTrue(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow().isBlocked());
    }

    @Test
    @DisplayName("Deve neutralizar tentativas de SQL Injection pelo uso de PreparedStatement")
    void shouldBeImmuneToSqlInjection() {
        Optional<Account> account = accountRepository.findByAccountNumber("' OR '1'='1");

        // O valor malicioso é tratado como dado, jamais como instrução SQL.
        assertTrue(account.isEmpty());
        assertFalse(accountRepository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Deve remover a conta e o seu histórico")
    void shouldDeleteAccount() {
        Account account = accountRepository.findByAccountNumber(ACCOUNT_NUMBER).orElseThrow();

        assertTrue(accountRepository.deleteById(account.getId()));
        assertTrue(accountRepository.findByAccountNumber(ACCOUNT_NUMBER).isEmpty());
        assertFalse(accountRepository.deleteById(account.getId()));
    }
}
