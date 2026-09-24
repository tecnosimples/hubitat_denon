/**
 * =======================================================================================
 *  Denon AVR Master (Parent Driver)
 *  
 *  Plataforma: Hubitat Elevation
 *  Equipamento Alvo: Denon AVR-X4400H (e compatíveis com Denon Control Protocol / DCP)
 *  Transporte: TCP Porta 23 (Telnet), framing ASCII + CR (0x0D)
 *
 *  Copyright 2026 TecnoSimples
 *  Licença: MIT
 * =======================================================================================
 */

metadata {
    definition(
        name: "Denon AVR Master",
        namespace: "TecnoSimples",
        author: "TecnoSimples",
        importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/drivers/Denon_AVR_Master.groovy"
    ) {
        capability "Initialize"
        capability "Refresh"
        capability "Switch"
        capability "AudioVolume"
        capability "MediaInputSource"
        capability "Actuator"

        // Comandos customizados do Main Zone e Sistema
        command "setVolume", [[name: "volume*", type: "NUMBER", description: "Volume direto 0 a 100 (ex: 56) OU em dB negativo (ex: -24 para -24 dB)"]]
        command "sendRawCommand", [[name: "command*", type: "STRING", description: "Comando bruto DCP (ex: PWON, ZMON, MVUP)"]]
        command "reconnect"
        command "disconnect"
        command "setSoundMode", [[name: "soundMode*", type: "STRING", description: "Modo surround (ex: MOVIE, MUSIC, STEREO, DIRECT)"]]
        command "allZonesOff"

        // Atributos de estado
        attribute "networkStatus", "string"
        attribute "soundMode", "string"
        attribute "rawVolume", "string"
        attribute "volumeDb", "string"
    }

    preferences {
        input name: "deviceIp", type: "text", title: "Endereço IP do Denon AVR", description: "Ex: 10.0.1.154", defaultValue: "10.0.1.154", required: true
        input name: "enableZone2", type: "bool", title: "Habilitar Zone 2", description: "Cria dispositivo filho para Zone 2", defaultValue: true
        input name: "enableZone3", type: "bool", title: "Habilitar Zone 3", description: "Cria dispositivo filho para Zone 3", defaultValue: true
        input name: "maxVolumeLimit", type: "number", title: "Limite de Volume Máximo Seguro (0-98)", description: "Teto de segurança raw (80 = 0.0 dB referência; 90 = limite de fábrica)", range: "30..98", defaultValue: 80, required: true
        input name: "customInputs", type: "text", title: "Mapeamento de Entradas (Nome Amigável:Código Denon)", description: "Separe por vírgulas. Formato: Label:CODIGO", defaultValue: "TV:TV, Apple TV:MPLAY, Blu-ray:BD, Game:GAME, Cabo:SAT/CBL, Música:NET, Bluetooth:BT, Auxiliar:AUX1, CD:CD, Phono:PHONO, Tuner:TUNER", required: true
        input name: "logEnable", type: "bool", title: "Habilitar Log de Debug", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Habilitar Log de Descrição/Eventos", defaultValue: true
    }
}

// ---------------------------------------------------------------------------------------
// Ciclo de Vida do Driver
// ---------------------------------------------------------------------------------------

def installed() {
    logInfo "Instalando Denon AVR Master..."
    initialize()
}

def updated() {
    logInfo "Atualizando configurações do Denon AVR Master..."
    unschedule()
    updateSupportedInputs()
    syncChildDevices()
    initialize()
}

def initialize() {
    state.reconnectDelay = 5
    disconnect()
    connect()
    runIn(2, "refresh")
}

// ---------------------------------------------------------------------------------------
// Gerenciamento de Conexão Telnet (TCP :23)
// ---------------------------------------------------------------------------------------

def connect() {
    if (!settings.deviceIp) {
        logWarn "Endereço IP não configurado!"
        sendEvent(name: "networkStatus", value: "error", descriptionText: "IP não configurado")
        return
    }
    
    logInfo "Conectando ao Denon AVR em ${settings.deviceIp}:23 via Telnet..."
    sendEvent(name: "networkStatus", value: "connecting")
    try {
        telnetConnect([termChars:[13]], settings.deviceIp, 23, null, null)
    } catch (Exception e) {
        logWarn "Falha ao iniciar telnetConnect: ${e.message}"
        handleConnectionFailure()
    }
}

