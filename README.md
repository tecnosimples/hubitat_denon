# Denon AVR — Integração Hubitat Elevation

Pacote oficial TecnoSimples para controle local via rede (Telnet TCP:23) de Receivers **Denon / Marantz** compatíveis com o protocolo DCP (Denon Control Protocol).

📖 **Guia Passo a Passo Completo:** Consulte o [GUIA_DE_INSTALACAO.md](GUIA_DE_INSTALACAO.md) para o roteiro detalhado com preparação física do receiver, configuração de rede, particularidades de zonas e exemplos práticos de automação.

## 🚀 Instalação via Hubitat Package Manager (HPM)

1. No Hubitat Elevation, abra o app **Hubitat Package Manager (HPM)**.
2. Selecione **Install** > **From a URL**.
3. Cole a URL do manifesto:
   ```
   https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/packageManifest.json
   ```
4. Conclua a instalação. O HPM instalará o driver Pai (`Denon AVR Master`) e o driver Filho (`Denon AVR Zone Child`).

*(Se você já tinha instalado manualmente os drivers pelo Drivers Code, no HPM escolha **Match Up** > **From a URL** com o mesmo link para vinculá-los às atualizações automáticas).*

---

## ⚙️ Configuração do Dispositivo

1. No Hubitat, vá em **Devices** > **Add Device** > **Virtual**.
2. Preencha o nome (ex: `Denon Sala Principal`) e selecione o Type **`Denon AVR Master`** (namespace `TecnoSimples`).
3. Nas Preferences do dispositivo criado:
   - **IP:** Digite o endereço IP do receiver na rede local (ex: `10.0.1.154`).
   - **Zone 2 / Zone 3:** Habilite se o seu modelo suportar múltiplas zonas.
   - **Limite de Volume Seguro (dB):** Defina o teto máximo permitido (padrão `0.0 dB`).
   - **Mapeamento de Entradas:** Personalize os nomes amigáveis das entradas se desejar.
4. Clique em **Save Preferences**.
5. O driver conectará imediatamente via Telnet e criará os dispositivos filhos (`Denon Sala Principal - Zone 2` e `Zone 3`) na lista de dispositivos.

---

## 🎧 Recursos Suportados

- **Transporte Telnet Persistente (TCP :23):** Reconexão automática resiliente com backoff exponencial.
- **Controle de 3 Zonas:** Main Zone, Zone 2 e Zone 3 com dispositivos independentes no Hubitat.
- **Volume em dB:** Padronizado na escala nativa do Denon (-80 a +18 dB).
- **Mute / Unmute:** Suporte completo à capability `AudioVolume`.
- **Entradas e Modos de Som:** Seleção de fontes e modos surround (Movie, Music, Stereo, Direct, etc.).
- **Feedback Bidirecional Real-time:** Alterações feitas no painel físico ou controle remoto atualizam instantaneamente o Hubitat.

---

## 📄 Licença

MIT License — Copyright (c) 2026 TecnoSimples Tecnologia LTDA.
