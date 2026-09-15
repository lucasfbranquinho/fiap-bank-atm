package com.fiap.bank.atm.application.service;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.application.exception.AccountBlockedException;
import com.fiap.bank.atm.application.exception.AccountNotFoundException;
import com.fiap.bank.atm.application.exception.DailyLimitExceededException;
import com.fiap.bank.atm.application.exception.InsufficientFundsException;
import com.fiap.bank.atm.application.exception.InvalidPinException;
import com.fiap.bank.atm.application.mapper.AtmMapper;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Serviço de aplicação do caixa eletrônico: orquestra os casos de uso do ATM.
 *
 * <p>
 * <strong>Fronteira arquitetural:</strong> todos os métodos públicos recebem e
 * devolvem exclusivamente DTOs (Java Records) ou tipos da biblioteca padrão do
 * Java ({@code UUID}, {@code BigDecimal}, {@code Boolean}...). Nenhuma entidade
 * do domínio atravessa esta fronteira.
 *
 * <p>
 * O serviço guarda apenas o <em>identificador</em> da conta autenticada; o
 * agregado é recarregado do repositório a cada operação, de modo que a fonte da
 * verdade seja sempre o banco de dados relacional, e não a memória RAM.
 */
public class AtmService {

    /** Quantidade de lançamentos exibidos no comprovante impresso. */
    public static final int RECEIPT_TRANSACTION_LIMIT = 5;

    private final AccountRepository accountRepository;
    private UUID currentAccountId;

    public AtmService(AccountRepository accountRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "Account repository cannot be null");
    }

    /**
     * Autentica o cliente a partir do número da conta e do PIN digitados.
     *
     * <p>
     * Em caso de senha incorreta, o estado de tentativas (e o eventual bloqueio) é
     * persistido antes de a falha ser propagada.
     *
     * @return contrato com os dados públicos da conta autenticada
     */
    public AccountInfoDTO authenticate(String accountNumber, String pin) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new InvalidPinException("Conta não encontrada."));

        try {
            account.authenticate(pin);
        } catch (RuntimeException exception) {
            accountRepository.save(account);
            throw translate(exception);
        }

        accountRepository.save(account);
        this.currentAccountId = account.getId();
        return AtmMapper.toAccountInfo(account);
    }

    public void withdraw(double amount) {
        Account account = requireCurrentAccount();
        execute(() -> account.withdraw(Money.of(amount)));
        accountRepository.save(account);
    }

    public void deposit(double amount) {
        Account account = requireCurrentAccount();
        execute(() -> account.deposit(Money.of(amount)));
        accountRepository.save(account);
    }

    public void transfer(String targetAccountNumber, double amount) {
        Account sourceAccount = requireCurrentAccount();
        Account targetAccount = accountRepository.findByAccountNumber(targetAccountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Conta de destino não encontrada."));

        execute(() -> sourceAccount.transfer(targetAccount, Money.of(amount)));

        accountRepository.save(sourceAccount);
        accountRepository.save(targetAccount);
    }

    /**
     * @return saldo atual da conta autenticada
     */
    public BigDecimal getBalance() {
        return requireCurrentAccount().getBalance().getAmount();
    }

    /**
     * @return limite de saque ainda disponível hoje
     */
    public BigDecimal getRemainingDailyLimit() {
        return requireCurrentAccount().getRemainingDailyLimit().getAmount();
    }

    /**
     * @return extrato completo, do lançamento mais recente para o mais antigo
     */
    public List<TransactionDTO> getStatement() {
        return AtmMapper.toStatement(requireCurrentAccount(), Integer.MAX_VALUE);
    }

    /**
     * @param limit quantidade máxima de lançamentos
     * @return últimos lançamentos da conta autenticada
     */
    public List<TransactionDTO> getLastTransactions(int limit) {
        return AtmMapper.toStatement(requireCurrentAccount(), limit);
    }

    /**
     * Dados da conta autenticada.
     *
     * <p>
     * Devolve {@link Optional} vazio quando não há sessão ativa — jamais
     * {@code null} —, obrigando a apresentação a tratar a ausência de forma segura.
     */
    public Optional<AccountInfoDTO> getCurrentAccount() {
        return findCurrentAccount().map(AtmMapper::toAccountInfo);
    }

    public boolean isAuthenticated() {
        return currentAccountId != null;
    }

    public void logout() {
        this.currentAccountId = null;
    }

    private Optional<Account> findCurrentAccount() {
        return Optional.ofNullable(currentAccountId).flatMap(accountRepository::findById);
    }

    private Account requireCurrentAccount() {
        return findCurrentAccount()
                .orElseThrow(() -> new IllegalStateException("Nenhum usuário está autenticado no momento."));
    }

    /**
     * Executa um comportamento do domínio traduzindo eventuais falhas de negócio
     * para as exceções da camada de aplicação.
     */
    private void execute(Runnable domainOperation) {
        try {
            domainOperation.run();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    /**
     * Traduz as exceções do pacote {@code domain} para os equivalentes do pacote
     * {@code application}, únicos visíveis à camada de apresentação.
     */
    private RuntimeException translate(RuntimeException exception) {
        return switch (exception) {
            case com.fiap.bank.atm.domain.exception.AccountBlockedException ex ->
                new AccountBlockedException(ex.getMessage());
            case com.fiap.bank.atm.domain.exception.InvalidPinException ex ->
                new InvalidPinException(ex.getMessage());
            case com.fiap.bank.atm.domain.exception.InsufficientFundsException ex ->
                new InsufficientFundsException(ex.getMessage());
            case com.fiap.bank.atm.domain.exception.DailyLimitExceededException ex ->
                new DailyLimitExceededException(ex.getMessage());
            default -> exception;
        };
    }
}
