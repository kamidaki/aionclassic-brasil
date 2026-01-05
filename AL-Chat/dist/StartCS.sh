#!/bin/bash

export LANG=pt_BR.UTF-8
export LC_ALL=pt_BR.UTF-8

echo "Inicializando o Servidor de Bate-Papo"

java -Dfile.encoding=UTF-8 -Xms512m -Xmx1024m -ea -Xbootclasspath/p:./libs/jsr166.jar -cp ./libs/*:AL-Chat.jar com.aionemu.chatserver.ChatServer

if [ $? -eq 2 ]; then
echo "Reiniciando..."
bash $0
elif [ $? -eq 1 ]; then
echo "Servidor de Bate-Papo terminou de forma brusca..."
else
echo "Servidor de Bate-Papo terminou..."
fi
