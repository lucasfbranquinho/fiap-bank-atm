package com.fiap.bank.atm.application.mapper;

import com.fiap.bank.atm.application.dto.AccountInfoDTO;
import com.fiap.bank.atm.application.dto.TransactionDTO;
import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Transaction;
import java.util.Comparator;
import java.util.List;

/**
 * Tradutor entre as entidades do domínio e os contratos (Records) da aplicação.
 *
 * <p>
 * Concentrar a conversão aqui garante que nenhuma entidade de domínio escape da
 * camada de aplicação: a fronteira é atravessada exclusivamente por DTOs.
 */
public final class AtmMapper {

    private AtmMapper() {
        throw new UnsupportedOperationException("Classe utilitária não deve ser instanciada.");
    }

    /**
     * Converte o agregado {@link Account} em seu contrato público.
     */
    public static AccountInfoDTO toAccountInfo(Account account) {
        return new AccountInfoDTO(
                account.getId(),
                account.getAgency(),
                account.getAccountNumber(),
                account.getBalance().getAmount(),
                account.getDailyWithdrawalLimit().getAmount(),
                account.getTotalWithdrawnToday().getAmount(),
                account.getStatus().name(),
                account.isBlocked());
    }

    /**
     * Converte um lançamento do domínio em seu contrato público.
     */
    public static TransactionDTO toTransaction(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getAccountId(),
                transaction.getType().name(),
                transaction.getType().getDescription(),
                transaction.getAmount().getAmount(),
                transaction.getDescription(),
                transaction.getTimestamp());
    }

    /**
     * Monta o extrato da conta já ordenado do lançamento mais recente para o mais
     * antigo, utilizando a Streams API em substituição aos laços imperativos.
     *
     * @param account conta de origem dos lançamentos
     * @param limit   quantidade máxima de lançamentos retornados
     * @return lista imutável de lançamentos convertidos em DTO
     */
    public static List<TransactionDTO> toStatement(Account account, int limit) {
        return account.getTransactions().stream()
                .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
                .limit(limit)
                .map(AtmMapper::toTransaction)
                .toList();
    }
}
