@echo off
chcp 65001 > nul
echo ====================================================
echo        FIAP BANK - EMULADOR DE CAIXA ELETRÔNICO
echo ====================================================
echo.
echo Compilando os módulos (domain, application, infrastructure,
echo presentation e bootstrap) e iniciando a aplicação...
echo.

set MVN_PATH="C:\Program Files\Apache NetBeans\java\maven\bin\mvn.cmd"

if exist %MVN_PATH% (
    call %MVN_PATH% clean package -DskipTests
) else (
    where mvn >nul 2>nul
    if %errorlevel% equ 0 (
        call mvn clean package -DskipTests
    ) else (
        echo [ERRO] Maven não encontrado. Por favor, abra este projeto
        echo na sua IDE e execute a classe com.fiap.bank.atm.AtmApplication
        echo do módulo bootstrap, ou instale o Maven e adicione-o ao PATH.
        pause
        exit /b 1
    )
)

if not exist "bootstrap\target\fiap-bank-atm.jar" (
    echo [ERRO] A compilação falhou. Verifique as mensagens acima.
    pause
    exit /b 1
)

echo.
echo Iniciando o caixa eletrônico...
java -jar bootstrap\target\fiap-bank-atm.jar
