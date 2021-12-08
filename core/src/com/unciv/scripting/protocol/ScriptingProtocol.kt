package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.reflection.FunctionDispatcher
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.utils.ScriptingDebugParameters
import com.unciv.scripting.serialization.TokenizingJson
import com.unciv.ui.utils.stringifyException
import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID


// See Module.md for a description of the protocol.
// Please update the specifications there if you add, remove, or change any action types or packet structures here.

// To add a new packet type:
//  Add to Module.md.
//  Add to responseTypes.
//  Add to makeActionRequests and parseActionResponses, or to makeActionResponse.
//  Use in scripting backend, E.G. wrapping.py


/**
 * Implementation of IPC packet specified in Module.md.
 *
 * @property action String or null specifying request or response type of packet.
 * @property identifier Randomly generated String, or null, that should be shared by and unique to each request-response pair.
 * @property data Arbitratry hierarchy of containers and values.
 * @property flags Collection of Strings that communicate extra information in this packet. Can be used with null action.
 */
@Serializable
data class ScriptingPacket(
    var action: String?,
    var identifier: String?,
    var data: JsonElement? = null,
    // This can have arbitrary structure, values/types, and depth. So since it lives so close to the IPC protocol anyway, JsonElement is the easiest way to represent and serialize it.
    var flags: Collection<String> = listOf()
) {

    companion object {
        /**
         * Parse a JSON string into a ScriptingPacket instance.

         * Uses automatic Kotlin object tokenization by InstanceTokenizer through TokenizingJson.

         * @param string Valid JSON string.
         * @return ScriptingPacket instance with properties and data field hierarchy of JSON string.
         */
        fun fromJson(string: String): ScriptingPacket = TokenizingJson.json.decodeFromString<ScriptingPacket>(string)
    }

    /**
     * Encode this packet into a JSON string.

     * Uses automatic Kotlin object tokenization by InstanceTokenizer through TokenizingJson.

     * @return Valid JSON string.
     */
    fun toJson() = TokenizingJson.json.encodeToString(this)

    /**
     * @param flag Flag type to check for.
     * @return Whether the given flag is present in this packet's flags field.
     */
    fun hasFlag(flag: ScriptingProtocol.KnownFlag) = flag.name in flags

}


/**
 * Implementation of IPC communication protocol specified in Module.md.
 *
 * Does not handle transmission or reception. Only creates responses and requests.
 * Agnostic to Unciv types. Protocol spec should be generic enough to apply to all Kotlin objects.
 * Uses automatic Kotlin/JVM object tokenization by InstanceTokenizer through TokenizingJson.
 *
 * @property scope Kotlin/JVM object that represents the hierarchical root of actions that require recursively resolving a property path through reflection. In practice, this should never be set to anything other than an instance of ScriptingScope. But that's an API-level implementation and use case detail, so the protocol is typed for and should never assume anything more specific than Any.
 * @property instanceSaver Mutable list in which to save response values, to prevent created instances from being garbage collected before the other end of the IPC can save or use them. List, not Set, to preserve instance identity and not value. Not automatically cleared. Should be manually cleared when not needed.
 */
class ScriptingProtocol(val scope: Any, val instanceSaver: MutableList<Any?>? = null) {

    /**
     * Enum class of valid items for the flag field in scripting packets.
     *
     * The flag field requires strings, so currently the .value property is usually used when constructing and working with scripting packets.
     *
     * @property name Serialized string value of this flag.
     */
    enum class KnownFlag {
        PassMic,
        Exception
    }

