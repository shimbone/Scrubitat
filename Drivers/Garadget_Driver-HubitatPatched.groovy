/**
 *  Garadget Device Handler
 *
 *  Copyright 2016 Stuart Buchanan based loosely based on original code by Krishnaraj Varma with thanks
 *  Additional contribution by:
 *   - RBoy
 *   - btrenbeath
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 21/03/2018 V1.8 Report Door Control and Garage Door Control state -RBoy
 * 14/02/2018 V1.7 Support Door Control capability for expanded compatibility with SmartApps -RBoy
 * 04/02/2018 V1.6 Updated Door Status response parsing to be more resilient to changes in format from Garadget, and fixed bug resulting from latest Garadget response format change. -btrenbeath
 * 12/12/2017 V1.5 UPDATED - Changed the route to immediately update send the 'Closing' status regardless of what the Garget device reads. Also changed the mechanism used to wait on the 'opening' delay to utilize 'runOnce' instead of 'delayBetween', since delayBetween is broken in the SmartThings SDK.  I added 2000 miliseconds to the wait time configured in Garadget for the device to account for lag and delay in SmartThings execution. - btrenbeath
 * 22/07/2016 V1.4 updated with "Garage Door Control" capability with thanks to Nick Jones, also have improved the open command to refresh status again after door motion timeframe has elapsed
 * 12/02/2016 V1.3 updated with to remove token and DeviceId parameters from inputs to retrieving from dni
 */


import groovy.json.JsonOutput

preferences {
	input("prdt", "text", title: "sensor scan interval in mS (default: 1000)")
	input("pmtt", "text", title: "door moving time in mS(default: 10000)")
	input("prlt", "text", title: "button press time mS (default: 300)")
	input("prlp", "text", title: "delay between consecutive button presses in mS (default: 1000)")
	input("psrr", "text", title: "number of sensor reads used in averaging (default: 3)")
	input("psrt", "text", title: "reflection threshold below which the door is considered open (default: 25)")
	input("paot", "text", title: "alert for open timeout in seconds (default: 320)")
	input("pans", "text", title: " alert for night time start in minutes from midnight (default: 1320)")
	input("pane", "text", title: " alert for night time end in minutes from midnight (default: 360)")
	input("debugEnabled", "bool", title: "Debug Logging", defaultValue:true)
}

metadata {

	definition (name: "Garadget", 
		namespace: "fuzzysb", 
		author: "Stuart Buchanan", 
		vid: "generic-contact-4", 
		ocfDeviceType: "oic.d.garagedoor",
		importUrl: "https://raw.githubusercontent.com/shimbone/Scrubitat/master/Drivers/Garadget_Driver-patched.groovy",
		mnmn: "SmartThings") 
	{

		capability "Switch"
		capability "Contact Sensor"
		capability "Signal Strength"
		capability "Actuator"
		capability "Sensor"
		capability "Refresh"
		capability "Polling"
		capability "Configuration"
		capability "Garage Door Control"
		capability "Door Control"

		attribute "reflection", "string"
		attribute "status", "string"
		attribute "time", "string"
		attribute "lastAction", "string"
		attribute "reflection", "string"
		attribute "ver", "string"

		command "stop"
		command "statusCommand"
		command "setConfigCommand"
		command "doorConfigCommand"
		command "netConfigCommand"
	}

}

// handle commands
def poll() {
	debugLog "Executing 'poll' - Device Manager poll"
    refresh()
}

def refresh() {
	debugLog "Executing 'refresh'"
    statusCommand()
    netConfigCommand()
    doorConfigCommand()

}

def configure() {
	debugLog "Resetting Sensor Parameters to SmartThings Compatible Defaults"
	SetConfigCommand()
}

