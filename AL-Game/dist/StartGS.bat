@echo off
chcp 65001 >nul
TITLE Servidor do Jogo
SET PATH="COLOQUE O CAMINHO DO SEU JAVA 1.8 AQUI"
@COLOR 0A

:START
CLS

echo.

echo Inicializando Servidor do Jogo.

echo.
REM -------------------------------------
REM Default parameters for a basic server.
java -Dfile.encoding=UTF-8 -Xms2048m -Xmx8192m -XX:MaxHeapSize=8192m -Xdebug -XX:MaxNewSize=48m -XX:NewSize=48m -XX:+UseParNewGC -XX:+CMSParallelRemarkEnabled -XX:+UseConcMarkSweepGC -ea:./lib/AL-Commons.jar -cp ./lib/*;./lib/AL-Game.jar com.aionemu.gameserver.GameServer
REM -------------------------------------
SET CLASSPATH=%OLDCLASSPATH%

if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
if ERRORLEVEL 0 goto end

REM Restart...
:restart
echo.
echo Reiniciando...
echo.
goto start

REM Error...
:error
echo.
echo Servidor do Jogo terminou de forma brusca...
echo.
goto end

REM End...
:end
echo.
echo Servidor do Jogo terminou...
echo.
pause
