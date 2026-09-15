package com.fiap.bank.atm.application.exception;

/**
 * Limite diário de saque da conta excedido.
 */
public class DailyLimitExceededException extends AtmOperationException {

    public DailyLimitExceededException(String message) {
        super(message);
    }
}
