@ECHO off
chcp 65001 >nul
TITLE Servidor de Bate-Papo
@COLOR 0B
SET PATH="COLOQUE O CAMINHO DO SEU JAVA 1.8 AQUI"
:START
CLS

ECHO Inicializando o Servidor de Bate-Papo
java -Dfile.encoding=UTF-8 -cp ./libs/*;AL-Chat.jar com.aionemu.chatserver.ChatServer
SET CLASSPATH=%OLDCLASSPATH%
IF ERRORLEVEL 2 GOTO START
IF ERRORLEVEL 1 GOTO ERROR
IF ERRORLEVEL 0 GOTO END
:ERROR
ECHO.
ECHO Servidor de Bate-Papo terminou de forma brusca!
ECHO.
PAUSE
EXIT
:END
ECHO.
ECHO Servidor de Bate-Papo terminou!
ECHO.
PAUSE
EXIT
