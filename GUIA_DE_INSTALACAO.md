# Guia Completo de Instalação e Operação — Integração Denon AVR no Hubitat Elevation

> **Público-alvo:** Integradores de automação residencial, instaladores de áudio/vídeo e entusiastas.  
> **Versão do Driver:** v1.0.1+  
> **Desenvolvido por:** TecnoSimples Tecnologia LTDA  
> **Protocolo:** Denon Control Protocol (DCP) via Telnet TCP porta 23 (100% Local / Sem Nuvem)

---

## 1. Visão Geral da Solução

O ecossistema **TecnoSimples Denon AVR para Hubitat Elevation** foi arquitetado para oferecer controle profissional, robusto e instantâneo de Receivers Denon e Marantz na rede local.

### Diferenciais Técnicos
- **Comunicação 100% Local (TCP porta 23):** Sem dependência de nuvem, sem latência de internet e sem necessidade de tokens de autenticação externos.
- **Arquitetura Parent/Child:** Um único driver Pai (**Denon AVR Master**) mantém o socket de rede persistente com reconexão automática e cria de forma nativa e automática os dispositivos filhos (**Zone 2** e **Zone 3**) com controle independente.
- **Feedback Bidirecional em Tempo Real:** Se o cliente ligar o receiver pelo controle remoto infravermelho ou girar o botão de volume no painel frontal, o status no Hubitat e nos dashboards é atualizado imediatamente.
- **Segurança de Áudio:** Limite máximo de volume configurável para proteger alto-falantes e a audição dos moradores contra comandos acidentais de automação.

### Equipamentos Compatíveis
Todos os receivers das linhas **Denon AVR-X**, **Denon AVR-S** e **Marantz SR/NR/Cinema** que possuam porta de rede (Ethernet ou Wi-Fi) e suporte ao protocolo serial/IP DCP clássico (porta 23).  
*Homologado em bancada com hardware real Denon AVR-X4400H (9.2 canais / 3 Zonas).*

---

## 2. Preparação do Receiver Denon (Passo a Passo no Aparelho)

Antes de adicionar o driver no Hubitat, o receiver precisa de 3 configurações essenciais feitas pelo controle remoto na tela da TV (On-Screen Display - OSD) ou no display frontal.

### Passo 2.1 — Conexão de Rede e IP Fixo (Obrigatório)
1. Conecte o receiver à rede local (preferencialmente via cabo Ethernet RJ-45).
2. No controle do Denon, pressione o botão **SETUP**.
3. Navegue até: **Network** > **Information** e anote o **IP Address** do receiver (ex: `10.0.1.154`).
4. **Recomendação Profissional:** No roteador do cliente, crie uma **reserva de DHCP** para o endereço MAC do receiver, garantindo que o IP nunca mude após quedas de energia.

### Passo 2.2 — Ativar o Network Control / IP Control (Crítico)
Sem este ajuste, a placa de rede do receiver é desligada quando o aparelho entra em standby, impedindo o Hubitat de ligá-lo via rede!

1. No menu de Setup do Denon, acesse:  
   `Setup` > `Network` > `Network Control` (em alguns modelos: `IP Control`).
2. Mude a opção de **Off in Standby** para **Always On** (ou **Always On / On**).

### Passo 2.3 — Compreender a Escala de Volume (Scale)
No menu: `Setup` > `Audio` > `Volume` > `Scale`, o Denon oferece duas opções de exibição:
- **0 – 98 (Absoluto / Direto):** O visor do aparelho mostra números de 0 a 98 (onde 80 é o volume de referência).
- **-79.5 dB – 18.0 dB (Relativo em dB):** O visor mostra valores negativos de atenuação (ex: -24.0 dB).

> **Nota do Integrador:** O driver da TecnoSimples suporta ambas as formas de entrada de maneira inteligente! Se o integrador enviar `56`, o driver envia volume direto 56. Se enviar `-24`, o driver calcula a atenuação e envia exatamente o mesmo volume correspondente a -24.0 dB (`80 - 24 = 56`).

---

## 3. Instalação no Hubitat Elevation

Existem dois caminhos para instalar os drivers: pelo **Hubitat Package Manager (HPM)** (recomendado para facilitar atualizações futuras) ou manualmente via **Drivers Code**.

---

### Método A: Via Hubitat Package Manager — HPM (Recomendado)

