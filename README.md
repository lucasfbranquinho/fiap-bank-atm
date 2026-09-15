# FIAP Bank ATM — Refactoring para Domain-Driven Design

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Maven](https://img.shields.io/badge/Maven-Multi--módulo-blue?style=for-the-badge&logo=apache-maven)
![SQLite](https://img.shields.io/badge/SQLite-JDBC_Nativo-003B57?style=for-the-badge&logo=sqlite)
![FlatLaf](https://img.shields.io/badge/UI-FlatLaf_Dark-darkgreen?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-DDD-purple?style=for-the-badge)

> **FIAP — Engenharia de Software · Turma 2ESPH**
> **Checkpoint 4 — Domain Driven Design (Java)** · Prof. Eduardo dos Santos Ramos
> Refactoring do Emulador FIAP Bank ATM

---

## 👥 Integrantes do Grupo

| RM | Nome completo |
| :--- | :--- |
| **562262** | Lucas Branquinho |

> Repositório criado a partir do **fork** de [`prof-eduardo-ramos/fiap-bank-atm`](https://github.com/prof-eduardo-ramos/fiap-bank-atm).

---

## 🎯 O que foi feito

O emulador entregue pela empresa parceira funcionava, mas foi reprovado pela Arquitetura Corporativa do banco. Esta entrega **troca o motor da aplicação sem que o usuário final perceba**: a interface Java Swing continua pixel a pixel a mesma, mas por trás dela existe agora uma arquitetura modularizada, persistente e funcional.

| Problema do projeto legado | Solução aplicada |
| :--- | :--- |
| Monólito único: todas as classes no mesmo escopo, com o Swing acessando o domínio diretamente | Quatro submódulos Maven físicos + Composition Root; a apresentação enxerga **apenas** `application` |
| Persistência volátil em RAM (`InMemoryAccountRepository`): tudo se perdia no restart | Persistência real em **SQLite via JDBC nativo**, com DDL, carga inicial e transações |
| Repositórios devolvendo `null` e laços `for` imperativos | `Optional<T>` em todas as buscas + **Streams API** para filtros, ordenações e somas |
| Entidades de domínio trafegando até a tela | **Java Records** (`AccountInfoDTO`, `TransactionDTO`) como único contrato de fronteira |
| Consultas SQL inexistentes / risco de SQL Injection | 100% `PreparedStatement` com injeção de parâmetros via métodos `set*` |

---

## 🏗️ Arquitetura em Módulos

```
fiap-bank-atm  (pom agregador, packaging = pom)
│
├── domain           → entidades, value objects, exceções e contratos de repositório
├── application      → casos de uso, DTOs (Records) e tradução de exceções
├── infrastructure   → JDBC + SQLite (implementação dos contratos do domínio)
├── presentation     → interface gráfica Java Swing (intocada)
└── bootstrap        → Composition Root: injeta as dependências e sobe a aplicação
```

### Grafo de dependências (garantido pelo Maven em tempo de compilação)

```
presentation ──► application ──► domain ◄── infrastructure
                                   ▲              │
                                   └──────────────┘
                              (Inversão de Dependência)

bootstrap ──► presentation + application + infrastructure
```

- `presentation/pom.xml` declara **exclusivamente** `application` (+ FlatLaf). Não existe uma única tag `<dependency>` apontando para `domain` ou `infrastructure` — se alguém tentar importar uma entidade de domínio na tela, **o projeto não compila**.
- `domain` não depende de ninguém: é o centro da arquitetura.
- `infrastructure` implementa as interfaces ditadas pelo `domain` (Inversão de Dependência), e é a única camada que conhece SQL.
- `bootstrap` é o **Composition Root**: o único ponto autorizado a enxergar todas as camadas ao mesmo tempo, justamente para que a apresentação não precise conhecer a infraestrutura para instanciar o repositório.

---

## 🧩 Decisões técnicas por fase

### Fase 1 — Engenharia de Módulos e Setup Maven
- `pom.xml` raiz convertido em **projeto agregador** (`<packaging>pom</packaging>`) com `dependencyManagement` e `pluginManagement` centralizando versões.
- Classes migradas fisicamente para o `src/main/java` de cada submódulo (com `git mv`, preservando o histórico).
- `InMemoryAccountRepository` removido — a persistência volátil deixou de existir.

### Fase 2 — Isolamento da Apresentação e DTOs
- `AccountInfoDTO` e `TransactionDTO` criados como **Java Records** (imutáveis e leves). O PIN nunca é exposto em nenhum contrato.
- `AtmService` passou a devolver apenas DTOs, `BigDecimal`, `Boolean`, `UUID` e `Optional` — nunca mais entidades de domínio.
- As exceções de negócio foram espelhadas em `application.exception`: como a tela não enxerga `domain`, o serviço **traduz** cada exceção do domínio para o equivalente da aplicação (usando *pattern matching for switch* do Java 21).
- A formatação monetária `pt-BR` passou a ser responsabilidade da apresentação; os DTOs trafegam `BigDecimal` puro.

### Fase 3 — Paradigma Funcional e Generics
- `ATMRepository<T extends BaseEntity>` — interface genérica com **restrição de limite superior**, garantindo em tempo de compilação que só entidades legítimas do domínio sejam persistidas.
- `AccountRepository extends ATMRepository<Account>`, acrescentando apenas `findByAccountNumber`.
- **Erradicação do `null`**: toda busca devolve `Optional<T>`; a camada orquestradora trata a presença/ausência com `orElseThrow`, `map`, `flatMap` e `isEmpty`.
- **Streams API** substituindo laços imperativos:
  - `Account.getTotalWithdrawnToday()` → `filter` (tipo + data) + `map` + `reduce`;
  - `AtmMapper.toStatement()` → `sorted` + `limit` + `map` + `toList`;
  - `AccountStatus.fromDatabase()` / `TransactionType.fromDatabase()` → `Arrays.stream` + `findFirst`.

### Fase 4 — Persistência de Dados (JDBC)
- Driver `org.xerial:sqlite-jdbc` declarado **exclusivamente** no `pom.xml` do módulo `infrastructure`.
- `ConnectionFactory` fornece e encerra as conexões (e ativa `PRAGMA foreign_keys = ON`).
- `DatabaseInitializer` cria o schema (DDL) e aplica a carga inicial de forma **idempotente** (`ON CONFLICT DO NOTHING`).
- `AccountRepositoryJdbcImpl` implementa os contratos do domínio, mapeando o `ResultSet` manualmente para as entidades — **sem nenhum ORM** (Hibernate/JPA/Spring Data).
- `save()` e `deleteById()` executam dentro de uma transação (`setAutoCommit(false)` + `commit`/`rollback`).

---

## 🔒 Regras invioláveis — onde conferir

| # | Regra do enunciado | Onde está cumprida |
| :--- | :--- | :--- |
| 1 | Frontend Swing intocável | `presentation/` — `AtmFrame.form` inalterado; em `AtmFrame.java` mudaram apenas `import`s e os tipos trocados por DTOs. Nenhum componente, texto, cor ou fluxo foi alterado |
| 2 | Isolamento físico estrito | `presentation/pom.xml` — depende só de `application`; nenhuma tag apontando para `domain`/`infrastructure` |
| 3 | Proteção contra SQL Injection | `AccountRepositoryJdbcImpl` e `DatabaseInitializer` — 100% `PreparedStatement` + `set*`; zero concatenação de String |
| 4 | Proibição de ORMs | `infrastructure/pom.xml` — apenas `sqlite-jdbc`; mapeamento manual via `ResultSet` |
| 5 | Uso defensivo e erradicação do `null` | `ATMRepository` / `AccountRepository` — todas as buscas devolvem `Optional<T>` |
| 6 | Versionamento e colaboração | Histórico de commits do repositório, organizado por fase do refactoring |

---

## 🗄️ Modelo de Dados

```sql
CREATE TABLE IF NOT EXISTS tb_account (
    id                     VARCHAR(36) PRIMARY KEY,
    agency                 VARCHAR(10) NOT NULL,
    number                 VARCHAR(20) NOT NULL UNIQUE,
    balance                DECIMAL(15, 2) NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    pin                    VARCHAR(4) NOT NULL,
    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
    failed_attempts        INTEGER NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL,
    updated_at             TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_transaction (
    id          VARCHAR(36) PRIMARY KEY,
    account_id  VARCHAR(36) NOT NULL,
    type        VARCHAR(20) NOT NULL,
    amount      DECIMAL(15, 2) NOT NULL,
    description VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id) REFERENCES tb_account(id)
);
```

> **Nota sobre o dicionário de dados.** O anexo 7.2 do enunciado define `tb_account` com `id`, `agency`, `number`, `balance` e `status` — todas mantidas exatamente como especificadas. Porém o emulador exige **PIN de 4 dígitos, limite diário de saque e contador de tentativas** para funcionar como antes; sem essas colunas, o login e o controle de limite do frontend original seriam impossíveis. Por isso o dicionário foi **estendido** (nunca alterado): `pin`, `daily_withdrawal_limit`, `failed_attempts`, `created_at` e `updated_at`. O mesmo vale para `description` em `tb_transaction`, usada no extrato impresso. O total sacado no dia **não é persistido**: ele é derivado do próprio histórico via Streams API.

O arquivo do banco (`fiap-bank-atm.db`) é criado automaticamente no diretório de execução na primeira vez que a aplicação sobe. Para usar outro caminho:
`java -Datm.database.file=C:\caminho\banco.db -jar bootstrap/target/fiap-bank-atm.jar`

---

## 🔑 Contas de Teste (carga inicial)

| Conta | PIN | Saldo | Limite diário | Status | Histórico inicial |
| :---: | :---: | :---: | :---: | :---: | :--- |
| **`12345`** | `1234` | R$ 5.000,00 | R$ 1.500,00 | ACTIVE | Depósito R$ 2.000, Transf. recebida R$ 500, Saque R$ 100 |
| **`67890`** | `5678` | R$ 1.200,00 | R$ 1.000,00 | ACTIVE | Depósito R$ 1.500, Transf. enviada R$ 500 |
| **`99999`** | `9999` | R$ 50,00 | R$ 500,00 | ACTIVE | Depósito R$ 50,00 (abertura de conta) |
| **`11111`** | `1111` | R$ 0,00 | R$ 500,00 | **BLOCKED** | — (conta bloqueada do dicionário de dados) |

---

## 🚀 Como Executar

### Pré-requisitos
- **JDK 21** ou superior (`JAVA_HOME` configurado)
- **Apache Maven 3.8+**

### Opção 1 — JAR executável (recomendado)
```bash
mvn clean package -DskipTests
java -jar bootstrap/target/fiap-bank-atm.jar
```

### Opção 2 — Direto pelo Maven
```bash
mvn clean install -DskipTests
mvn -pl bootstrap exec:java
```

### Opção 3 — Script no Windows
```cmd
run.bat
```

### Opção 4 — IDE (NetBeans / IntelliJ / Eclipse / VS Code)
Abra a pasta raiz (o `pom.xml` agregador), aguarde a sincronização dos módulos e execute a classe `com.fiap.bank.atm.AtmApplication` do módulo **bootstrap**.

---

## 🧪 Testes

```bash
mvn test
```

**13 testes** cobrindo a pilha real (sem mocks, contra um SQLite descartável):

- `AccountRepositoryJdbcImplTest` (infrastructure) — mapeamento de `ResultSet`, `Optional` vazio para conta inexistente, persistência entre conexões, bloqueio persistido, remoção em cascata e **imunidade a SQL Injection** (`' OR '1'='1` é tratado como dado, jamais como instrução).
- `AtmServiceIntegrationTest` (bootstrap) — autenticação devolvendo DTO, `Optional` vazio sem sessão, saque/depósito/transferência persistidos, extrato ordenado do mais recente ao mais antigo, tradução das exceções de negócio e bloqueio após 3 tentativas mantido entre reinícios.

---

## 🛠️ Tecnologias

- **Java 21** (Records, `Optional`, Streams API, pattern matching for switch, text blocks)
- **Apache Maven** multi-módulo
- **SQLite** via `org.xerial:sqlite-jdbc` 3.45.1.0 (JDBC nativo, sem ORM)
- **Swing + FlatLaf 3.5.1** (camada de apresentação preservada)
- **JUnit 5** 5.10.2

---

## 📂 Estrutura de Pastas

```
fiap-bank-atm/
├── pom.xml                                   # Agregador (packaging pom)
├── run.bat
├── domain/
│   └── src/main/java/com/fiap/bank/atm/domain/
│       ├── exception/                        # AccountBlocked, InvalidPin, InsufficientFunds, DailyLimitExceeded
│       ├── model/                            # BaseEntity, Account, Transaction, Money, AccountStatus, TransactionType
│       └── repository/                       # ATMRepository<T extends BaseEntity>, AccountRepository
├── application/
│   └── src/main/java/com/fiap/bank/atm/application/
│       ├── dto/                              # AccountInfoDTO, TransactionDTO (Java Records)
│       ├── exception/                        # Exceções expostas à apresentação
│       ├── mapper/                           # AtmMapper (domínio → DTO)
│       └── service/                          # AtmService
├── infrastructure/
│   └── src/
│       ├── main/java/.../infrastructure/persistence/
│       │   ├── ConnectionFactory.java        # Fábrica de conexões JDBC
│       │   ├── DatabaseInitializer.java      # DDL + carga inicial
│       │   ├── AccountRepositoryJdbcImpl.java
│       │   └── DatabaseException.java
│       └── test/java/...                     # Testes de integração JDBC
├── presentation/
│   └── src/main/java/com/fiap/bank/atm/presentation/
│       ├── AtmFrame.java                     # Tela Swing (layout e fluxo preservados)
│       ├── AtmFrame.form
│       └── ScreenState.java
└── bootstrap/
    └── src/
        ├── main/java/com/fiap/bank/atm/AtmApplication.java   # Composition Root
        └── test/java/...                                     # Testes end-to-end
```
