/**
 * Tuya-Zigbee-Device-Driver for Hubitat
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 * 	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * 	in compliance with the License. You may obtain a copy of the License at:
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * 	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * 	for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired from @w35l3y EdgeDrivers project
 *
 * ver. 1.0.0  2023-04-19 kkossev  - Initial test version
 *
 *                                   TODO: rxStats
 *                                   TODO:
 *                                   TODO:
 *                                   TODO:
 *                                   TODO: implement 'Configuration' capability
 *                                   TODO: implement 'Health Check' capability
 *                                   TODO: implement ping() command and rtt neasurement
 *                                   TODO: implement 'Refresh' capability
 *                                   TODO: implement Get Device Info command

 *                                   TODO: 'device' capability
 */

static String version() { "1.0.0" }
static String timeStamp() {"2023/04/20 1:00 PM"}

@Field static final Boolean _DEBUG = true

import hubitat.zigbee.zcl.DataType
import groovy.transform.Field
import groovy.json.JsonOutput

metadata {
    definition(name: 'Tuya Zigbee Device Driver',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Tuya%20Advanced%20Zigbee%20RGBW%20Bulb/Tuya%20Advanced%20Zigbee%20RGBW%20Bulb.groovy',
        namespace: 'tuya', author: 'Krassimir Kossev') {

        capability 'Configuration'
        capability 'Health Check'
        capability 'Refresh'

        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online']
        
        if (_DEBUG) {
            command 'test'
        }

        // trap for Hubitat F2 bug
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug" 
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: \
             '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: \
             '<i>Turns on debug logging for 30 minutes.</i>'
        input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: \
             '<i>Changes how often the hub pings the bulb to check health.</i>'
    }
}

@Field static final Map HealthcheckIntervalOpts = [
    defaultValue: 10,
    options     : [10: 'Every 10 Mins', 15: 'Every 15 Mins', 30: 'Every 30 Mins', 45: 'Every 45 Mins', '59': 'Every Hour', '00': 'Disabled']
]

/**
 * Parse Zigbee message
 * @param description Zigbee message in hex format
 */
void parse(final String description) {
    log.debug "parse: ${description}"
    if (state.stats != null) state.stats['RxCtr'] = (state.stats['RxCtr'] ?: 0) + 1 else state.stats=[:]

}



/*
 * -----------------------------------------------------------------------------
 * Tuya cluster EF00 specific code
 * -----------------------------------------------------------------------------
*/
private static getCLUSTER_TUYA()       { 0xEF00 }
private static getSETDATA()            { 0x00 }
private static getSETTIME()            { 0x24 }

// Tuya Commands
private static getTUYA_REQUEST()       { 0x00 }
private static getTUYA_REPORTING()     { 0x01 }
private static getTUYA_QUERY()         { 0x02 }
private static getTUYA_STATUS_SEARCH() { 0x06 }
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 }

// tuya DP type
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ]
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ]
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ]
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ]
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ]
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits

private sendTuyaCommand(dp, dp_type, fncmd) {
    ArrayList<String> cmds = []
    def ep = safeToInt(state.destinationEP)
    if (ep==null || ep==0) ep = 1
    
    cmds += zigbee.command(CLUSTER_TUYA, SETDATA, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd )
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}"
    return cmds
}

private getPACKET_ID() {
    return randomPacketId()
}

def tuyaBlackMagic() {
    def ep = safeToInt(state.destinationEP ?: 01)
    if (ep==null || ep==0) ep = 1
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200)
}



/*
 * -----------------------------------------------------------------------------
 * Hubitat default handlers methods
 * -----------------------------------------------------------------------------
*/

/**
 * Send configuration parameters to the device
 * Invoked when device is first installed and when the user updates the configuration
 * @return sends zigbee commands
 */
def configure() {
    ArrayList<String> cmds = []
    logInfo 'configure...'
    logDebug settings
    cmds += tuyaBlackMagic()
    sendZigbeeCommands(cmds)
}

/**
 * Invoked by Hubitat when driver is installed
 */
void installed() {
    logInfo 'installed'
    // populate some default values for attributes
    sendEvent(name: 'healthStatus', value: 'unknown')
    sendEvent(name: 'powerSource', value: 'unknown')
}

/**
 * Disable logging (for debugging)
 */
void logsOff() {
    logWarn 'debug logging disabled...'
    device.updateSetting('logEnable', [value: 'false', type: 'bool'])
}

/*
 *-----------------------------------------------------------------------------
 * kkossev drivers commonly used functions
 *-----------------------------------------------------------------------------
*/

static Integer safeToInt(val, Integer defaultVal=0) {
	return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

static Double safeToDouble(val, Double defaultVal=0.0) {
	return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal
}

void sendZigbeeCommands(ArrayList<String> cmd) {
    logDebug "${device.displayName} sendZigbeeCommands(cmd=$cmd)"
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction()
    cmd.each {
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE))
            if (state.stats != null) state.stats['TxCtr'] = (state.stats['TxCtr'] ?: 0) + 1 else state.stats=[:]
    }
    sendHubCommand(allActions)
}

static def driverVersionAndTimeStamp() {version() + ' ' + timeStamp() + ((_DEBUG) ? " debug version!" : " ")}

def getDeviceInfo() {
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>"
}

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())]
    return state.destinationEP ?: device.endpointId ?: "01"
}

def checkDriverVersion() {
    if (state.driverVersion != null && driverVersionAndTimeStamp() == state.driverVersion) {
        // no driver version change
    }
    else {
        logDebug "${device.displayName} updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        state.driverVersion = driverVersionAndTimeStamp()
    }
}


/**
 * called from TODO
 * 
 */

def deleteAllStatesAndJobs() {
    state.clear()    // clear all states
    unschedule()
    device.deleteCurrentState('*')
    device.deleteCurrentState('')

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}"
}


/**
 * called from TODO
 * 
 */
def resetStats() {
    state.stats = [:]
    state.states = [:]
    state.lastRx = [:]
    state.lastTx = [:]
    state.stats["RxCtr"] = 0
    state.stats["TxCtr"] = 0
    state.states["isDigital"] = false
    state.states["isRefresh"] = false
    state.states["notPresentCtr"] = 0
    state.lastTx["pingTime"] = new Date().getTime()
}


/**
 * called from TODO
 * 
 */
def setDestinationEP() {
    def ep = device.getEndpointId()
    if (ep != null && ep != 'F2') {
        state.destinationEP = ep
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}"
    }
    else {
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!"
        state.destinationEP = "01"    // fallback EP
    }      
}


def logDebug(msg) {
    if (settings.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings.logEnable) {
        log.warn "${device.displayName} " + msg
    }
}

def test() {
    sendZigbeeCommands(zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [:], delay=200))
}
