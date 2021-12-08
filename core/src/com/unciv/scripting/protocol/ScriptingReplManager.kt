package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.ScriptingImplementation
import com.unciv.scripting.utils.ScriptingDebugParameters

//import com.unciv.scripting.protocol.ScriptingPacket
//import com.unciv.scripting.protocol.ScriptingProtocol


abstract class ScriptingReplManager(val scope: Any, val blackbox: Blackbox): ScriptingImplementation {
    //

    //Thus, separate partly as a semantic distinction. ScriptingBackend is designed mostly to interact with ScriptingState and (indirectly, through ScriptingState) ConsoleScreen by presenting a clean interface to shallower classes in the call stack. This class is designed to do the opposite, and keep all the code for wrapping the interfaces of the deeper and more complicated ScriptingProtocol and Blackbox classes in one place.
}


/**
 * REPL manager that sends and receives only raw code and prints raw strings with a black box. Allows interacting with an external script interpreter, but not suitable for exposing Kotlin-side API in external scripts.
 */
class ScriptingRawReplManager(scope: Any, blackbox: Blackbox): ScriptingReplManager(scope, blackbox) {

    override fun motd(): String {
        return "${exec("motd()\n")}\n"
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        return AutocompleteResults()
    }

    override fun exec(command: String): String {
        if (!blackbox.readyForWrite) {
            throw IllegalStateException("REPL not ready: ${blackbox}")
        } else {
            blackbox.write(command)
            return blackbox.readAll(block=true).joinToString("\n")
        }
    }

    override fun terminate(): Exception? {
        return blackbox.stop()
    }
}


/**
 * REPL manager that uses the IPC protocol defined in ScriptingProtocol.kt to communicate with a black box. Suitable for presenting arbitrary access to Kotlin/JVM state to scripting APIs. See Module.md for a detailed description of the REPL loop.
 */
class ScriptingProtocolReplManager(scope: Any, blackbox: Blackbox): ScriptingReplManager(scope, blackbox) {

    /**
     * ScriptingProtocol puts references to pre-tokenized returned objects in here.
     * Should be cleared here at the end of each REPL execution.
     *
     * This makes sure a single script execution doesn't get its tokenized Kotlin/JVM objects garbage collected, and has a chance to save them elsewhere (E.G. ScriptingScope.apiHelpers.registeredInstances) if it needs them later.
     * Should preserve each instance, not just each value, so should be List and not Set.
     * To test in Python console backend: x = apiHelpers.Jvm.Vector2(1,2); civInfo.endTurn(); print(apiHelpers.toString(x))
     */
    val instanceSaver = mutableListOf<Any?>()

    val scriptingProtocol = ScriptingProtocol(scope, instanceSaver = instanceSaver)

    //TODO: Doc
    fun getRequestResponse(packetToSend: ScriptingPacket, enforceValidity: Boolean = true, execLoop: () -> Unit = fun(){}): ScriptingPacket {
        // Please update the specifications in Module.md if you change the basic structure of this REPL loop.
        if (ScriptingDebugParameters.printScriptingPacketsForDebug) println("\nSending: ${packetToSend}")
        // TODO: Move this to ScriptingProtocol?
        blackbox.write(packetToSend.toJson() + "\n")
        execLoop()
        val response = ScriptingPacket.fromJson(blackbox.read(block=true))
        if (ScriptingDebugParameters.printScriptingPacketsForDebug) println("\nReceived: ${response}")
        if (enforceValidity) {
            ScriptingProtocol.enforceIsResponse(packetToSend, response)
        }
        instanceSaver.clear() // Clear saved references to objects in response, now that the script has had a chance to save them elsewhere.
        return response
    }

    /**
     * Listens to requests for values from the black box, and replies to them, during script execution.
     * Terminates loop after receiving a request with a the 'PassMic' flag.
     */
    fun foreignExecLoop() {
        while (true) {
            val request = ScriptingPacket.fromJson(blackbox.read(block=true))
            if (ScriptingDebugParameters.printScriptingPacketsForDebug) println("\nReceived: ${request}")
            if (request.action != null) {
                val response = scriptingProtocol.makeActionResponse(request)
                if (ScriptingDebugParameters.printScriptingPacketsForDebug) println("\nSending: ${response}")
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
                ScriptingProtocol.makeActionRequests.motd(),
                execLoop = ::foreignExecLoop
            )
        )
    }

    override fun autocomplete(command: String, cursorPos: Int?): AutocompleteResults {
        return ScriptingProtocol.parseActionResponses.autocomplete(
            getRequestResponse(
                ScriptingProtocol.makeActionRequests.autocomplete(command, cursorPos),
                execLoop = ::foreignExecLoop
            )
        )
    }

    override fun exec(command: String): String {
        if (!blackbox.readyForWrite) {
            throw IllegalStateException("REPL not ready: ${blackbox}")
        } else {
            return ScriptingProtocol.parseActionResponses.exec(
                getRequestResponse(
                    ScriptingProtocol.makeActionRequests.exec(command),
                    execLoop = ::foreignExecLoop
                )
            )
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
