package com.fiap.bank.atm.application.exception;

/**
 * Conta bloqueada por excesso de tentativas de senha ou por decisão do banco.
 */
public class AccountBlockedException extends AtmOperationException {

    public AccountBlockedException(String message) {
        super(message);
    }
}
