# Aion Classic 2.4 ~ 2.8 (EU)

<div align="center">

[![Discord](https://img.shields.io/badge/Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/RbXr9cuACR)
[![Versão](https://img.shields.io/badge/Version-2.4~2.8%20-blue)](http://aionclassicbrasil.com/)
[![Java](https://img.shields.io/badge/Java-8+-orange)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Licença](https://img.shields.io/badge/License-GPL%20v3-green)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Construção_Ativa-brightgreen)](https://github.com/kamidaki/aionclassic-brasil)

<br>

<strong>O emulador mais completo e tecnicamente avançado para Aion Classic 2.4 ~ 2.8</strong>

</div>

---

## Visão Geral

Este repositório contém um **emulador avançado** para o MMORPG **Aion: Classic (EU) versões 2.4 até 2.8**.  
O objetivo do projeto é preservar a experiência clássica original, mantendo fidelidade ao conteúdo classic, ao mesmo tempo em que evolui a base técnica com correções, otimizações e sistemas personalizados.

---

## Aviso Importante

> **ATENÇÃO**  
>  
> A base estrutural deste emulador **não foi criada do zero por esta equipe**.  
> Ela deriva do projeto **Aion Lightning**, originalmente desenvolvido com foco em versões **Retail**.  
>
> Como consequência:
>
> - Centenas de classes ainda **necessitam correções profundas**
> - O projeto **não está em estado jogável**
> - O processo de correção pode levar **anos**
>
> **Não oferecemos suporte, ajuda ou troubleshooting** para quem apenas clonar o repositório.

---

## Participação no Desenvolvimento

> Este projeto é mantido por uma equipe reduzida, com tempo limitado.

- As correções são feitas de forma **esporádica**
- Não há prazos definidos
- Em alguns períodos, alterações podem ocorrer apenas **uma vez por mês**

Para participar do desenvolvimento, é **obrigatório** estar no Discord oficial do **Aion Live Clássico**.

---

## Arquivos Essenciais (.GEO e .NAV)

### Download

https://drive.google.com/drive/folders/1cnowlvXpMKdeCnXYtZ1nXpEGEyM-PIK9

### Instalação

Copie as pastas `geo` e `nav` para:

```

AL_Game/data/

```

### Estrutura esperada

```

AL_Game/data/
├─ geo
├─ nav
├─ packets
├─ scripts
├─ static_data
└─ ...

```

---

## Client Aion Classic (EU)

### Client 2.4

**Download:**  

https://drive.google.com/drive/folders/1cnowlvXpMKdeCnXYtZ1nXpEGEyM-PIK9


**Observação:**  
Esta versão **não possui a classe MONK**.

---

### Client 2.8

**Status:**  
Versão estável **ainda não definida**.

**Observação:**  
Esta versão **possui a classe MONK**.

---

## Executáveis e Modos de Uso

### Modo Offline (127.0.0.1)

Utilizado exclusivamente para testes locais:

```bat
start "" /affinity 7FFFFFFF "bin64\aionclassic.bin" -ip:127.0.0.1 -port:2106 -cc:2 -lang:ENG
````
Nota: Não utilize shugo console durante execuções de teste, isso afetará a análise precisa da qualidade do client.

---

### Development Server ALC

* Conecta diretamente ao **servidor de testes interno**
* Ambiente **não destinado a jogadores**
* Usado apenas para **validação e correção de bugs**

> O acesso a este executável é restrito a membros do Discord oficial.

---

## Como Contribuir (Não Membros da Equipe)

1. Faça um **Fork** do repositório
2. Crie uma branch para sua alteração

   ```bash
   git checkout -b feature/nova-feature
   ```
3. Realize o commit

   ```bash
   git commit -m "Adiciona nova feature"
   ```
4. Envie para o seu fork

   ```bash
   git push origin feature/nova-feature
   ```
5. Abra um **Pull Request**

---

## Canais Oficiais

<div align="center">

[![Discord](https://img.shields.io/badge/Discord-7289DA?style=for-the-badge\&logo=discord\&logoColor=white)](https://discord.gg/RbXr9cuACR)
[![Email](https://img.shields.io/badge/Email-D14836?style=for-the-badge\&logo=gmail\&logoColor=white)](mailto:oldclassaion@gmail.com)
[![Website](https://img.shields.io/badge/Website-000000?style=for-the-badge\&logo=About.me\&logoColor=white)](http://aionclassicbrasil.com)

</div>

<div align="center">

<strong>Aion Live Clássico — Desenvolvimento independente e contínuo</strong>


</div>
