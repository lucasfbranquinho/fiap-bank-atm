package com.fiap.bank.atm;

import com.fiap.bank.atm.application.service.AtmService;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import com.fiap.bank.atm.infrastructure.persistence.AccountRepositoryJdbcImpl;
import com.fiap.bank.atm.infrastructure.persistence.ConnectionFactory;
import com.fiap.bank.atm.infrastructure.persistence.DatabaseInitializer;
import com.fiap.bank.atm.presentation.AtmFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Ponto de entrada do FIAP Bank ATM e <strong>Composition Root</strong> da
 * arquitetura.
 *
 * <p>
 * Este é o único lugar do sistema autorizado a conhecer todas as camadas ao
 * mesmo tempo. É aqui que a implementação concreta de infraestrutura
 * ({@link AccountRepositoryJdbcImpl}) é injetada no serviço de aplicação, que por
 * sua vez é entregue à tela Swing.
 *
 * <p>
 * Graças a este módulo, a camada de apresentação permanece dependendo
 * exclusivamente de {@code application}: ela recebe o serviço já montado e nunca
 * precisa saber que existe um banco SQLite por trás.
 */
public class AtmApplication {

    public static void main(String[] args) {
        try {
            // Camada de Infraestrutura: conexão e esquema do banco relacional
            ConnectionFactory connectionFactory = new ConnectionFactory();
            new DatabaseInitializer(connectionFactory).initialize();

            // Inversão de Dependência: o contrato é do domínio, a implementação é do JDBC
            AccountRepository accountRepository = new AccountRepositoryJdbcImpl(connectionFactory);

            // Camada de Aplicação: orquestra os casos de uso do caixa eletrônico
            AtmService atmService = new AtmService(accountRepository);

            // Camada de Apresentação, iniciada com segurança na Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(() -> {
                AtmFrame mainFrame = new AtmFrame(atmService);
                mainFrame.setVisible(true);
            });

        } catch (RuntimeException exception) {
            System.err.println("Falha ao iniciar o FIAP Bank ATM: " + exception.getMessage());
            JOptionPane.showMessageDialog(null,
                    "Não foi possível iniciar o caixa eletrônico:\n" + exception.getMessage(),
                    "FIAP Bank ATM", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }
}
