package com.fiap.bank.atm.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Contrato de saída com os dados públicos de uma conta.
 *
 * <p>
 * Implementado como <strong>Java Record</strong>: leve, imutável e totalmente
 * desacoplado do domínio. É este objeto — e nunca a entidade {@code Account} —
 * que trafega até a camada de apresentação, impedindo que a tela Swing invoque
 * comportamentos de negócio ou mute o estado do agregado.
 *
 * <p>
 * Repare que o PIN jamais é exposto neste contrato.
 *
 * @param id                   identificador único da conta
 * @param agency               agência da conta
 * @param accountNumber        número da conta
 * @param balance              saldo atual
 * @param dailyWithdrawalLimit limite diário de saque contratado
 * @param totalWithdrawnToday  total já sacado no dia corrente
 * @param status               situação cadastral (ACTIVE / BLOCKED)
 * @param blocked              indicador de conta bloqueada
 */
public record AccountInfoDTO(
        UUID id,
        String agency,
        String accountNumber,
        BigDecimal balance,
        BigDecimal dailyWithdrawalLimit,
        BigDecimal totalWithdrawnToday,
        String status,
        Boolean blocked) {

    /**
     * Limite de saque ainda disponível hoje, derivado dos próprios dados do
     * contrato.
     *
     * @return limite diário restante
     */
    public BigDecimal remainingDailyLimit() {
        return dailyWithdrawalLimit.subtract(totalWithdrawnToday);
    }
}
