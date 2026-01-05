
echo "Inicializando o servidor de LOGIN."

java -Xms64m -Xmx128m -ea -Xbootclasspath/p:./lib/jsr166-1.7.0.jar -cp ./lib/*:AL-Login.jar com.aionemu.loginserver.LoginServer

if [ $? -eq 2 ]; then
echo "Reiniciando..."
bash $0
elif [ $? -eq 1 ]; then
echo "O servidor parou de forma brusca..."
else
echo "O Servidor parou..."
fi
