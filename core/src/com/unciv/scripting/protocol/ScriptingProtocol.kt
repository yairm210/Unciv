package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingScope
import com.unciv.scripting.reflection.Reflection
//import com.badlogic.gdx.utils.Json
import kotlin.random.Random
//import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
//import kotlinx.serialization.descriptors.SerialDescriptor
//import kotlinx.serialization.descriptors.PrimitiveKind
//import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
//import kotlinx.serialization.encoding.Decoder
//import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.util.UUID

/*
    A single IPC action consists of one request packet and one response packet.
    A request packet should always be followed by a response packet if it has an action.
    If a request packet has a null action, then it should not be followed by a response. This is to let flags be sent without generating useless responses.

    However, responses do not have to be sent in the same order as their correspond requests. New requests can be sent out while old ones are left "open"— E.G., if creating a response requires requesting new information.

    Both the Kotlin side and the script interpreter can send and receive packets, but not necessarily at all times.

    (The current loop is described in a comment in ScriptingReplManager.kt. Kotlin initiates a scripting exec, during which the script interpreter can request values from Kotlin, and at the end of which the script interpreter sends its STDOUT response.)

    A single packet is a standard JSON string of one of the form:

    {
        'action': String?,
        'identifier': String?,
        'data': Any?,
        'flags': Collection<String>
    }

    Identifiers should be set to a unique value in each request.
    Each response should check have the same identifier as its corresponding request.
    Upon receiving a response, both its action and identifier should be checked to match the relevant request.

    Some action types, data formats, and expected response data formats for packets sent from the Kotlin side to the script interpreter include:
        'motd': null -> 'motd_response': String
        'autocomplete': {'command': String, 'cursorpos': Int} -> 'autocomplete_response': Collection<String> or String //List of matches, or help text to print.
        'exec': String -> 'exec_response': String
        'terminate': null -> 'terminate_response': String? //Error message or null.
    These are basically a mirror of ScriptingBackendBase, so the same interface can be implemented in the scripting language.

    Some action types, data formats, and expected response types and formats for packets sent from the script interpreter to the Kotlin side include:
        'read': {'path': String} -> 'read_reponse': {'value': Any?, 'exception': String?} //Attribute/property access.
        'call': {'path': String, 'args': Collection<Any>, 'kwargs': Map<String, Any?>} -> 'call_response': {'value': Any?, 'exception': String?} //Method/function call.
        'assign': {'path': String, 'value': Any} -> 'assign_response': String? //Error message or null.
        'dir': {'path': String} -> 'dir_response': {'value': Collection<String>, 'exception': String?} //Names of all members/properties/attributes/methods.
        //'items': {'path': Strign} -> 'keys_response': {}
        //'args': {'path'} ->  'args_response': Collection<Pair<String, String>> //Names and types of arguments accepted by a function.
        //'length': {'path': String} -> 'length_response': Int
        
    Flags are string values for communicating extra information that doesn't need a separate packet or response. Depending on the flag and action, they may be contextual to the packet, or they may not. I think I see them mostly as a way to semantically separate meta-communication about the protocol from actual requests for actions:
        'PassMic' //Indicates that the sending side will now begin listening instead, and the receiving side should send the next request. Sent by Kotlin side at start of script execution to allow script to request values, and should be sent by script interpreter immediately before sending response with results of execution.
        
    Thus, at the IPC level, all foreign backends will actually use the same language, which is this JSON-based protocol. Differences between Python, JS, Lua, etc. will all be down to how they interpret the "exec", "autocomplete", and "motd" actions differently, which each high-level scripting language is free to implement as works best for it.

*/

//object AnySerializer: KSerializer<Any?> {
//    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Any", PrimitiveKind.STRING)
//    
//    override fun deserialize(decoder: Decoder): Any? {
//        return decoder.decodeString()
//    }
//    
//    override fun serialize(encoder: Encoder, value: Any?): Unit {
//        encoder.encodeString("Test")
//    }
//    
//    //Could put the ScriptingObjectIndex stuff here.
//}

