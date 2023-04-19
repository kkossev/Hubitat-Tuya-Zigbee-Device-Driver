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
 *                                   TODO: 'device' capability
 */

def version() { "1.0.0" }
def timeStamp() {"2023/04/19 9:39 PM"}

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

        // trap for Hubitat F2 bug
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug" 
    }

    preferences {
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: \
             '<i>Enables command logging.</i>'
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: false, description: \
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
}
