/**
*   SMS Notify via Zapier
*
*
*  2020 Brett Error
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
*
*
*/

 def driverVer(){ 
 	// This is Device Driver Version
 	"0.41"
 }

metadata {
  	definition (name: "SMS Notify via Zapier", 
  	namespace: "shimbone", 
  	author: "Brett Error", 
  	importUrl: "https://raw.githubusercontent.com/shimbone/Scrubitat/master/Drivers/SMS_Notify_Via_Zapier.groovy") {
    	
    	capability "Notification"
    	capability "Actuator"
    	
    	command "setSubject", ["string"]
    	command "clearSubject"
    	
    	attribute "subject", "string"
  	}
}

preferences {
	input("url", "text", title: "Zapier Webhook URL", descriptionText: "Zapier Webhook URL", required:true)
	input("messageName", "text", title: "Advanced: Message Parameter Label", description: "", defaultValue: "message", required:true)
	input("messageFrom", "text", title: "Message labelled as from", description: "", defaultValue: "Hubitat", required: true)
	input("descText", "bool", title: "Log Event 'DescriptionText'", defaultValue:true)
	input("debug", "bool", title: "Enable Debug Logging?:", defaultValue:true)
}


def installed() {
	infoLog("Device installing with driver version ${driverVer()}")
	initialize()
	updateDataValue("driverVersion", driverVer())
	
	// By default debug logging is on during installation.
	// 3 min after installation, turn off debug logging
 	runIn(180, debugLoggingOff)
 	
}

def updated() {
	debugLog("Settings updated")
	initialize()
	if (getDataValue("driverVersion") != driverVer()) upgradeDriver()
	// now that settings have been deliberately set, we don't want to automatically turn off
	// debug logging if the user has it on
	unschedule("debugLoggingOff")
	def msg = ""
	settings.each {k, v -> msg += "// $k = $v "}
	infoLog("Settings now: $msg")
	
	clearSubject()
 }

def initialize() {
	debugLog("Driver initializing")
}

def upgradeDriver(){
	def validSettings = [url: "", messageFrom: "Hubitat", messageName: "message", debug: true, descText: true]
	
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
}


def parse(description) {
	debugLog("Parse called on input: $description")
}

def setSubject(subject){
	if (getDataValue("driverVersion") != driverVer()) upgradeDriver()
	def descriptionText = "subject set to $subject"
	sendEvent(name: "subject", value: subject, descriptionText: descriptionText)
	if (settings.descText) infoLog(descriptionText)
}

def clearSubject(){
	if (getDataValue("driverVersion") != driverVer()) upgradeDriver()
	def descriptionText = "subject cleared"
	sendEvent(name: "subject", value: "-", descriptionText: descriptionText)
	if (settings.descText) infoLog(descriptionText)
}

// create this as "global" field
def message = ""

def deviceNotification(inmsg){
	message = inmsg
	if (getDataValue("driverVersion") != driverVer()) upgradeDriver()
	state.lastMsg = [text: "${message}", sender: "${settings.messageFrom}" ]
	
	def messageBody = [(settings.messageName) : "${message}", sender: "${settings.messageFrom}" ]	
			
	if (device.currentSubject != "-"){
		messageBody["subject"] = "${device.currentSubject}"
		state.lastMsg["subject"] = "${device.currentSubject}"
	}
	clearSubject()
	
	if (settings.url == null){
		errorLog("<b>ERROR: Unable to send message.  Zapier URL has not been configured.</b> Please enter the Zapier Webhook URL in the preferences section of the driver")
		state.lastMsg["error"] = "URL not configured in preferences.  Please configure URL before sending messages."
		state.lastMsg["status"] = "failed"
	}
	else {
		
		// post params
		def params = [
			uri: "${settings.url}",
			body: messageBody
		]

		debugLog("Sending message to Zapier. HTTP Post Params: ${params}")
		// Send the message to Zapier
		try {
			httpPostJson(params, {serverResponse(it)})
		}
		catch (e){
			warnLog("Error when posting message to Zapier webhook to be sent as SMS")
			warnLog(e.toString())
			errorLog("Failed to send SMS message: ${message}")
			noteMsgFailureToState()
		}
	}
}

def serverResponse(response){
	debugLog("Received server response")
	if (response.isSuccess()){
		// HTTP Post went through
		state.lastMsg["status"] = "success"	
		debugLog("HTTP Post data: ${response.getData()}")
		infoLog("Message sent: $message")
		response.getData().each { k, v ->
			state.lastMsg[k] = v
		}
	}
	else {
		// HTTP Post must have failed
		noteMsgFailureToState(response)
		debugLog("Exception: $e")
	}
}

def noteMsgFailureToState(response){
	
	if (response){
		state.lastMsg["HTTPStatusCode"] = response.getStatus()
		warnLog("Status code: ${response.getStatus()}")
		debugLog("Response object:${response.toString()}")
		state.lastMsg["error"] = "Zapier server reported the error"
		errorLog("Zapier server reported error.  Sending message failed: $message")
	}
	else {
		state.lastMsg["error"] = "Exception when attempting to post to Zapier"
		errorLog("Exception when attempting to post to Zapier.  Sending message failed: $message")
	}
	state.lastMsg["status"] = "failed"				
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