// Parse incoming device messages to generate events
private parseDoorStatusResponse(resp) {
    debugLog("Executing parseDoorStatusResponse: "+resp.data)
    debugLog("Output status: "+resp.status)
    if(resp.status == 200) {
        debugLog("returnedresult: "+resp.data.result)
        def results = (resp.data.result).tokenize('|')
        def statusvalues = (results[0]).tokenize('=')
        def timevalues = (results[1]).tokenize('=')
        def sensorvalues = (results[2]).tokenize('=')
        def signalvalues = (results[3]).tokenize('=')
        def status = statusvalues[1]
        sendEvent(name: 'status', value: status)
        if(status == "open" || status == "closed"){
        	sendEvent(name: 'contact', value: status, displayed: false)
		sendEvent(name: 'door', value: status, displayed: false)
            }
        def time = timevalues[1]
        sendEvent(name: 'lastAction', value: time)
        def sensor = sensorvalues[1]
        sendEvent(name: 'reflection', value: sensor)
        def signal = signalvalues[1]
        sendEvent(name: 'rssi', value: signal)

    }else if(resp.status == 201){
        debugLog("Something was created/updated")
    }
}

private parseDoorConfigResponse(resp) {
    debugLog("Executing parseResponse: "+resp.data)
    debugLog("Output status: "+resp.status)
    if(resp.status == 200) {
        debugLog("returnedresult: "+resp.data.result)
        def results = (resp.data.result).tokenize('|')

    		results.each { value ->
            	def resultValue = value.tokenize('=')
                switch (resultValue[0]) {
                	case "ver": def ver = resultValue[1];
                    			debugLog ("GARADGET: Firmware Version (ver): " +ver);
                    			sendEvent(name: 'ver', value: ver, displayed: false);
                                break;

                    case "rdt": def rdt = resultValue[1];
                    			debugLog ("GARADGET: Sensor Scan Interval (rdt): " +rdt);
                                break;

                    case "mtt": def mtt = resultValue[1];
                    			state.mtt = mtt;
                                debugLog ("GARADGET: Door Moving Time (mtt): " +mtt);
                                break;

                    case "rlt": def rlt = resultValue[1];
                    			debugLog ("GARADGET: Button Press time (rlt): " +rlt);
                                break;

                    case "rlp": def rlp = resultValue[1];
                    			debugLog ("GARADGET: Delay Between Consecutive Button Presses (rlp)" +rlp);
                                break;

                    case "srr": def srr = resultValue[1];
                    			debugLog ("GARADGET: Number of Sensor Feeds used in Averaging: " +srr);
                                break;

                    case "srt": def srt = resultValue[1];
                    			debugLog ("GARADGET: Reflection Value below which door is 'open': " +srt);
                                break;

                    case "aot": def aot = resultValue[1];
                    			debugLog ("GARADGET: Alert for Open Timeout in seconds: " +aot);
                                break;

                    case "ans": def ans = resultValue[1];
                    			debugLog ("GARADGET: Alert for night time start in minutes from midnight: " +ans);
                                break;

                    case "ane": def ane = resultValue[1];
                    			debugLog ("GARADGET: Alert for night time end in minutes from midnight: " +ane );
                                break;

                    default : debugLog ("GARADGET UNUSED CONFIG: " +resultValue[0] +" value of " +resultValue[1])
                }
            }


    }else if(resp.status == 201){
        debugLog("Something was created/updated")
    }
}

private parseNetConfigResponse(resp) {
    debugLog("Executing parseResponse: "+resp.data)
    debugLog("Output status: "+resp.status)
    if(resp.status == 200) {
        debugLog("returnedresult: "+resp.data.result)
        def results = (resp.data.result).tokenize('|')
        def ipvalues = (results[0]).tokenize('=')
        def snetvalues = (results[1]).tokenize('=')
        def dgwvalues = (results[2]).tokenize('=')
        def macvalues = (results[3]).tokenize('=')
        def ssidvalues = (results[4]).tokenize('=')
        def ip = ipvalues[1]
        sendEvent(name: 'ip', value: ip, displayed: false)
        debugLog("IP Address: "+ip)
        def snet = snetvalues[1]
        debugLog("Subnet Mask: "+snet)
        def dgw = dgwvalues[1]
        debugLog("Default Gateway: "+dgw)
        def mac = macvalues[1]
        debugLog("Mac Address: "+mac)
        def ssid = ssidvalues[1]
        sendEvent(name: 'ssid', value: ssid, displayed: false)
        debugLog("Wifi SSID : "+ssid)
    }else if(resp.status == 201){
        debugLog("Something was created/updated")
    }
}