def disconnect() {
    logDebug "Fechando conexão Telnet..."
    try {
        telnetClose()
    } catch (Exception e) {
        logDebug "telnetClose ignorado: ${e.message}"
    }
    sendEvent(name: "networkStatus", value: "disconnected")
}

def reconnect() {
    logInfo "Tentando reconectar ao Denon AVR..."
    disconnect()
    connect()
}

def telnetStatus(String status) {
    logWarn "Status do Telnet recebido: ${status}"
    if (status.contains("error") || status.contains("closed") || status.contains("fail") || status.contains("timeout")) {
        handleConnectionFailure()
    }
}

private void handleConnectionFailure() {
    sendEvent(name: "networkStatus", value: "disconnected", descriptionText: "Conexão Telnet perdida")
    disconnect()
    
    // Backoff exponencial resiliente (5s, 10s, 20s, até max 60s)
    int delay = (state.reconnectDelay ?: 5) as Integer
    logInfo "Agendando reconexão em ${delay}s..."
    runIn(delay, "reconnect", [overwrite: true])
    state.reconnectDelay = Math.min(delay * 2, 60)
}

// ---------------------------------------------------------------------------------------
// Envio de Comandos DCP
// ---------------------------------------------------------------------------------------

def sendDenonCommand(String cmd) {
    if (!cmd) return
    logDebug "Enviando comando: ${cmd}"
    sendHubCommand(new hubitat.device.HubAction("${cmd}\r", hubitat.device.Protocol.TELNET))
}

def sendRawCommand(String cmd) {
    logInfo "Enviando comando raw customizado: ${cmd}"
    sendDenonCommand(cmd?.trim())
}

// ---------------------------------------------------------------------------------------
// Parse de Mensagens Recebidas (Terminadas em \r)
// ---------------------------------------------------------------------------------------

def parse(String msg) {
    if (!msg) return
    String cleanMsg = msg.trim()
    logDebug "Recebido raw: '${cleanMsg}'"

    // Marca como conectado se receber mensagens válidas
    if (device.currentValue("networkStatus") != "connected") {
        sendEvent(name: "networkStatus", value: "connected")
        state.reconnectDelay = 5
    }

    // 1. Power Master / Sistema
    if (cleanMsg == "PWON") {
        sendEvent(name: "switch", value: "on", descriptionText: "Denon Sistema ligado")
        return
    }
    if (cleanMsg == "PWSTANDBY") {
        sendEvent(name: "switch", value: "off", descriptionText: "Denon Sistema em standby")
        // Notifica também os filhos sobre standby
        notifyChildZone(2, "switch", "off", "Sistema em standby")
        notifyChildZone(3, "switch", "off", "Sistema em standby")
        return
    }

    // 2. Main Zone Power
    if (cleanMsg == "ZMON") {
        sendEvent(name: "switch", value: "on", descriptionText: "Main Zone ligada")
        return
    }
    if (cleanMsg == "ZMOFF") {
        sendEvent(name: "switch", value: "off", descriptionText: "Main Zone desligada")
        return
    }

    // 3. Main Zone Volume & Mute
    if (cleanMsg.startsWith("MVMAX")) {
        logDebug "Limite máximo reportado pelo AVR: ${cleanMsg}"
        return
    }
    if (cleanMsg.startsWith("MV")) {
        String volStr = cleanMsg.substring(2)
        Double raw = parseRawVolume(volStr)
        if (raw != null) {
            Double db = rawToDb(raw)
            int volLevel = Math.round(raw) as Integer
            sendEvent(name: "rawVolume", value: "${raw} (${db} dB)")
            sendEvent(name: "volumeDb", value: "${db} dB")
            sendEvent(name: "volume", value: volLevel, unit: "%", descriptionText: "Main Volume: ${volLevel} (${db} dB)")
        }
        return
    }
    if (cleanMsg == "MUON") {
        sendEvent(name: "mute", value: "muted", descriptionText: "Main Zone mutada")
        return
    }
    if (cleanMsg == "MUOFF") {
        sendEvent(name: "mute", value: "unmuted", descriptionText: "Main Zone desmutada")
        return
    }

    // 4. Main Zone Input Source
    if (cleanMsg.startsWith("SI")) {
        String code = cleanMsg.substring(2)
        String label = getCodeToLabelMap()[code] ?: code
        sendEvent(name: "mediaInputSource", value: label, descriptionText: "Main Zone Entrada: ${label} (${code})")
        return
    }

    // 5. Sound Mode
    if (cleanMsg.startsWith("MS")) {
        String mode = cleanMsg.substring(2)
        sendEvent(name: "soundMode", value: mode, descriptionText: "Modo de som: ${mode}")
        return
    }

    // 6. Zone 2
    if (cleanMsg.startsWith("Z2")) {
        handleZoneMessage(2, cleanMsg.substring(2))
        return
    }

    // 7. Zone 3
    if (cleanMsg.startsWith("Z3")) {
        handleZoneMessage(3, cleanMsg.substring(2))
        return
    }

    // 8. Video Select
    if (cleanMsg.startsWith("SV")) {
        logDebug "Video Select reportado: ${cleanMsg}"
        return
    }
}

