/**
 *  Xiaomi Open/Close Sensor
 *
 *  Copyright 2018 Bogdan Alexe
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Xiaomi Door/Window Sensor", namespace: "bogdanalexe90", author: "Bogdan Alexe", ocfDeviceType: "x.com.st.d.sensor.contact") {
		capability "Contact Sensor"
		capability "Sensor"
        capability "Battery"
		capability "Health Check"
        
        fingerprint profileId: "0104", deviceId: "0104", inClusters: "0000,0003,FFFF,0019", outClusters: "0000,0004,FFFF", manufacturer: "LUMI", model: "lumi.sensor_magnet", deviceJoinName: "Xiaomi Door Sensor"
	}
    
    simulator {
    }

	tiles {
		multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
			tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
				attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13"
				attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC"
			}
            tileAttribute("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'${currentValue}%', unit:"%", icon: "st.samsung.da.RC_ic_charge"
    		}
		}
        valueTile("battery", "device.battery", width: 2, height: 2, decoration:"flat") {
			state "battery", label: '${currentValue}% battery', unit: "%"
		}
        
        main("contact")
		details(["contact","battery"])
	}
}

private getBASIC_CLUSTER() { 0x0000 }
private getCUSTOM_MEASURE_VALUE() { 0xFF02 }

private Map getContactResultEvent(Integer newState) {
	String newStateAsString = newState == 1 ? "open" : "closed"
	log.debug "Updating contact value: $newStateAsString"
    
	return createEvent([
        name: "contact",
        value: newStateAsString,
        descriptionText: "{{ device.displayName }} was {{ value }}"
	])
}

private Map getBatteryResultEvent(BigDecimal newVolts) {
	if (newVolts == 0 || newVolts == 255) {
    	return [:]
    }
    
    BigDecimal minVolts = 2.7
    BigDecimal maxVolts = 3.1
    
    BigDecimal newBatteryPercent = ((newVolts - minVolts) / (maxVolts - minVolts)) * 100
    newBatteryPercent = (newBatteryPercent.min(100)).max(1)
    newBatteryPercent = newBatteryPercent.setScale(0, BigDecimal.ROUND_HALF_UP)
    
    log.debug "Updating battery value: $newBatteryPercent"
    
    return createEvent([
    	name: "battery",
        value: newBatteryPercent,
        descriptionText: "{{ device.displayName }} battery was {{ value }}%",
        unit: "%"
    ])
}

private List<Map> getCustomEventList(List<String> data) {
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/42#issuecomment-367801988
	List<String> reverseData = data.reverse()    
    String contactReading = [reverseData.get(17)].join("")
    String batteryReading = [reverseData.get(14), reverseData.get(15)].join("")
   
   	log.warn zigbee.convertHexToInt(batteryReading) / 1000
   
    return [
    	getContactResultEvent(zigbee.convertHexToInt(contactReading)),
        getBatteryResultEvent(zigbee.convertHexToInt(batteryReading) / 1000)
    ]
}


def parse(String description) {
	log.debug "description: $description"

    if (description?.startsWith("on/off")) {
    	return getContactResultEvent((description - "on/off: ") as Integer)
    }
        
    Map descMap = zigbee.parseDescriptionAsMap(description)
     
    switch (descMap?.clusterInt) {  
        // Custom event - battery, contact
        case BASIC_CLUSTER:
        	if (descMap.attrInt == CUSTOM_MEASURE_VALUE && descMap.data){
                log.info "Parsing custom attribute: $descMap"
                return getCustomEventList(descMap.data)
            }
        break
    }
}

def installed() {
	// Device wakes up every 1 hour, this interval allows us to miss one wakeup notification before marking offline
	log.info "### Installed"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}

def updated() {
	// Device wakes up every 1 hours, this interval allows us to miss one wakeup notification before marking offline
	log.info "### Updated"
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}
