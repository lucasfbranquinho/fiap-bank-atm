package com.fiap.bank.atm.application.exception;

/**
 * Exceção base das falhas de negócio expostas pela camada de aplicação.
 *
 * <p>
 * A camada de apresentação NÃO enxerga o pacote {@code domain} e, portanto, não
 * poderia capturar as exceções de negócio originais. O serviço de aplicação
 * traduz cada exceção do domínio para o seu equivalente deste pacote,
 * preservando o comportamento da tela sem furar a fronteira arquitetural.
 */
public class AtmOperationException extends RuntimeException {

    public AtmOperationException(String message) {
        super(message);
    }
}
