package com.fiap.bank.atm.domain.model;

import com.fiap.bank.atm.domain.exception.AccountBlockedException;
import com.fiap.bank.atm.domain.exception.DailyLimitExceededException;
import com.fiap.bank.atm.domain.exception.InsufficientFundsException;
import com.fiap.bank.atm.domain.exception.InvalidPinException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raiz de agregado do domínio bancário.
 *
 * <p>
 * A conta é a única guardiã das suas próprias invariantes: saldo, limite diário,
 * bloqueio por tentativas de senha e histórico de transações só podem ser
 * alterados através dos comportamentos expostos aqui. Nenhuma camada externa
 * consegue mutar o estado interno diretamente.
 */
public class Account extends BaseEntity {

    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final Money MINIMUM_OPERATION_AMOUNT = Money.of(0.01);

    private final String agency;
    private final String accountNumber;
    private final String pin;
    private final Money dailyWithdrawalLimit;
    private Money balance;
    private AccountStatus status;
    private int failedAttempts;
    private final List<Transaction> transactions;

    /**
     * Construtor de criação de uma nova conta ativa.
     */
    public Account(UUID id, String agency, String accountNumber, String pin, Money initialBalance,
            Money dailyWithdrawalLimit) {
        super(id);
        this.agency = Objects.requireNonNull(agency, "Agency cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.pin = Objects.requireNonNull(pin, "PIN cannot be null");
        this.balance = Objects.requireNonNull(initialBalance, "Initial balance cannot be null");
        this.dailyWithdrawalLimit = Objects.requireNonNull(dailyWithdrawalLimit, "Daily limit cannot be null");
        this.status = AccountStatus.ACTIVE;
        this.failedAttempts = 0;
        this.transactions = new ArrayList<>();
    }

    private Account(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt, String agency, String accountNumber,
            String pin, Money balance, Money dailyWithdrawalLimit, AccountStatus status, int failedAttempts,
            List<Transaction> transactions) {
        super(id, createdAt, updatedAt);
        this.agency = Objects.requireNonNull(agency, "Agency cannot be null");
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number cannot be null");
        this.pin = Objects.requireNonNull(pin, "PIN cannot be null");
        this.balance = Objects.requireNonNull(balance, "Balance cannot be null");
        this.dailyWithdrawalLimit = Objects.requireNonNull(dailyWithdrawalLimit, "Daily limit cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.failedAttempts = failedAttempts;
        this.transactions = new ArrayList<>(Objects.requireNonNull(transactions, "Transactions cannot be null"));
    }

    /**
     * Reconstitui o agregado a partir dos dados lidos do banco relacional.
     *
     * <p>
     * É o único caminho pelo qual a camada de infraestrutura pode devolver uma
     * conta já existente para dentro do domínio, sem burlar as regras de negócio.
     */
    public static Account restore(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt, String agency,
            String accountNumber, String pin, Money balance, Money dailyWithdrawalLimit, AccountStatus status,
            int failedAttempts, List<Transaction> transactions) {
        return new Account(id, createdAt, updatedAt, agency, accountNumber, pin, balance, dailyWithdrawalLimit,
                status, failedAttempts, transactions);
    }

    public String getAgency() {
        return agency;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    /**
     * Exposto exclusivamente para o mapeamento objeto-relacional manual feito via
     * JDBC na camada de infraestrutura. Nenhum DTO da aplicação trafega o PIN.
     */
    public String getPin() {
        return pin;
    }

    public Money getBalance() {
        return balance;
    }

    public Money getDailyWithdrawalLimit() {
        return dailyWithdrawalLimit;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isBlocked() {
        return status == AccountStatus.BLOCKED;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public List<Transaction> getTransactions() {
        return Collections.unmodifiableList(transactions);
    }

    /**
     * Total já sacado no dia corrente, derivado do próprio histórico através da
     * Streams API (filtro por tipo e data + redução monetária), em substituição ao
     * antigo contador imperativo mantido em memória.
     */
    public Money getTotalWithdrawnToday() {
        LocalDate today = LocalDate.now();
        return transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.WITHDRAWAL)
                .filter(transaction -> transaction.getTimestamp().toLocalDate().isEqual(today))
                .map(Transaction::getAmount)
                .reduce(Money.ZERO, Money::plus);
    }

    /**
     * Limite de saque ainda disponível para o dia corrente.
     */
    public Money getRemainingDailyLimit() {
        return dailyWithdrawalLimit.minus(getTotalWithdrawnToday());
    }

    public void authenticate(String pinAttempt) {
        if (isBlocked()) {
            throw new AccountBlockedException("Esta conta está bloqueada por excesso de tentativas de senha.");
        }

        if (!this.pin.equals(pinAttempt)) {
            failedAttempts++;
            touch();
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                status = AccountStatus.BLOCKED;
                throw new AccountBlockedException(
                        "Conta bloqueada após " + MAX_FAILED_ATTEMPTS + " tentativas incorretas.");
            }
            throw new InvalidPinException(
                    "Senha incorreta. Tentativa " + failedAttempts + " de " + MAX_FAILED_ATTEMPTS + ".");
        }

        failedAttempts = 0; // Reset attempts on successful login
        touch();
    }

    public void withdraw(Money amount) {
        if (isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(MINIMUM_OPERATION_AMOUNT)) {
            throw new IllegalArgumentException("O valor do saque deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException(
                    "Saldo insuficiente para realizar o saque. Saldo disponível: " + balance);
        }

        Money projectedWithdrawal = getTotalWithdrawnToday().plus(amount);
        if (projectedWithdrawal.isGreaterThan(dailyWithdrawalLimit)) {
            throw new DailyLimitExceededException(
                    "Limite diário de saque excedido. Limite restante hoje: " + getRemainingDailyLimit());
        }

        balance = balance.minus(amount);
        register(TransactionType.WITHDRAWAL, amount, "Saque eletrônico");
    }

    public void deposit(Money amount) {
        if (isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta bloqueada.");
        }

        if (amount.isLessThan(MINIMUM_OPERATION_AMOUNT)) {
            throw new IllegalArgumentException("O valor do depósito deve ser maior que zero.");
        }

        balance = balance.plus(amount);
        register(TransactionType.DEPOSIT, amount, "Depósito em dinheiro");
    }

    public void transfer(Account targetAccount, Money amount) {
        if (isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta de origem bloqueada.");
        }

        if (targetAccount.isBlocked()) {
            throw new AccountBlockedException("Operação não permitida: conta de destino está bloqueada.");
        }

        if (amount.isLessThan(MINIMUM_OPERATION_AMOUNT)) {
            throw new IllegalArgumentException("O valor da transferência deve ser maior que zero.");
        }

        if (amount.isGreaterThan(balance)) {
            throw new InsufficientFundsException("Saldo insuficiente para transferência. Saldo disponível: " + balance);
        }

        if (this.accountNumber.equals(targetAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Não é possível realizar transferência para a mesma conta.");
        }

        // Debita a conta de origem
        this.balance = this.balance.minus(amount);
        register(TransactionType.TRANSFER_OUT, amount, "Transf. para Conta " + targetAccount.getAccountNumber());

        // Credita a conta de destino
        targetAccount.receiveTransfer(this, amount);
    }

    private void receiveTransfer(Account sourceAccount, Money amount) {
        this.balance = this.balance.plus(amount);
        register(TransactionType.TRANSFER_IN, amount, "Transf. de Conta " + sourceAccount.getAccountNumber());
    }

    private void register(TransactionType type, Money amount, String description) {
        transactions.add(new Transaction(UUID.randomUUID(), getId(), type, amount, description));
        touch();
    }

    private void touch() {
        setUpdatedAt(LocalDateTime.now());
    }
}
