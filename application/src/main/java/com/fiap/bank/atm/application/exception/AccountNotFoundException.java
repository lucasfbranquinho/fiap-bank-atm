package com.fiap.bank.atm.application.exception;

/**
 * Conta inexistente na base de dados.
 *
 * <p>
 * Estende {@link IllegalArgumentException} (tipo da biblioteca padrão do Java)
 * para que a apresentação continue tratando o caso exatamente como antes, sem
 * precisar conhecer novos tipos.
 */
public class AccountNotFoundException extends IllegalArgumentException {

    public AccountNotFoundException(String message) {
        super(message);
    }
}
