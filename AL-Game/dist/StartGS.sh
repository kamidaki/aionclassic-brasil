#!/bin/bash

export LANG=pt_BR.UTF-8
export LC_ALL=pt_BR.UTF-8

echo "Inicializando Servidor do Jogo."

java -Dfile.encoding=UTF-8 -Xmx8g -ea -cp ./lib/*:./lib/AL-Game.jar com.aionemu.gameserver.GameServer

if [ $? -eq 2 ]; then
echo "Reiniciando..."
bash $0
elif [ $? -eq 1 ]; then
echo "Servidor do Jogo terminou de forma brusca..."
else
echo "Servidor do Jogo terminou..."
fi