1. No navegador, acesse o painel do seu Hubitat Elevation (`http://<IP-DO-HUB>`).
2. No menu lateral esquerdo, clique em **Apps** e abra o **Hubitat Package Manager**.
3. Selecione a opção **Install** e depois clique em **From a URL**.
4. No campo **URL**, cole exatamente o link do manifesto oficial:
   ```text
   https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/packageManifest.json
   ```
5. Clique em **Next**. O HPM identificará o pacote:  
   **Denon AVR — Integração Hubitat** (autor: TecnoSimples Tecnologia LTDA).
6. Confirme a instalação. O HPM instalará automaticamente os 2 drivers necessários:
   - `Denon AVR Master` (Driver Pai)
   - `Denon AVR Zone Child` (Driver Filho)
7. Clique em **Next** até a tela de conclusão (**Done**).

*(Dica: Quando houver novas atualizações de firmware ou melhorias, bastará clicar em **Update** no HPM).*

---

### Método B: Instalação Manual (Alternativa sem HPM)

Caso não utilize o HPM na central:
1. No menu lateral do Hubitat, vá em **Developer Tools** > **Drivers Code**.
2. Clique no botão superior direito **+ New Driver**.
3. Clique em **Import** e cole a URL do driver Filho:
   ```text
   https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/drivers/Denon_AVR_Zone.groovy
   ```
4. Clique em **Import**, depois em **Save**.
5. Repita o processo para o driver Pai: clique em **+ New Driver** > **Import** e cole a URL:
   ```text
   https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/drivers/Denon_AVR_Master.groovy
   ```
6. Clique em **Import** e depois em **Save**.

---

## 4. Criação e Configuração do Dispositivo

Com os drivers gravados no hub, vamos criar o dispositivo mestre do receiver.

### Passo 4.1 — Criar o Dispositivo Mestre
1. No menu lateral do Hubitat, clique em **Devices**.
2. Clique no botão superior direito **+ Add Device** e selecione **Virtual**.
3. Preencha os campos iniciais:
   - **Device Name:** `Denon Sala de Estar` (ou o nome do ambiente).
   - **Device Network Id (DNI):** Um identificador único, por exemplo: `DENON-MAIN-01`.
   - **Type:** Procure na lista e selecione **`Denon AVR Master`** (fica ao final da lista, sob o namespace `TecnoSimples`).
4. Clique em **Save Device**.

---

### Passo 4.2 — Configurar as Preferências (Preferences)
Após salvar o dispositivo, a tela de detalhes será exibida. Role até a seção **Preferences**:

| Campo | Tipo | Valor Padrão | Descrição / Orientação Prática |
|---|---|---|---|
| **Endereço IP do Denon AVR** | Texto | `10.0.1.154` | Insira o IP exato do receiver na rede local anotado na Etapa 2.1. |
| **Habilitar Zone 2** | Switch | `Ativado (True)` | Se o receiver possui Zona 2 em uso, mantenha ativado. O Hubitat criará automaticamente o filho. |
| **Habilitar Zone 3** | Switch | `Ativado (True)` | Ative apenas se o modelo possuir e utilizar Zona 3 (ex: modelos de 9 ou 11 canais). |
| **Limite de Volume Máximo Seguro (0-98)** | Número | `80` | **Teto de segurança:** `80` corresponde a 0.0 dB (volume de referência THX). Evita que automações acidentais danifiquem caixas acústicas. |
| **Mapeamento de Entradas** | Texto | *(ver lista abaixo)* | Nomes amigáveis para as entradas físicas do receiver. Formato: `Nome No Hubitat:CODIGO_DENON`. |
| **Habilitar Log de Debug** | Switch | `Ativado (True)` | Deixe ligado nos primeiros dias para validação de bancada; desligue após homologação. |
| **Habilitar Log de Eventos** | Switch | `Ativado (True)` | Registra no Log do Hubitat mudanças de volume, entrada e liga/desliga. |

#### Mapeamento Padrão de Entradas
O campo já vem pré-configurado de fábrica com os códigos padrão Denon:
```text
TV:TV, Apple TV:MPLAY, Blu-ray:BD, Game:GAME, Cabo:SAT/CBL, Música:NET, Bluetooth:BT, Auxiliar:AUX1, CD:CD, Phono:PHONO, Tuner:TUNER
```
*Se na casa do cliente a entrada MPLAY for um Chromecast, basta alterar para: `Chromecast:MPLAY`.*