    companion object {

        /**
         * @return Unique, never-repeating ID string.
         */
        fun makeUniqueId(): String {
            return "${System.nanoTime()}-${Random.nextBits(30)}-${UUID.randomUUID().toString()}"
        }

        /**
         * Map of valid request action types to their required response action types.
         */
        val responseTypes = mapOf<String?, String?>(
            // Deliberately repeating myself because I don't want to imply a hard specification for the names of response packets.
            "motd" to "motd_response",
            "autocomplete" to "autocomplete_response",
            "exec" to "exec_response",
            "terminate" to "terminate_response",
            "read" to "read_response",
            "assign" to "assign_response",
            "delete" to "delete_response",
            "dir" to "dir_response",
//            "hash" to "hash_response",
            "keys" to "keys_response",
            "length" to "length_response",
            "contains" to "contains_response",
            "ismapping" to "ismapping_response",
            "callable" to "callable_response",
            "args" to "args_response",
            "docstring" to "docstring_response"
        )

        /**
         * Ensure that a response packet is a valid response for a request packet.
         *
         * This function only checks the action type and unique identifier metadata of the packets.
         * It should catch desynchronization arising from E.G. race conditions, or if one side either forgets to send an expected packet or erroneously sends an unexpected packet.
         * But as the data field of each packet is variable, and defined more by convention/convergence than by specification, it will not catch if the data contained in the request or response is malformed or invalid. That task is left up to the sender and receiver of the packet.
         *
         * @param request Original packet. Sets action type and identifier.
         * @param response Response packet. Should have matching action type and same identifier as request.
         * @throws IllegalStateException If response and request mismatched.
         */
        fun enforceIsResponse(request: ScriptingPacket, response: ScriptingPacket): ScriptingPacket {
            if (!(
                (response.action == responseTypes[request.action]!!)
                && response.identifier == request.identifier
            )) {
                throw IllegalStateException("Scripting packet response does not match request ID and type: ${request}, ${response}")
            }
            return response
        }

    }


    /**
     * Functions to generate requests to send to a script interpreter.
     *
     * Implements the specifications on packet fields and structure in Module.md.
     * Function names and call arguments parallel ScriptingBackend.
     */
    object makeActionRequests {
        fun motd() = ScriptingPacket(
            "motd",
            makeUniqueId(),
            JsonNull,
            listOf(KnownFlag.PassMic.name)
        )
        fun autocomplete(command: String, cursorPos: Int?) = ScriptingPacket(
            "autocomplete",
            makeUniqueId(),
            JsonObject(mapOf("command" to JsonPrimitive(command), "cursorpos" to JsonPrimitive(cursorPos))),
            listOf(KnownFlag.PassMic.name)
        )
        fun exec(command: String) = ScriptingPacket(
            "exec",
            makeUniqueId(),
            JsonPrimitive(command),
            listOf(KnownFlag.PassMic.name)
        )
        fun terminate() = ScriptingPacket(
            "terminate",
            makeUniqueId()
        )
    }

    /**
     * Functions to parse a response packet received after a request packet sent to a scripting interpreter.
     *
     * Implements the specifications on packet fields and structure in Module.md.
     * Function names and return types parallel ScriptingBackend.
     */
    object parseActionResponses {
        fun motd(packet: ScriptingPacket): String = (packet.data as JsonPrimitive).content

        fun autocomplete(packet: ScriptingPacket): AutocompleteResults =
            if (packet.data is JsonArray)
                AutocompleteResults((packet.data as List<JsonElement>).map { (it as JsonPrimitive).content })
            else
                AutocompleteResults(listOf(), true, (packet.data as JsonPrimitive).content)

        fun exec(packet: ScriptingPacket): String = (packet.data as JsonPrimitive).content

        fun terminate(packet: ScriptingPacket): Exception? =
            if (packet.data == JsonNull || packet.data == null)
                null
            else
                RuntimeException((packet.data as JsonPrimitive).content)
    }

    /**
     * Save an instance in the mutable list cache that prevents generated responses from being garbage-collected before the other end of the protocol can use them.
     *
     * @param obj Instance to save.
     * @return Same instance as given, unchanged. Allows this function to be chained, or used to pass through an anonymous instance.
     */
    fun trySaveInstance(obj: Any?): Any? {
        if (instanceSaver != null) {
            instanceSaver.add(obj)
        }//TODO: I should use this in more of the request types.
        return obj
    }

    // Helper class for parsing the data field of a common request packet structure from script interpreters.

    // THIS IS A CONVENTION THAT MANY REQUEST TYPES HAVE CONVERGED ON, NOT A SPECIFICATION THAT IS INTEGRAL TO THE PROTOCOL ITSELF.
    // See Module.md.

    // @param packet Packet from which to read the data field.
    private class ScriptingPacketPathedData(val packet: ScriptingPacket) {
        val packetData = packet.data as JsonObject

        val use_root = (getRealOrNull(packetData["use_root"]) ?: false) as Boolean
        val root = getRealOrNull(packetData["root"])
        val path = TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>(packetData["path"]!!)
        val value = getRealOrNull(packetData["value"])

        init {
            if (ScriptingDebugParameters.printScriptingAccessForDebug) printDebug()
        }

        private fun getRealOrNull(jsonElement: JsonElement?): Any? {
            if (jsonElement == null) return null
            return TokenizingJson.getJsonReal(jsonElement)
        }

