package com.fiap.bank.atm.domain.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * Situação cadastral da conta, espelhando a coluna {@code status} da tabela
 * {@code tb_account} do dicionário de dados do FIAP Bank.
 */
public enum AccountStatus {

    ACTIVE("Ativa"),
    BLOCKED("Bloqueada");

    private final String description;

    AccountStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Converte o valor textual persistido no banco para o enum correspondente.
     *
     * @param value conteúdo da coluna {@code status}
     * @return status equivalente; {@link #ACTIVE} quando o valor for desconhecido
     */
    public static AccountStatus fromDatabase(String value) {
        Objects.requireNonNull(value, "Status cannot be null");
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(ACTIVE);
    }
}