5. Clique no botão **Save Preferences**.

---

### Passo 4.3 — Sincronização Automática dos Dispositivos Filhos
Assim que você clica em *Save Preferences*:
1. O driver Master conecta imediatamente à porta 23 do Denon via Telnet.
2. O atributo `networkStatus` no canto superior direito muda para **online**.
3. O driver cria automaticamente na árvore de dispositivos os filhos:
   - `Denon Sala de Estar - Zone 2` (se ativada)
   - `Denon Sala de Estar - Zone 3` (se ativada)

---

## 5. Operação e Guia de Comandos

### 5.1 Main Zone (Dispositivo Principal)

| Comando | Parâmetro | Como Usar / O que faz |
|---|---|---|
| **on()** | — | Liga a Zona Principal (`ZMON`). |
| **off()** | — | Desliga a Zona Principal (`ZMOFF`). |
| **allZonesOff()** | — | Desliga o receiver por completo (`PWSTANDBY`), desligando Main Zone, Zone 2 e Zone 3 simultaneamente. Ideal para cenas "Sair de Casa". |
| **setVolume()** | `volume*` | Ajusta o volume. Aceita dois formatos:<br>• **Escala Direta (0 a 100):** digite `56` para ir a 56.<br>• **Escala em dB (-80 a 0):** digite `-24` para ir a -24.0 dB (que corresponde ao nível 56). |
| **volumeUp()** | — | Aumenta o volume em 1 passo (+1.0 dB / +1 nível). |
| **volumeDown()** | — | Diminui o volume em 1 passo (-1.0 dB / -1 nível). |
| **mute()** / **unmute()** | — | Muta ou desmuta o áudio da Main Zone. No visor frontal do Denon a palavra **MUTE** piscará em destaque. |
| **setInputSource()** | `sourceName*` | Seleciona a entrada pelo nome amigável (ex: `Apple TV`, `TV`, `Game`, `Cabo`). |
| **setSoundMode()** | `soundMode*` | Altera o processamento surround (ex: `MOVIE`, `MUSIC`, `STEREO`, `DIRECT`, `PURE DIRECT`). |
| **refresh()** | — | Consulta o status completo do receiver e sincroniza todos os atributos no Hubitat. |

---

### 5.2 Zonas Secundárias (Zone 2 e Zone 3)

Cada zona secundária opera como um dispositivo filho com a capability nativa `AudioVolume`, `Switch` e `MediaInputSource`.

#### Regras de Ouro das Zonas Secundárias (Gotchas de Hardware)
1. **Ligar antes de Mutar:** O circuito interno do receiver Denon **descarta comandos de Mute** se a zona estiver desligada (em standby). Para mutar a Zone 2 em uma automação, envie primeiro o comando `on()` e depois o `mute()`.
2. **Comportamento do LCD Frontal:** O painel LCD frontal do Denon é dedicado à **Main Zone**. Quando você muta ou troca a entrada da Zone 2:
   - O corte de som nas caixas da Zona 2 é **imediato**;
   - O status no Hubitat muda para `muted`;
   - O visor frontal do aparelho **não** exibe a palavra "MUTE" para não incomodar quem está assistindo à TV na Zona Principal (a menos que o usuário pressione o botão físico *Zone 2* no painel do aparelho).
3. **Entrada "Source (Main)":** Na Zona 2 ou 3, além das entradas normais, você pode selecionar a fonte `Source (Main)` para espelhar exatamente o mesmo áudio que está tocando na sala principal.

---

## 6. Exemplos de Automação no Hubitat Elevation

### Cenário 1: "Cena Cinema / Assistir Filme" (Rule Machine)
- **Gatilho:** Botão do Keypad ou comando de voz "Assistir Filme".
- **Ações:**
  1. `Denon Sala de Estar`: `on()`
  2. `Denon Sala de Estar`: `setInputSource("Apple TV")`
  3. `Denon Sala de Estar`: `setSoundMode("MOVIE")`
  4. `Denon Sala de Estar`: `setVolume(50)` (nível moderado de partida)
  5. Iluminação: Reduzir luzes para 15% e fechar cortinas.

---

