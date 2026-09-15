package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.Account;
import java.util.Optional;

/**
 * Contrato de persistência do agregado {@link Account}.
 *
 * <p>
 * Estende a abstração genérica {@link ATMRepository}, herdando as operações de
 * CRUD comuns e acrescentando apenas a consulta específica do negócio bancário.
 *
 * <p>
 * Este contrato pertence ao <strong>domínio</strong> (Inversão de Dependência):
 * é o domínio quem dita as regras de acesso a dados, e a camada de
 * infraestrutura quem se submete a elas ao implementá-las com JDBC.
 */
public interface AccountRepository extends ATMRepository<Account> {

    /**
     * Busca uma conta pelo número informado no caixa eletrônico.
     *
     * @param accountNumber número da conta digitado pelo cliente
     * @return {@code Optional} com a conta quando existir, vazio caso contrário
     */
    Optional<Account> findByAccountNumber(String accountNumber);
}
