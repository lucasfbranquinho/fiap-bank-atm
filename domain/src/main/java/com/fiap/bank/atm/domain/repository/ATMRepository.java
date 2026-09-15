package com.fiap.bank.atm.domain.repository;

import com.fiap.bank.atm.domain.model.BaseEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrato genérico de persistência do FIAP Bank ATM.
 *
 * <p>
 * O tipo genérico possui <strong>restrição de limite superior</strong>
 * ({@code T extends BaseEntity}), garantindo em tempo de compilação que apenas
 * entidades legítimas do domínio (que possuem identidade própria) possam ser
 * persistidas por esta abstração.
 *
 * <p>
 * Nenhum método de busca devolve {@code null}: a ausência de dados é
 * representada de forma explícita e defensiva por {@link Optional}, obrigando a
 * camada orquestradora a tratar ativamente a presença ou ausência do registro.
 *
 * @param <T> tipo da entidade de domínio gerenciada pelo repositório
 */
public interface ATMRepository<T extends BaseEntity> {

    /**
     * Recupera uma entidade pelo seu identificador único.
     *
     * @param id identificador da entidade
     * @return {@code Optional} preenchido quando encontrada, vazio caso contrário
     */
    Optional<T> findById(UUID id);

    /**
     * Recupera todas as entidades armazenadas.
     *
     * @return lista (possivelmente vazia, nunca {@code null}) de entidades
     */
    List<T> findAll();

    /**
     * Persiste o estado atual da entidade (inserção ou atualização).
     *
     * @param entity entidade a ser persistida
     */
    void save(T entity);

    /**
     * Remove a entidade identificada pelo id informado.
     *
     * @param id identificador da entidade
     * @return {@code true} se algum registro foi efetivamente removido
     */
    boolean deleteById(UUID id);
}