private parseResponse(resp) {
    debugLog("Executing parseResponse: "+resp.data)
    debugLog("Output status: "+resp.status)
    if(resp.status == 200) {
        debugLog("Executing parseResponse.successTrue")
        def id = resp.data.id
        def name = resp.data.name
        def connected = resp.data.connected
		    def returnValue = resp.data.return_value
    }else if(resp.status == 201){
        debugLog("Something was created/updated")
    }
}

private getDeviceDetails() {
  def fullDni = device.deviceNetworkId
  return fullDni
}

private sendCommand(method, args = []) {
	def DefaultUri = "https://api.particle.io"
  def cdni = getDeviceDetails().tokenize(':')
	def deviceId = cdni[0]
	def token = cdni[1]
    def methods = [
		'doorStatus': [
					uri: "${DefaultUri}",
					path: "/v1/devices/${deviceId}/doorStatus",
					requestContentType: "application/json",
					query: [access_token: token]
                    ],
    'doorConfig': [
					uri: "${DefaultUri}",
					path: "/v1/devices/${deviceId}/doorConfig",
					requestContentType: "application/json",
					query: [access_token: token]
                    ],
		'netConfig': [
					uri: "${DefaultUri}",
					path: "/v1/devices/${deviceId}/netConfig",
					requestContentType: "application/json",
					query: [access_token: token]
                   	],
		'setState': [
					uri: "${DefaultUri}",
					path: "/v1/devices/${deviceId}/setState",
					requestContentType: "application/json",
                    query: [access_token: token],
					body: args[0]
                   	],
		'setConfig': [
					uri: "${DefaultUri}",
					path: "/v1/devices/${deviceId}/setConfig",
					requestContentType: "application/json",
                    query: [access_token: token],
					body: args[0]
                   	]
	]

	def request = methods.getAt(method)

    debugLog "Http Params ("+request+")"

    try{
        debugLog "Executing 'sendCommand'"

        if (method == "doorStatus"){
            debugLog "calling doorStatus Method"
            httpGet(request) { resp ->
                parseDoorStatusResponse(resp)
            }
        }else if (method == "doorConfig"){
            debugLog "calling doorConfig Method"
            httpGet(request) { resp ->
                parseDoorConfigResponse(resp)
            }
        }else if (method == "netConfig"){
			      debugLog "calling netConfig Method"
            httpGet(request) { resp ->
                parseNetConfigResponse(resp)
            }
        }else if (method == "setState"){
            debugLog "calling setState Method"
            httpPost(request) { resp ->
                parseResponse(resp)
			      }
        }else if (method == "setConfig"){
            debugLog "calling setConfig Method"
            httpPost(request) { resp ->
                 parseResponse(resp)
            }
        }else{
            httpGet(request)
        }
    } catch(Exception e){
        debugLog("___exception: " + e)
    }
}


def on() {
/* UPDATED - Changed the route to immediately update send the 'Opening' status regardless of what the Garget device reads. */
/* Also changed the mechanism used to wait on the 'opening' delay to utilize 'runOnce' instead of 'delayBetween', since    */
/* delayBetween is broken in the SmartThings SDK.  I added 2000 miliseconds to the wait time configured in Garadget */
/* for the device to account for lag and delay in SmartThings execution. - btrenbeath   */

	debugLog ("Executing - on()")
	openCommand()

    def buttonPressTime =  new Date()
    def myDelay = (state.mtt).toInteger()
    def laterTime = new Date(now() + myDelay + 2000)
   	debugLog ("TimeStamp - on() - Now: "+buttonPressTime)
    debugLog ("Paramater Validation - on() - Garage Open time - myDelay")
    debugLog ("TimeStamp - on() - +15: "+laterTime)

 	sendEvent(name: 'status', value: 'opening')
	sendEvent(name: 'door', value: 'opening')
    debugLog ("Executing - on() - SendEvent opening")

    runOnce(laterTime, statusCommand, [overwrite: false])
    debugLog ("Executing - on() - runOnce(statusCommand)")
}