private void handleZoneMessage(int zoneNum, String subMsg) {
    if (subMsg == "ON") {
        notifyChildZone(zoneNum, "switch", "on", "Zone ${zoneNum} ligada")
        return
    }
    if (subMsg == "OFF") {
        notifyChildZone(zoneNum, "switch", "off", "Zone ${zoneNum} desligada")
        return
    }
    if (subMsg == "MUON") {
        notifyChildZone(zoneNum, "mute", "muted", "Zone ${zoneNum} mutada")
        return
    }
    if (subMsg == "MUOFF") {
        notifyChildZone(zoneNum, "mute", "unmuted", "Zone ${zoneNum} desmutada")
        return
    }
    
    // Volume de zona: Z250 ou Z2505 (dígitos)
    if (subMsg.matches("^\\d{2,3}\$")) {
        Double raw = parseRawVolume(subMsg)
        if (raw != null) {
            Double db = rawToDb(raw)
            int volLevel = Math.round(raw) as Integer
            notifyChildZone(zoneNum, "rawVolume", "${raw} (${db} dB)", null)
            notifyChildZone(zoneNum, "volumeDb", "${db} dB", null)
            notifyChildZone(zoneNum, "volume", volLevel, "Zone ${zoneNum} Volume: ${volLevel} (${db} dB)")
        }
        return
    }

    // Input de zona: Z2<SOURCE>
    String code = subMsg
    String label = (code == "SOURCE") ? "Source (Main)" : (getCodeToLabelMap()[code] ?: code)
    notifyChildZone(zoneNum, "mediaInputSource", label, "Zone ${zoneNum} Entrada: ${label}")
}

// ---------------------------------------------------------------------------------------
// Capabilities e Comandos Main Zone
// ---------------------------------------------------------------------------------------

def on() {
    logInfo "Ligando Main Zone..."
    sendDenonCommand("ZMON")
}

def off() {
    logInfo "Desligando Main Zone..."
    sendDenonCommand("ZMOFF")
}

def allZonesOff() {
    logInfo "Desligando todas as zonas (Standby Geral)..."
    sendDenonCommand("PWSTANDBY")
}

def mute() {
    logInfo "Mutando Main Zone..."
    sendDenonCommand("MUON")
}

def unmute() {
    logInfo "Desmutando Main Zone..."
    sendDenonCommand("MUOFF")
}

def setMute(muteState) {
    logInfo "setMute(${muteState}) na Main Zone..."
    if (muteState == "muted" || muteState == "on" || muteState == true) {
        mute()
    } else {
        unmute()
    }
}

def volumeUp() {
    logDebug "Main Zone Volume Up"
    sendDenonCommand("MVUP")
}

def volumeDown() {
    logDebug "Main Zone Volume Down"
    sendDenonCommand("MVDOWN")
}

def setVolume(volumeLevel) {
    String rawDenon = calculateRawDenon(volumeLevel)
    Double db = rawToDb(parseRawVolume(rawDenon))
    logInfo "Ajustando volume da Main Zone para ${rawDenon} (${db} dB)..."
    sendDenonCommand("MV${rawDenon}")
}

