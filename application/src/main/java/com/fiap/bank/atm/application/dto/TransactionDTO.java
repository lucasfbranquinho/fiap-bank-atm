package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Contrato de saída de um lançamento do extrato.
 *
 * <p>
 * Também implementado como <strong>Java Record</strong>, carrega apenas tipos da
 * biblioteca padrão do Java ({@code UUID}, {@code BigDecimal},
 * {@code LocalDateTime} e {@code String}). A apresentação recebe a descrição do
 * tipo já resolvida, sem precisar conhecer o enum {@code TransactionType} do
 * domínio.
 *
 * @param id              identificador único da transação
 * @param accountId       conta à qual o lançamento pertence
 * @param type            código do tipo (WITHDRAWAL, DEPOSIT, TRANSFER_OUT, TRANSFER_IN)
 * @param typeDescription descrição amigável do tipo, exibida no extrato
 * @param amount          valor movimentado
 * @param description     histórico do lançamento
 * @param timestamp       data e hora do lançamento
 */
public record TransactionDTO(
        UUID id,
        UUID accountId,
        String type,
        String typeDescription,
        BigDecimal amount,
        String description,
        LocalDateTime timestamp) {
}
