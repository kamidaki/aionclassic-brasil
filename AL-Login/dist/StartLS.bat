@ECHO off
TITLE Servidor de LOGIN
SET PATH="COLOQUE O CAMINHO DO SEU JAVA 1.8 AQUI"
@COLOR 0C

:START
CLS

echo.

echo Inicializando o servidor de LOGIN.

echo.

REM -------------------------------------
REM Configuração padrão para servidores básicos
java -cp ./lib/*;AL-Login.jar com.aionemu.loginserver.LoginServer
REM -------------------------------------
SET CLASSPATH=%OLDCLASSPATH%

if ERRORLEVEL 2 goto restart
IF ERRORLEVEL 1 GOTO ERROR
IF ERRORLEVEL 0 GOTO END

REM Restart...
:restart
echo.
echo Reiniciando ...
echo.
goto start

REM Error...
:error
echo.
echo O servidor parou de forma brusca...
echo.
goto end

REM End...
:end
echo.
echo O Servidor parou...
echo.
pause