def setInputSource(String sourceName) {
    if (!sourceName) return
    String code = getLabelToCodeMap()[sourceName] ?: sourceName
    logInfo "Alterando entrada da Main Zone para: ${sourceName} (${code})"
    sendDenonCommand("SI${code}")
}

def setSoundMode(String mode) {
    if (!mode) return
    logInfo "Alterando modo de som para: ${mode}"
    sendDenonCommand("MS${mode.trim().toUpperCase()}")
}

def refresh() {
    logInfo "Atualizando status do Denon AVR..."
    sendDenonCommand("PW?")
    sendDenonCommand("ZM?")
    sendDenonCommand("MV?")
    sendDenonCommand("MU?")
    sendDenonCommand("SI?")
    sendDenonCommand("MS?")

    if (settings.enableZone2 != false) {
        refreshZone(2)
    }
    if (settings.enableZone3 != false) {
        refreshZone(3)
    }
}

// ---------------------------------------------------------------------------------------
// Delegação e Comunicação com Child Devices (Zone 2 e Zone 3)
// ---------------------------------------------------------------------------------------

def sendZoneCommand(int zoneNum, String action) {
    logDebug "Comando recebido do filho Zone ${zoneNum}: ${action}"
    switch (action) {
        case "ON":
            sendDenonCommand("Z${zoneNum}ON")
            break
        case "OFF":
            sendDenonCommand("Z${zoneNum}OFF")
            break
        case "MUON":
            sendDenonCommand("Z${zoneNum}MUON")
            break
        case "MUOFF":
            sendDenonCommand("Z${zoneNum}MUOFF")
            break
        case "UP":
            sendDenonCommand("Z${zoneNum}UP")
            break
        case "DOWN":
            sendDenonCommand("Z${zoneNum}DOWN")
            break
        default:
            sendDenonCommand("Z${zoneNum}${action}")
            break
    }
}

def setZoneVolume(int zoneNum, volumeLevel) {
    String rawDenon = calculateRawDenon(volumeLevel)
    Double db = rawToDb(parseRawVolume(rawDenon))
    logInfo "Ajustando volume da Zone ${zoneNum} para ${rawDenon} (${db} dB)..."
    sendDenonCommand("Z${zoneNum}${rawDenon}")
}

def setZoneInputSource(int zoneNum, String sourceName) {
    if (!sourceName) return
    String code = (sourceName == "Source (Main)") ? "SOURCE" : (getLabelToCodeMap()[sourceName] ?: sourceName)
    logInfo "Alterando entrada da Zone ${zoneNum} para: ${sourceName} (${code})"
    sendDenonCommand("Z${zoneNum}${code}")
}

def refreshZone(int zoneNum) {
    logDebug "Consultando status da Zone ${zoneNum}..."
    sendDenonCommand("Z${zoneNum}?")
    sendDenonCommand("Z${zoneNum}MU?")
}

private void notifyChildZone(int zoneNum, String attrName, Object attrValue, String desc) {
    def child = getChildDevice(getChildDni(zoneNum))
    if (child) {
        child.parseZoneEvent(attrName, attrValue, desc)
    }
}

private String getChildDni(int zoneNum) {
    return "${device.deviceNetworkId}-zone${zoneNum}"
}

private void syncChildDevices() {
    manageChildZone(2, settings.enableZone2 != false)
    manageChildZone(3, settings.enableZone3 != false)
}

private void manageChildZone(int zoneNum, boolean isEnabled) {
    String dni = getChildDni(zoneNum)
    def child = getChildDevice(dni)
    if (isEnabled && !child) {
        logInfo "Criando dispositivo filho para Zone ${zoneNum} (${dni})..."
        try {
            child = addChildDevice(
                "TecnoSimples",
                "Denon AVR Zone Child",
                dni,
                [
                    name: "${device.displayName} - Zone ${zoneNum}",
                    label: "${device.displayName} - Zone ${zoneNum}",
                    isComponent: false
                ]
            )
            child.updateDataValue("zoneNumber", "${zoneNum}")
            updateChildSupportedInputs(child)
        } catch (Exception e) {
            logWarn "Erro ao criar filho Zone ${zoneNum}: ${e.message}"
        }
    } else if (!isEnabled && child) {
        logInfo "Removendo dispositivo filho para Zone ${zoneNum} (${dni})..."
        deleteChildDevice(dni)
    } else if (child) {
        updateChildSupportedInputs(child)
    }
}

