package com.fiap.bank.atm.application.exception;

/**
 * Saldo insuficiente para concluir a operação solicitada.
 */
public class InsufficientFundsException extends AtmOperationException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