        private fun printDebug() = println("${packet.action}: ${if (use_root) "Root: ${root} " else ""}Path: ${Reflection.stringifyKotlinPath(path)}${if (value == null) "" else " Value: ${value}"}")
    }

    /**
     * Return a valid response packet for a request packet from a script interpreter.
     *
     * This is what allows scripts to access Kotlin/JVM properties, methods, keys, etc.
     * Implements the specifications on action and response types, structures, and behaviours in Module.md.
     *
     * @param packet Packet to respond to.
     * @return Response packet.
     */
    fun makeActionResponse(packet: ScriptingPacket): ScriptingPacket {
        val action = responseTypes[packet.action]!!
        var responseData: JsonElement? = null
        val responseFlags = mutableListOf<String>()
        when (packet.action) {
            // There's a lot of repetition here, because I don't want to enforce any specification on what form the request and response data fields for actions must take.
            // I prefer to try to keep the code for each response type independent enough to be readable on its own.
            // This is kinda the reference (and only) implementation of the protocol spec. So the serialization and such can be and is handled with functions, but the actual structure and logic of each response should be hardcoded manually IMO.
            "read" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    responseData = TokenizingJson.getJsonElement(
                        trySaveInstance(
                            Reflection.resolveInstancePath(
                                if (packetData.use_root) packetData.root else scope,
                                packetData.path
                            )
                        ),
                        requireTokenization = TokenizingJson::isNotPrimitive
                    )
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "assign" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    Reflection.setInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path,
                        packetData.value
                    )
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "delete" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    Reflection.removeInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "dir" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    responseData = TokenizingJson.getJsonElement(leaf!!::class.members.map {it.name})
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "keys" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    responseData = TokenizingJson.getJsonElement((leaf as Map<Any, *>).keys)
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "length" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    try {
                        responseData = TokenizingJson.getJsonElement((leaf as Array<*>).size)
                        // AFAICT avoiding these casts/checks would require reflection.
                    } catch (e: ClassCastException) {
                        try {
                            responseData = TokenizingJson.getJsonElement((leaf as Map<*, *>).size)
                        } catch (e: ClassCastException) {
                            responseData = TokenizingJson.getJsonElement((leaf as Collection<*>).size)
                        }
                    }
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "contains" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    try {
                        responseData = TokenizingJson.getJsonElement(packetData.value in (leaf as Map<Any?, Any?>))
                    } catch (e: ClassCastException) {
                        responseData = TokenizingJson.getJsonElement(packetData.value in (leaf as Collection<Any?>))
                    }
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "ismapping" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    try {
                        leaf as Map<Any, *>
                        // Ensure same behaviour as "keys" action. IK It's probably/hopefully the same as using the is operator, but I'm not sure.
                        // TODO: Make this and other key operations work with operator overloading. Though Map is already an interface that anything can implement, so maybe not.
                        responseData = TokenizingJson.getJsonElement(true)
                    } catch (e: ClassCastException) {
                        responseData = TokenizingJson.getJsonElement(false)
                    }
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "callable" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    responseData = TokenizingJson.getJsonElement(leaf is FunctionDispatcher)
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "args" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    responseData = TokenizingJson.getJsonElement(
                        mapOf<String, List<List<String?>>>(
                            *((leaf as FunctionDispatcher).functions.map {
                                it.toString() to it.parameters.map { listOf<String?>(it.name?.toString(), it.type.toString()) }
                            }).toTypedArray()
                            // The innermost listOf should semantically be a Pair as per the spec in Module.md, but a List seems safer to serialize.
                        )
                    )
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                        responseFlags.add(KnownFlag.Exception.name)
                }
            }
            "docstring" -> {
                try {
                    val packetData = ScriptingPacketPathedData(packet)
                    val leaf = Reflection.resolveInstancePath(
                        if (packetData.use_root) packetData.root else scope,
                        packetData.path
                    )
                    responseData = TokenizingJson.getJsonElement(leaf.toString())
                } catch (e: Exception) {
                    responseData = JsonPrimitive(e.stringifyException())
                    responseFlags.add(KnownFlag.Exception.name)
                }
            }
            else -> {
                throw IllegalArgumentException("Unknown action received in scripting request packet: ${packet.action}")
            }
        }
        return ScriptingPacket(action, packet.identifier, responseData, responseFlags)
    }

}
