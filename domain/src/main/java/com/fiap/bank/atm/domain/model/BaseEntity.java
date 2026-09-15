package com.fiap.bank.atm.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Superclasse de todas as entidades do domínio.
 *
 * <p>
 * Define a identidade ({@link UUID}) e os metadados de auditoria comuns. Serve
 * também como <strong>limite superior</strong> do tipo genérico declarado em
 * {@code ATMRepository<T extends BaseEntity>}.
 */
public abstract class BaseEntity {

    private final UUID id;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Construtor de criação: utilizado quando uma entidade nasce na aplicação.
     */
    protected BaseEntity(UUID id) {
        this(id, LocalDateTime.now(), LocalDateTime.now());
    }

    /**
     * Construtor de reconstituição: utilizado pela camada de infraestrutura para
     * reidratar a entidade a partir dos dados lidos do banco relacional,
     * preservando as datas originais de auditoria.
     */
    protected BaseEntity(UUID id, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "CreatedAt cannot be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    public UUID getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "UpdatedAt cannot be null");
    }

    /**
     * Entidades são comparadas exclusivamente pela sua identidade, e não pelos
     * seus atributos (conceito de Entity no DDD).
     */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BaseEntity other)) {
            return false;
        }
        return getClass() == o.getClass() && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