private void updateSupportedInputs() {
    List<String> inputs = getLabelToCodeMap().keySet().toList()
    sendEvent(name: "supportedInputs", value: groovy.json.JsonOutput.toJson(inputs))
    
    // Atualiza filhos
    [2, 3].each { zNum ->
        def child = getChildDevice(getChildDni(zNum))
        if (child) {
            updateChildSupportedInputs(child)
        }
    }
}

private void updateChildSupportedInputs(child) {
    List<String> childInputs = ["Source (Main)"] + getLabelToCodeMap().keySet().toList()
    child.sendEvent(name: "supportedInputs", value: groovy.json.JsonOutput.toJson(childInputs))
}

// ---------------------------------------------------------------------------------------
// Funções Auxiliares de Conversão (Volume e Mapeamento de Entradas)
// ---------------------------------------------------------------------------------------

Double parseRawVolume(String val) {
    if (!val) return null
    try {
        if (val.length() == 3) {
            return (val.substring(0, 2) + "." + val.substring(2)).toDouble()
        } else if (val.length() == 2) {
            return val.toDouble()
        }
    } catch (Exception e) {
        logDebug "Erro ao parsear volume raw '${val}': ${e.message}"
    }
    return null
}

Double rawToDb(Double raw) {
    if (raw == null) return -80.0
    // No Denon DCP: raw 80 = 0.0 dB (referência). raw 40 = -40.0 dB.
    return (raw - 80.0)
}

String calculateRawDenon(def inputVol) {
    if (inputVol == null) return "00"
    double val = inputVol as Double
    int raw
    
    if (val < 0) {
        // Valor negativo: interpreta como dB relativo (-80.0 a 0.0 dB)
        // Ex: -24 -> -24 + 80 = 56
        raw = Math.round(val + 80.0) as Integer
    } else {
        // Valor positivo ou zero: interpreta como nível direto (0 a 100)
        // Ex: 56 -> 56
        raw = Math.round(val) as Integer
    }
    
    // Clamp físico Denon (00 a 98)
    raw = Math.max(0, Math.min(98, raw))
    
    // Limite de segurança configurável (padrão 80 raw = 0.0 dB referência)
    int safeMaxRaw = (settings.maxVolumeLimit != null) ? (settings.maxVolumeLimit as Integer) : 80
    if (raw > safeMaxRaw) {
        logWarn "Volume solicitado (${raw}) ultrapassa o limite de segurança (${safeMaxRaw} = ${safeMaxRaw - 80} dB). Limitado a ${safeMaxRaw}."
        raw = safeMaxRaw
    }
    
    return sprintf("%02d", raw)
}

Map<String, String> getLabelToCodeMap() {
    Map<String, String> map = [:]
    String config = settings.customInputs ?: "TV:TV, Apple TV:MPLAY, Blu-ray:BD, Game:GAME, Cabo:SAT/CBL, Música:NET, Bluetooth:BT, Auxiliar:AUX1, CD:CD, Phono:PHONO, Tuner:TUNER"
    config.split(",").each { pair ->
        def parts = pair.split(":", 2)
        if (parts.size() == 2) {
            map[parts[0].trim()] = parts[1].trim()
        }
    }
    return map
}

Map<String, String> getCodeToLabelMap() {
    Map<String, String> labelToCode = getLabelToCodeMap()
    Map<String, String> codeToLabel = [:]
    labelToCode.each { label, code ->
        codeToLabel[code] = label
    }
    return codeToLabel
}

// ---------------------------------------------------------------------------------------
// Logs Padronizados
// ---------------------------------------------------------------------------------------

private void logDebug(String msg) {
    if (settings.logEnable != false) log.debug "[Denon Master] ${msg}"
}

private void logInfo(String msg) {
    if (settings.txtEnable != false) log.info "[Denon Master] ${msg}"
}

private void logWarn(String msg) {
    log.warn "[Denon Master] ${msg}"
}