//class BoxSerializer<T>(private val dataSerializer: KSerializer<T>) : KSerializer<Any?> {
//    override val descriptor: SerialDescriptor = dataSerializer.descriptor
//    override fun serialize(encoder: Encoder, value: Any?) = dataSerializer.serialize(encoder, 5)
//    override fun deserialize(decoder: Decoder): Any? = (dataSerializer.deserialize(decoder))
//    //Could put the ScriptingObjectIndex stuff here.
//    //It'd probably be easier to just use the LibGDX JSON tools.
//}

val json = Json {
    explicitNulls = true; //Disable these if it becomes a problem.
    encodeDefaults = true
}

@Serializable
data class ScriptingPacket(
    var action: String?,
    var identifier: String?,
//    @Serializable(with = BoxSerializer::class)
    var data: JsonElement? = null,
    var flags: Collection<String> = listOf()
) {
    companion object {
        fun fromJson(string: String): ScriptingPacket = json.decodeFromString<ScriptingPacket>(string)
    }
    
    fun toJson() = json.encodeToString(this)
    
    fun hasFlag(flag: ScriptingProtocol.KnownFlag) = flag.value in flags
}


class ScriptingProtocol(val scriptingScope: ScriptingScope) {

    companion object {
        fun transformPath(path: List<Reflection.PathElement>): List<Reflection.PathElement> {
            return path.map{ Reflection.PathElement(
                type = it.type,
                name = it.name,
                doEval = it.doEval,
                params = it.params.map( { p -> ScriptingObjectIndex.getReal(p) })
            ) }
        }
        
        fun makeUniqueId(): String {
            return "${System.nanoTime()}-${Random.nextBits(30)}-${UUID.randomUUID().toString()}"
        }
        
        val responseTypes = mapOf(
            "motd" to "motd_response",
            "autocomplete" to "autocomplete_response",
            "exec" to "exec_response"
        )
    
        fun enforceIsResponse(original: ScriptingPacket, response: ScriptingPacket): ScriptingPacket {
            if (!(
                ((response.action == null && original.action == null) || response.action == original.action.toString() + "_reponse")
                || response.identifier == original.identifier
            )) {
                throw IllegalStateException("Scripting packet response does not match request ID and type: ${original}, ${response}")
            }
            return response
        }
        
        fun <T> getJsonElement(value: T?): JsonElement {
            if (value is JsonElement) {
                return value
            }
            if (value is Map<*, *>) {
                return JsonObject( (value as Map<String, Any?>).mapValues{ getJsonElement(it.value) } )
            }
            if (value is Collection<*>) {
                return JsonArray(value.map{ getJsonElement(it) })
            }
            if (value is String) { 
                return JsonPrimitive(value as String)
            }
            if (value is Int || value is Long || value is Float || value is Double) { 
                return JsonPrimitive(value as Number)
            }
            if (value is Boolean) { 
                return JsonPrimitive(value as Boolean)
            }
            if (value == null) {
                return JsonNull
            }
            return JsonPrimitive(ScriptingObjectIndex.getToken(value))
        }
        
        fun getJsonReal(value: JsonElement): Any? {
            if (value is JsonNull || value == JsonNull) {
                return null
            }
            if (value is JsonArray) {
                return (value as List<JsonElement>).map{ getJsonReal(it) }// as List<Any?>
            }
            if (value is JsonObject) {
                return (value as Map<String, JsonElement>).mapValues{ getJsonReal(it.value) }// as Map<String, Any?>
            }
            if (value is JsonPrimitive) {
                val v = value as JsonPrimitive
                if (v.isString) {
                    return ScriptingObjectIndex.getReal(v.content)
                } else {
                    return v.content.toIntOrNull()
                        ?: v.content.toDoubleOrNull()
                        ?: v.content.toBooleanStrictOrNull()
                        ?: v.content
                }
            }
            throw IllegalArgumentException("Unrecognized type of JsonElement: ${value::class}/${value}")
        }
    }

