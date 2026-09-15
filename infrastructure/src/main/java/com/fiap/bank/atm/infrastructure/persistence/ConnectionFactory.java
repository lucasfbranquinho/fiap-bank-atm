package com.fiap.bank.atm.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexões com o banco de dados SQLite.
 *
 * <p>
 * Centraliza a criação e o encerramento das conexões JDBC, evitando que as
 * demais classes da infraestrutura conheçam a URL do banco ou repitam o
 * tratamento de {@code SQLException}.
 *
 * <p>
 * O caminho do arquivo pode ser sobrescrito pela propriedade de sistema
 * {@code -Datm.database.file=/caminho/do/banco.db}, o que permite, por exemplo,
 * que os testes usem uma base descartável.
 */
public class ConnectionFactory {

    /** Propriedade de sistema que sobrescreve o arquivo do banco. */
    public static final String DATABASE_FILE_PROPERTY = "atm.database.file";

    /** Arquivo padrão criado no diretório de execução da aplicação. */
    public static final String DEFAULT_DATABASE_FILE = "fiap-bank-atm.db";

    private final String jdbcUrl;

    public ConnectionFactory() {
        this(System.getProperty(DATABASE_FILE_PROPERTY, DEFAULT_DATABASE_FILE));
    }

    public ConnectionFactory(String databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * Fornece uma nova conexão já com a checagem de chaves estrangeiras ativada
     * (no SQLite o suporte a FOREIGN KEY é desligado por padrão).
     *
     * @return conexão aberta com o banco de dados
     * @throws DatabaseException quando não for possível conectar
     */
    public Connection getConnection() {
        try {
            Connection connection = DriverManager.getConnection(jdbcUrl);
            try (var statement = connection.prepareStatement("PRAGMA foreign_keys = ON")) {
                statement.execute();
            }
            return connection;
        } catch (SQLException exception) {
            throw new DatabaseException("Não foi possível conectar ao banco de dados: " + jdbcUrl, exception);
        }
    }

    /**
     * Encerra a conexão informada de forma segura, ignorando conexões nulas ou já
     * fechadas.
     *
     * @param connection conexão a ser encerrada
     */
    public void close(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            throw new DatabaseException("Falha ao encerrar a conexão com o banco de dados.", exception);
        }
    }
}