### Cenário 2: "Cena Festa / Espelhamento de Áudio"
- **Gatilho:** Ativação do modo Festa.
- **Ações:**
  1. `Denon Sala de Estar`: `on()`
  2. `Denon Sala de Estar`: `setInputSource("Música")` (ou Bluetooth/Spotify)
  3. `Denon Sala de Estar - Zone 2`: `on()`
  4. `Denon Sala de Estar - Zone 2`: `setInputSource("Source (Main)")`
  5. `Denon Sala de Estar - Zone 2`: `setVolume(45)`

---

### Cenário 3: "Sair de Casa / Boa Noite"
- **Gatilho:** Alarme armado para Modo Ausente ou botão "Tudo Desliga".
- **Ações:**
  1. `Denon Sala de Estar`: `allZonesOff()` *(garante que nenhuma zona externa fique ligada gastando energia)*.

---

## 7. Diagnóstico e Resolução de Problemas (Troubleshooting)

### Problema 1: O atributo `networkStatus` fica em "connecting" ou "error"
- **Causa A (IP incorreto):** Verifique se o IP digitado nas Preferences bate exatamente com o IP atual do Denon.
- **Causa B (Network Control desligado):** No menu do Denon, certifique-se de que `Setup > Network > Network Control` está em **Always On**.
- **Causa C (Limite de sessões Telnet excedido):** O protocolo DCP da Denon aceita somente **uma conexão simultânea na porta 23**. Se outro sistema (ex: Control4, Savant, Home Assistant ou um terminal do computador) estiver conectado à porta 23 do Denon, o Hubitat terá a conexão recusada. Feche as outras conexões e clique em **reconnect** no driver.

### Problema 2: O volume no Hubitat não bate com o display do Denon
- **Causa:** O visor do seu Denon pode estar configurado em **0–98** em vez de **dB**.
- **Solução:** Envie o valor positivo direto no Hubitat (ex: digite `56` para ir a 56). Se preferir que o visor do Denon exiba em dB, altere no menu do receiver em `Setup > Audio > Volume > Scale` para `-79.5 dB - 18.0 dB`.

### Problema 3: O comando de Mute na Zona 2 não parece fazer nada
- **Verificação 1:** A Zona 2 está ligada? Verifique se o atributo `switch` da Zone 2 está `on`. O Denon ignora comandos de mudo em zonas em standby.
- **Verificação 2:** Você está olhando o visor frontal do Denon? Lembre-se de que o visor físico frontal mostra apenas o status da Main Zone. Confira se o atributo `mute` no Hubitat mudou para `muted` e se o som nas caixas da Zona 2 foi cortado.

---

## 8. Tabela de Referência Rápida (Códigos DCP Comuns)

| Função | Código DCP Bruto | Observações |
|---|---|---|
| Master Power ON / Standby | `PWON` / `PWSTANDBY` | `PWSTANDBY` desliga todas as zonas |
| Main Zone ON / OFF | `ZMON` / `ZMOFF` | Liga/Desliga apenas a sala principal |
| Main Zone Volume | `MV<raw>` (ex: `MV56`) | 00 a 98 (80 = 0 dB referência) |
| Main Zone Mute ON / OFF | `MUON` / `MUOFF` | Pisca "MUTE" no visor frontal |
| Zone 2 ON / OFF | `Z2ON` / `Z2OFF` | Liga/Desliga canal da Zona 2 |
| Zone 2 Volume | `Z2<raw>` (ex: `Z240`) | 00 a 98 |
| Zone 2 Mute ON / OFF | `Z2MUON` / `Z2MUOFF` | Requer que Z2 esteja ON |
| Zone 3 ON / OFF | `Z3ON` / `Z3OFF` | Liga/Desliga canal da Zona 3 |
| Zone 3 Volume | `Z3<raw>` (ex: `Z345`) | 00 a 98 |
| Zone 3 Mute ON / OFF | `Z3MUON` / `Z3MUOFF` | Requer que Z3 esteja ON |
| Consulta Geral de Status | `PW?`, `ZM?`, `MV?`, `MU?`, `SI?`, `Z2?`, `Z3?` | Executado automaticamente pelo `refresh()` |

---

## 9. Suporte e Contato

- **Repositório de Distribuição (HPM):** [https://github.com/tecnosimples/hubitat_denon](https://github.com/tecnosimples/hubitat_denon)
- **Portal TecnoSimples:** [https://tecnosimples.com.br](https://tecnosimples.com.br)
- **Desenvolvido por:** TecnoSimples Tecnologia LTDA — Automação e Engenharia Residencial de Alto Padrão.