    object makeActionRequests {
        fun motd() = ScriptingPacket(
            "motd",
            makeUniqueId()
        )
        fun autocomplete(command: String, cursorPos: Int?) = ScriptingPacket(
            "autocomplete",
            makeUniqueId(),
            JsonObject(mapOf("command" to JsonPrimitive(command), "cursorpos" to JsonPrimitive(cursorPos))),
            listOf(KnownFlag.PassMic.value)
        )
        fun exec(command: String) = ScriptingPacket(
            "exec",
            makeUniqueId(),
            JsonPrimitive(command),
            listOf(KnownFlag.PassMic.value)
        )
        fun terminate() = ScriptingPacket(
            "terminate",
            makeUniqueId()
        )
    }
    
    object parseActionResponses {
        fun motd(packet: ScriptingPacket): String = (packet.data as JsonPrimitive).content
        
        fun autocomplete(packet: ScriptingPacket): AutocompleteResults =
            if (packet.data is JsonArray) 
                AutocompleteResults((packet.data as List<JsonElement>).map{ (it as JsonPrimitive).content })
            else
                AutocompleteResults(listOf(), true, (packet.data as JsonPrimitive).content)
                
        fun exec(packet: ScriptingPacket): String = (packet.data as JsonPrimitive).content
        
        fun terminate(packet: ScriptingPacket): Exception? =
            if (packet.data == JsonNull || packet.data == null)
                null
            else
                RuntimeException((packet.data as JsonPrimitive).content)
    }
    
    enum class KnownFlag(val value: String) {
        PassMic("PassMic")
    }

    fun makeActionResponse(packet: ScriptingPacket): ScriptingPacket {
        var action: String? = null
        var data: JsonElement? = null
        var flags = mutableListOf<String>()
        var value: JsonElement = JsonNull
        var exception: JsonElement = JsonNull
        when (packet.action) {
            "read" -> {
                action = "read_response"
                try {
                    value = getJsonElement(
                        Reflection.evalKotlinString(
                            scriptingScope,
                            (((packet.data as JsonObject)["path"]) as JsonPrimitive).content
                        )
                    )
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "call" -> {
                action = "call_response"
                try {
                    var path = Reflection.parseKotlinPath((((packet.data as JsonObject)["path"]) as JsonPrimitive).content) as MutableList<Reflection.PathElement>
                    val params = getJsonReal((packet.data as JsonObject)["args"]!!) as List<Any?>
                    val kwparams = getJsonReal((packet.data as JsonObject)["kwargs"]!!) as Map<String, Any?>
                    if (kwparams.size > 0) {
                        throw UnsupportedOperationException("Keyword arguments are not currently supported: ${kwparams}")
                    }
                    path.add(Reflection.PathElement(
                        type = Reflection.PathElementType.Call,
                        name = "",
                        doEval = false,
                        params = params
                    ))
                    value = getJsonElement(
                        Reflection.resolveInstancePath(
                            scriptingScope,
                            transformPath(path)
                        )
                    )
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "assign" -> {
                action = "assign_response"
                try {
                    Reflection.setInstancePath(
                        scriptingScope,
                        Reflection.parseKotlinPath((((packet.data as JsonObject)["path"]) as JsonPrimitive).content),
                        getJsonReal((packet.data as JsonObject)["value"]!!)
                    )
                } catch (e: Exception) {
                    exception = JsonPrimitive(e.toString())
                }
                data = exception
            }
            "dir" -> {
                action = "dir_response"
                try {
                    val leaf = Reflection.evalKotlinString(
                        scriptingScope,
                        (((packet.data as JsonObject)["path"]) as JsonPrimitive).content
                    )
                    value = getJsonElement(leaf!!::class.members.map{it.name})
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            else -> {
                throw IllegalArgumentException("Unknown action received in scripting packet: ${packet.action}")
            }
        }
        return ScriptingPacket(action, packet.identifier, data, flags)
    }

//    fun resolvePath() { }
}


