package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingScope
import com.unciv.scripting.protocol.ScriptingObjectIndex
import com.unciv.scripting.protocol.ScriptingPacket
import com.unciv.scripting.protocol.ScriptingProtocol
import com.unciv.scripting.utils.Blackbox


/*
    1. A scripted action is initiated from the Kotlin side, by sending a command string to the script interpreter.
    2. While the script interpreter is running, it has a chance to request values from the Kotlin side by sending back packets encoding attribute/property, key, and call stacks.
    3. When the Kotlin side receives a request for a value, it uses reflection to access the requested property or call the requested method, and it sends the result to the script interpreter.
    4. When the script interpreter finishes running, it sends a special packet to the Kotlin side. It then sends the REPL output of the command to the Kotlin side.
    5. When the Kotlin interpreter receives the packet marking the end of the command run, it stops listening for value requests packets. It then receives the next value, and passes it back to the display/handler.

    ```
    fun ExecuteCommand(command):
        SendToInterpreter(command:String)
        while True:
            packet:Packet = ReceiveFromInterpreter().parsed()
            if isPropertyrequest(packet):
                SendToInterpreter(ResolvePacket(scriptingScope, packet))
            else if isCommandEndPacket(packet):
                break
        PrintToConsole(ReceiveFromInterpreter():String)
    ```

    The "packets" should probably all be encoded as strings, probably JSON. The technique used to connect the script interpreter to the Kotlin code shouldn't matter. As long as it's wrapped up in and implements the `Blackbox` interface, IPC/embedding based on pipes, STDIN/STDOUT, sockets, queues, embedding, JNI, etc. should all be interchangeable.

    I'm not sure if there'd be much point to or a good technique for letting the script interpreter run constantly and initiate actions on its own, instead of waiting for commands from the Kotlin side.

    You would presumably have to interrupt the main Kotlin-side thread anyway in order to safely run any actions initiated by the script interpreter— Which means that you may as well just register a handler to call the script interpreter at that point.

    Plus, letting the script interpreter run completely in parallel would probably introduce potential for all sorts of issues with no-deterministic synchronicity, and performance issues  Calling the script interpreter from 
*/


class ScriptingReplManager(val scriptingScope: ScriptingScope, val blackbox: Blackbox): ScriptingBackend {
    
    val scriptingProtocol = ScriptingProtocol(scriptingScope)
    
    fun whileEval() {
        return
    }
    
    fun runCode(code: String) {
        blackbox.write(code)
    }
    
    fun getRequestResponse(packetToSend: ScriptingPacket, enforceValidity: Boolean = true, execLoop: () -> Unit = fun(){}): ScriptingPacket {
        blackbox.write(packetToSend.toJson() + "\n")
        execLoop()
        val response = ScriptingPacket.fromJson(blackbox.read(block=true))
        if (enforceValidity) {
            ScriptingProtocol.enforceIsResponse(packetToSend, response)
        }
        return response
    }
    
    fun foreignExecLoop() {
        while (true) {
            val request = ScriptingPacket.fromJson(blackbox.read(block=true))
            if (request.action != null) {
                val response = scriptingProtocol.makeActionResponse(request)
                blackbox.write(response.toJson() + "\n")
            }
            if (request.hasFlag(ScriptingProtocol.KnownFlag.PassMic)) {
                break
            }
        }
    }
    
    override fun motd(): String {
        return ScriptingProtocol.parseActionResponses.motd(
            getRequestResponse(
                ScriptingProtocol.makeActionRequests.motd()
            )
        )
        return "${exec("motd()\n")}\n"
    }
    
    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        return ScriptingProtocol.parseActionResponses.autocomplete(
            getRequestResponse(
                ScriptingProtocol.makeActionRequests.autocomplete(command, cursorPos),
                execLoop = { foreignExecLoop() }
            )
        )
        return AutocompleteResults()
    }
    
    override fun exec(command: String): String {
        if (!blackbox.readyForWrite) {
            throw IllegalStateException("REPL not ready: ${blackbox}")
        } else {
            return ScriptingProtocol.parseActionResponses.exec(
                getRequestResponse(
                    ScriptingProtocol.makeActionRequests.exec(command),
                    execLoop = { foreignExecLoop() }
                )
            )
            runCode(command)
            whileEval()
            return blackbox.readAll(block=true).joinToString("\n")
        }
    }
    
    override fun terminate(): Exception? {
        try {
            val msg = ScriptingProtocol.parseActionResponses.terminate(
                getRequestResponse(
                    ScriptingProtocol.makeActionRequests.terminate()
                )
            )
            if (msg != null) {
                return RuntimeException(msg)
            }
        } catch (e: Exception) {
        }
        return blackbox.stop()
    }
}
