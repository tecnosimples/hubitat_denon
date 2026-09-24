/**
 * =======================================================================================
 *  Denon AVR Zone Child (Child Component Driver)
 *  
 *  Plataforma: Hubitat Elevation
 *  Equipamento Alvo: Zonas secundárias do Denon AVR (Zone 2 e Zone 3)
 *  Controle: Delegado ao driver Pai (Denon AVR Master) via socket Telnet centralizado
 *
 *  Copyright 2026 TecnoSimples
 *  Licença: MIT
 * =======================================================================================
 */

metadata {
    definition(
        name: "Denon AVR Zone Child",
        namespace: "TecnoSimples",
        author: "TecnoSimples",
        importUrl: "https://raw.githubusercontent.com/tecnosimples/hubitat_denon/main/drivers/Denon_AVR_Zone.groovy"
    ) {
        capability "Switch"
        capability "AudioVolume"
        capability "MediaInputSource"
        capability "Refresh"
        capability "Actuator"

        // Comandos customizados com descrição
        command "setVolume", [[name: "volume*", type: "NUMBER", description: "Volume em dB (-80 a +18 dB, ex: -40 para -40 dB). Se digitar 40, será interpretado como -40 dB."]]

        // Atributos de estado da zona
        attribute "rawVolume", "string"
        attribute "zoneNumber", "number"
    }

    preferences {
        input name: "logEnable", type: "bool", title: "Habilitar Log de Debug", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Habilitar Log de Descrição/Eventos", defaultValue: true
    }
}

// ---------------------------------------------------------------------------------------
// Ciclo de Vida
// ---------------------------------------------------------------------------------------

def installed() {
    logInfo "Dispositivo de Zona Denon instalado: ${device.displayName}"
}

def updated() {
    logInfo "Dispositivo de Zona Denon atualizado: ${device.displayName}"
}

// ---------------------------------------------------------------------------------------
// Ações Delegadas ao Driver Pai
// ---------------------------------------------------------------------------------------

def on() {
    logInfo "Ligando Zone ${getZoneNumber()}..."
    parent.sendZoneCommand(getZoneNumber(), "ON")
}

def off() {
    logInfo "Desligando Zone ${getZoneNumber()}..."
    parent.sendZoneCommand(getZoneNumber(), "OFF")
}

def mute() {
    logInfo "Mutando Zone ${getZoneNumber()}..."
    if (device.currentValue("switch") != "on") {
        logWarn "Aviso: Zone ${getZoneNumber()} está em standby (OFF). O Denon AVR só aceita mute se a zona estiver ligada."
    }
    parent.sendZoneCommand(getZoneNumber(), "MUON")
}

def unmute() {
    logInfo "Desmutando Zone ${getZoneNumber()}..."
    if (device.currentValue("switch") != "on") {
        logWarn "Aviso: Zone ${getZoneNumber()} está em standby (OFF). O Denon AVR só aceita mute se a zona estiver ligada."
    }
    parent.sendZoneCommand(getZoneNumber(), "MUOFF")
}

def setMute(muteState) {
    logInfo "setMute(${muteState}) na Zone ${getZoneNumber()}..."
    if (muteState == "muted" || muteState == "on" || muteState == true) {
        mute()
    } else {
        unmute()
    }
}

def volumeUp() {
    logDebug "Zone ${getZoneNumber()} Volume Up"
    parent.sendZoneCommand(getZoneNumber(), "UP")
}

def volumeDown() {
    logDebug "Zone ${getZoneNumber()} Volume Down"
    parent.sendZoneCommand(getZoneNumber(), "DOWN")
}

def setVolume(volumeLevel) {
    logInfo "Ajustando volume da Zone ${getZoneNumber()} para ${volumeLevel} dB..."
    parent.setZoneVolume(getZoneNumber(), volumeLevel)
}

def setInputSource(String sourceName) {
    if (!sourceName) return
    logInfo "Alterando entrada da Zone ${getZoneNumber()} para: ${sourceName}"
    parent.setZoneInputSource(getZoneNumber(), sourceName)
}

def refresh() {
    logInfo "Atualizando status da Zone ${getZoneNumber()}..."
    parent.refreshZone(getZoneNumber())
}

// ---------------------------------------------------------------------------------------
// Recepção de Eventos Despachados pelo Pai
// ---------------------------------------------------------------------------------------

def parseZoneEvent(String attrName, Object attrValue, String desc) {
    logDebug "Evento recebido do Pai: ${attrName} = ${attrValue}"
    Map eventMap = [name: attrName, value: attrValue]
    if (attrName == "volume") {
        eventMap.unit = "dB"
    }
    if (desc) {
        eventMap.descriptionText = desc
        logInfo desc
    }
    sendEvent(eventMap)
}

// ---------------------------------------------------------------------------------------
// Funções Auxiliares
// ---------------------------------------------------------------------------------------

int getZoneNumber() {
    def zn = getDataValue("zoneNumber")
    if (zn != null) return zn.toInteger()
    // Fallback por DNI: ...-zone2 -> 2, ...-zone3 -> 3
    if (device.deviceNetworkId.endsWith("-zone3")) return 3
    return 2
}

// ---------------------------------------------------------------------------------------
// Logs Padronizados
// ---------------------------------------------------------------------------------------

private void logDebug(String msg) {
    if (settings.logEnable != false) log.debug "[Zone ${getZoneNumber()}] ${msg}"
}

private void logInfo(String msg) {
    if (settings.txtEnable != false) log.info "[Zone ${getZoneNumber()}] ${msg}"
}
