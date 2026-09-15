package com.fiap.bank.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.application.exception.AccountBlockedException;
import com.fiap.bank.atm.application.exception.AccountNotFoundException;
import com.fiap.bank.atm.application.exception.DailyLimitExceededException;
import com.fiap.bank.atm.application.exception.InsufficientFundsException;
import com.fiap.bank.atm.application.exception.InvalidPinException;
import com.fiap.bank.atm.application.service.AtmService;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import com.fiap.bank.atm.infrastructure.persistence.AccountRepositoryJdbcImpl;
import com.fiap.bank.atm.infrastructure.persistence.ConnectionFactory;
import com.fiap.bank.atm.infrastructure.persistence.DatabaseInitializer;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste de integração da pilha completa (application + domain + infrastructure),
 * exatamente como o Composition Root a monta em produção — apenas sem a camada
 * Swing.
 */
class AtmServiceIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private ConnectionFactory connectionFactory;
    private AtmService atmService;

    @BeforeEach
    void setUp() {
        connectionFactory = new ConnectionFactory(temporaryDirectory.resolve("atm-e2e.db").toString());
        new DatabaseInitializer(connectionFactory).initialize();
        atmService = new AtmService(newRepository());
    }

    private AccountRepository newRepository() {
        return new AccountRepositoryJdbcImpl(connectionFactory);
    }

    @Test
    @DisplayName("Deve autenticar e devolver apenas DTOs, sem expor entidades de domínio")
    void shouldAuthenticateAndReturnDto() {
        AccountInfoDTO account = atmService.authenticate("12345", "1234");

        assertEquals("12345", account.accountNumber());
        assertEquals("0001", account.agency());
        assertEquals(new BigDecimal("5000.00"), account.balance());
        assertEquals("ACTIVE", account.status());
        assertFalse(account.blocked());
        assertTrue(atmService.isAuthenticated());
    }

    @Test
    @DisplayName("Deve devolver Optional vazio quando não houver sessão ativa")
    void shouldReturnEmptyOptionalWhenLoggedOut() {
        assertTrue(atmService.getCurrentAccount().isEmpty());

        atmService.authenticate("12345", "1234");
        assertTrue(atmService.getCurrentAccount().isPresent());

        atmService.logout();
        assertTrue(atmService.getCurrentAccount().isEmpty());
    }

    @Test
    @DisplayName("Deve refletir saque e depósito no banco de dados")
    void shouldPersistWithdrawalAndDeposit() {
        atmService.authenticate("12345", "1234");

        atmService.withdraw(200.00);
        atmService.deposit(50.00);

        assertEquals(new BigDecimal("4850.00"), atmService.getBalance());

        // Nova pilha de serviço, como se a aplicação tivesse sido reiniciada.
        AtmService restartedService = new AtmService(newRepository());
        assertEquals(new BigDecimal("4850.00"), restartedService.authenticate("12345", "1234").balance());
    }

    @Test
    @DisplayName("Deve transferir valores entre contas debitando e creditando o extrato")
    void shouldTransferBetweenAccounts() {
        atmService.authenticate("12345", "1234");
        atmService.transfer("67890", 300.00);

        assertEquals(new BigDecimal("4700.00"), atmService.getBalance());

        AtmService targetSession = new AtmService(newRepository());
        AccountInfoDTO target = targetSession.authenticate("67890", "5678");
        assertEquals(new BigDecimal("1500.00"), target.balance());

        List<TransactionDTO> statement = targetSession.getLastTransactions(1);
        assertEquals("TRANSFER_IN", statement.get(0).type());
        assertEquals(new BigDecimal("300.00"), statement.get(0).amount());
    }

    @Test
    @DisplayName("Deve devolver o extrato do lançamento mais recente para o mais antigo")
    void shouldReturnStatementOrderedByMostRecent() {
        atmService.authenticate("12345", "1234");
        atmService.deposit(10.00);

        List<TransactionDTO> statement = atmService.getStatement();

        assertEquals("DEPOSIT", statement.get(0).type());
        assertEquals(4, statement.size());

        // O limite do comprovante impresso é respeitado pela Streams API.
        assertEquals(2, atmService.getLastTransactions(2).size());
    }

    @Test
    @DisplayName("Deve traduzir as falhas de negócio para as exceções da camada de aplicação")
    void shouldTranslateDomainFailures() {
        atmService.authenticate("12345", "1234");

        assertThrows(InsufficientFundsException.class, () -> atmService.withdraw(999_999.00));
        assertThrows(DailyLimitExceededException.class, () -> atmService.withdraw(1_600.00));
        assertThrows(AccountNotFoundException.class, () -> atmService.transfer("00000", 10.00));
        assertThrows(IllegalArgumentException.class, () -> atmService.deposit(0.00));
        assertThrows(InvalidPinException.class, () -> atmService.authenticate("00000", "1234"));
    }

    @Test
    @DisplayName("Deve bloquear a conta após três tentativas de senha e manter o bloqueio")
    void shouldBlockAccountAfterThreeFailedAttempts() {
        assertThrows(InvalidPinException.class, () -> atmService.authenticate("12345", "0000"));
        assertThrows(InvalidPinException.class, () -> atmService.authenticate("12345", "0000"));
        assertThrows(AccountBlockedException.class, () -> atmService.authenticate("12345", "0000"));

        AtmService restartedService = new AtmService(newRepository());
        assertThrows(AccountBlockedException.class, () -> restartedService.authenticate("12345", "1234"));
    }
}
