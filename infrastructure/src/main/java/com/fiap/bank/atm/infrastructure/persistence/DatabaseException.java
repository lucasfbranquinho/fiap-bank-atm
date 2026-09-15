package com.fiap.bank.atm.infrastructure.persistence;

/**
 * Falha técnica de acesso a dados.
 *
 * <p>
 * Envelopa a {@code SQLException} (exceção checada do JDBC) em uma exceção não
 * checada, para que os detalhes de infraestrutura não contaminem as assinaturas
 * dos contratos declarados pelo domínio.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