def off() {
/* UPDATED - Changed the route to immediately update send the 'Closing' status regardless of what the Garget device reads. */
/* Also changed the mechanism used to wait on the 'opening' delay to utilize 'runOnce' instead of 'delayBetween', since    */
/* delayBetween is broken in the SmartThings SDK.  I added 2000 miliseconds to the wait time configured in Garadget */
/* for the device to account for lag and delay in SmartThings execution. - btrenbeath   */

	debugLog ("Executing - off()")
	closeCommand()

    def buttonPressTime =  new Date()
    def myDelay = (state.mtt).toInteger()
    def laterTime = new Date(now() + myDelay + 2000)
   	debugLog ("TimeStamp - off() - Now: "+buttonPressTime)
    debugLog ("Paramater Validation - off() - Garage Open time - myDelay")
    debugLog ("TimeStamp - off() - +15: "+laterTime)

    sendEvent(name: 'status', value: 'closing')
    sendEvent(name: 'door', value: 'closing')
    debugLog ("Executing - off() - SendEvent closing")

    runOnce(laterTime, statusCommand, [overwrite: false])
    debugLog ("Executing - off() - runOnce(statusCommand)")
}

def stop(){
	debugLog "Executing - stop() - 'sendCommand.setState'"
  def jsonbody = new groovy.json.JsonOutput().toJson(arg:"stop")

  sendCommand("setState",[jsonbody])
  statusCommand()
}

def statusCommand(){
	debugLog "Executing - statusCommand() - 'sendCommand.statusCommand'"
	sendCommand("doorStatus",[])
}

def openCommand(){
	debugLog "Executing - openCommand() - 'sendCommand.setState'"
  def jsonbody = new groovy.json.JsonOutput().toJson(arg:"open")
	sendCommand("setState",[jsonbody])
}

def closeCommand(){
	debugLog "Executing - closeCommand() - 'sendCommand.setState'"
	def jsonbody = new groovy.json.JsonOutput().toJson(arg:"close")
	sendCommand("setState",[jsonbody])
}

def open() {
	debugLog "Executing - open() - 'on'"
	on()
}

def close() {
	debugLog "Executing - close() - 'off'"
	off()
}

def doorConfigCommand(){
	debugLog "Executing doorConfigCommand() - 'sendCommand.doorConfig'"
	sendCommand("doorConfig",[])
}

def SetConfigCommand(){
	def crdt = prdt ?: 1000
	def cmtt = pmtt ?: 10000
	def crlt = prlt ?: 300
	def crlp = prlp ?: 1000
	def csrr = psrr ?: 3
	def csrt = psrt ?: 25
	def caot = paot ?: 320
	def cans = pans ?: 1320
	def cane = pane ?: 360
	debugLog "Executing 'sendCommand.setConfig'"
	def jsonbody = new groovy.json.JsonOutput().toJson(arg:"rdt=" + crdt +"|mtt=" + cmtt + "|rlt=" + crlt + "|rlp=" + crlp +"|srr=" + csrr + "|srt=" + csrt)
	sendCommand("setConfig",[jsonbody])
    jsonbody = new groovy.json.JsonOutput().toJson(arg:"aot=" + caot + "|ans=" + cans + "|ane=" + cane)
    sendCommand("setConfig",[jsonbody])
}

def netConfigCommand(){
	debugLog "Executing 'sendCommand.netConfig'"
	sendCommand("netConfig",[])
}

def debugLog(msg){
	if (settings.debugEnabled) log.debug(msg)
}