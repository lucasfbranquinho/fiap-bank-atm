package com.fiap.bank.atm.domain.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

/**
 * Lançamento financeiro pertencente ao agregado {@link Account}.
 *
 * <p>
 * Entidade imutável: uma vez registrada, uma transação jamais é alterada — o
 * histórico do extrato é append-only, espelhando a tabela {@code tb_transaction}.
 */
public final class Transaction extends BaseEntity {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final UUID accountId;
    private final LocalDateTime timestamp;
    private final TransactionType type;
    private final Money amount;
    private final String description;

    /**
     * Construtor de criação: registra a transação no instante atual.
     */
    public Transaction(UUID id, UUID accountId, TransactionType type, Money amount, String description) {
        this(id, accountId, LocalDateTime.now(), type, amount, description);
    }

    /**
     * Construtor de reconstituição: utilizado ao mapear um {@code ResultSet} vindo
     * do banco de dados, preservando a data original do lançamento.
     */
    public Transaction(UUID id, UUID accountId, LocalDateTime timestamp, TransactionType type, Money amount,
            String description) {
        super(id, Objects.requireNonNull(timestamp, "Timestamp cannot be null"), timestamp);
        this.accountId = Objects.requireNonNull(accountId, "Account id cannot be null");
        this.timestamp = timestamp;
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.amount = Objects.requireNonNull(amount, "Amount cannot be null");
        this.description = Objects.requireNonNull(description, "Description cannot be null");
    }

    public UUID getAccountId() {
        return accountId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getFormattedTimestamp() {
        return timestamp.format(FORMATTER);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s (%s)", getFormattedTimestamp(), type.getDescription(), amount, description);
    }
}
