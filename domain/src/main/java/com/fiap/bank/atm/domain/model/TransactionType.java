package com.fiap.bank.atm.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Natureza do lançamento financeiro, espelhando a coluna {@code type} da tabela
 * {@code tb_transaction}.
 */
public enum TransactionType {

    WITHDRAWAL("Saque"),
    DEPOSIT("Depósito"),
    TRANSFER_OUT("Transf. Enviada"),
    TRANSFER_IN("Transf. Recebida");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Converte o valor textual persistido no banco para o enum correspondente.
     *
     * @param value conteúdo da coluna {@code type}
     * @return tipo equivalente
     * @throws IllegalArgumentException quando o valor não corresponder a nenhum tipo
     */
    public static TransactionType fromDatabase(String value) {
        Objects.requireNonNull(value, "Transaction type cannot be null");
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de transação desconhecido: " + value));
    }
}
