/**
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
 
 // Driver for a device hybrid having both contact sensor and switch interfaces.  When the switch is 
 // turned on, the contact sensor closes.  When the switch is off, the contact sensor opens.  The 
 // switch is optionally a momentary switch automatically resetting to off in the amount of time 
 // specified in preferences.
 //
 // This device is primarily useful on Amazon's Alexa platform.  Using it adds two new capabilities to your 
 // home automation implementation:
 //
 // 		Habitat may trigger routines defined in Alexa 
 //
 //		One Alexa routine may trigger/execute other Alexa routines
 //
 // How-to Overview:
 //		Create the Alexa routine you'd like to trigger (either from within Hubitat or from a different Alexa routine
 //		using a device of this type as the routine's trigger
 //
 //		To trigger the routine, set the switch associated with the device you set as the routine's trigger to "on".
 //		This can be done from within Hubitat, a dashboard, or from within another Alexa routine.  If you did not 
 //		enable the "auto off" feature of the device, you may want to turn the switch back off again so it is ready
 //		to trigger again the next time around.
 //
 
 def driverVer(){ 
 	// This is Device Driver Version
 	"20200212.02"
 }
 
 
metadata {

	definition (name: "Experiment", namespace: "shimbone", author: "Brett Error",
		importUrl: "https://raw.githubusercontent.com/shimbone/Scrubitat/master/Drivers/Experiment.groovy"
	) {
		capability "Switch"
		capability "Sensor"
		capability "Actuator"
		capability "ContactSensor"	  
		
	}
    
	preferences {
		input("resetDelay", "enum", title: "Auto Off Time", required: true, options:[[100:"0.1 sec"],[300:"0.3 sec"],[500:"0.5 sec"],[1000:"1.0 sec"],[2000:"2.0 sec"],[5000:"5.0 sec"],[10000:"10.0 sec"],[0:"Never"]], defaultValue: 500)
		input("descText", "bool", title: "Log Event 'DescriptionText'", defaultValue:true)
		input("debug", "bool", title: "Debug Logging", defaultValue:true)
		input("test", "capability.switch", title: "testing")
	}
    
}



def installed() {
	infoLog("Device installing with driver version ${driverVer()}")
	initialize()
	updateDataValue("driverVersion", driverVer())
	// By default debug logging is on during installation.
	// 3 min after installation, turn off debug logging
 	runIn(180, 'debugLoggingOff')
 	// Start with switch off
 	runIn(2, 'off')
}

def updated() {
	debugLog("Settings updated")
	initialize()
	checkDriverUpgrade()
	// now that settings have been deliberately set, we don't want to automatically turn off
	// debug logging if the user has it on
	unschedule("debugLoggingOff")
	def msg = ""
	settings.each {k, v -> msg += "// $k = $v "}
	infoLog("Settings now: $msg")

 }

def initialize() {
	debugLog("Driver initializing")
}

def upgradeDriver(){
	def validSettings = [resetDelay: "500", descText: true, debug: true]
	
	warnLog("${device.displayName} driver version is v${getDataValue("driverVersion")}.  Latest version is v${driverVer()}.  Upgrading driver...")
	unschedule()
	settings.each { key, value ->
		if (! validSettings.containsKey(key)){
			device.removeSetting(key)
			warnLog("Preference $key is not longer needed-- discarding")
		}
	}
	
	validSettings.each{ key, value ->
		if (settings[key] == null){
			if (validSettings[key] != "") {
				device.updateSetting(key, value)
				settings[key] = value
				warnLog("Preference $key set to a default value of $value")
				if (key == "debug") runIn(180, debugLoggingOff)
			}
			else {
				warnLog("Preference $key was not assigned a default value.  It should be reviewed and configured to meet your needs")
			}
		}
	}

	updateDataValue("driverVersion", driverVer())
	warnLog("Driver upgrade complete")
	
	if (device.currentSwitch == null || device.currentContact == null){
		warnLog("Setting switch to <b>OFF</b> as an initial state")
		off()
 	}
}

def checkDriverUpgrade(){
	if (getDataValue("driverVersion") != driverVer()) upgradeDriver()
}

def parse(input) {
	infoLog("Parse called on input: $input")
}

def on() {
	checkDriverUpgrade()
	def resetDesc = ""
	if (settings.resetDelay != "0") resetDesc = ", resetting in ${settings.resetDelay} ms"
	
	def descriptionText = "Switch <b>ON</b>" + resetDesc
	sendEvent(name: "switch", value: "on", descriptionText: descriptionText)
	if (descText) infoLog(descriptionText)
	
	descriptionText = "Contact <b>OPEN</b>"
	sendEvent(name: "contact", value: "open", descriptionText: descriptionText)
	if (descText) infoLog(descriptionText)
	
	if (settings.resetDelay != "0"){
		debugLog("Device resetting to off/closed in ${settings.resetDelay}ms")
		pauseExecution(settings.resetDelay.toInteger())
		off()
	}

}

def off() {
	checkDriverUpgrade()
	
	def descriptionText = "Switch <b>OFF</b>"
	sendEvent(name: "switch", value: "off", descriptionText: descriptionText)
	if (descText) infoLog(descriptionText)
	
	descriptionText = "Contact <b>CLOSED</b>"
	sendEvent(name: "contact", value: "closed", descriptionText: descriptionText)
	if (descText) infoLog(descriptionText)
}


def debugLoggingOff(){
	device.updateSetting("debug", [type:"bool", value: false])
	infoLog("Debug logging on by default for installation or driver upgrade now off")
}

// Logging helpers
def infoLog(msg){
	log.info(prepareLogMsg(msg))
}

def debugLog(msg){
	if (settings.debug) log.debug(prepareLogMsg(msg))
}

def warnLog(msg){
	log.warn(prepareLogMsg(msg))
}

def errorLog(msg){
	log.error(prepareLogMsg(msg))
}

def prepareLogMsg(msg){
	return "[${device.displayName} v${getDataValue("driverVersion")}] ${msg}"
}