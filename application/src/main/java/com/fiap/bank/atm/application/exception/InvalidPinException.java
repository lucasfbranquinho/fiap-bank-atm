package com.fiap.bank.atm.application.exception;

/**
 * Senha (PIN) inválida informada no teclado do caixa eletrônico.
 */
public class InvalidPinException extends AtmOperationException {

    public InvalidPinException(String message) {
        super(message);
    }
}
