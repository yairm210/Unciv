package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.reflection.Reflection
import com.unciv.scripting.utils.stringifyException
import com.unciv.scripting.utils.TokenizingJson
import kotlin.random.Random
import kotlin.reflect.KCallable
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID


// See Module.md a description of the protocol.


@Serializable
data class ScriptingPacket(
    var action: String?,
    var identifier: String?,
    var data: JsonElement? = null,
    // This can have arbitrary structure, values/types, and depth. So since it lives so close to the IPC protocol anyway, JsonElement is the easiest way to represent and serialize it.
    var flags: Collection<String> = listOf()
) {

    companion object {
        fun fromJson(string: String): ScriptingPacket = TokenizingJson.json.decodeFromString<ScriptingPacket>(string)
    }

    fun toJson() = TokenizingJson.json.encodeToString(this)

    fun hasFlag(flag: ScriptingProtocol.KnownFlag) = flag.value in flags

}


class ScriptingProtocol(val scope: Any, val instanceSaver: MutableList<Any?>? = null) {

    // Adds all returned and potentially tokenized instances in responses to `instanceSaver` to save them from garbage collection.
    // `instanceSaver` should not be usd as long-term storage.
    // Whatever's using this protocol instance should clear `instanceSaver` as soon as whatever's running at the other end of the protocol has had a chance to create references elsewhere (E.G. ScriptingScope.apiHelpers) for any instances that it needs.

    enum class KnownFlag(val value: String) {
        PassMic("PassMic")
    }

    companion object {

        fun makeUniqueId(): String {
            return "${System.nanoTime()}-${Random.nextBits(30)}-${UUID.randomUUID().toString()}"
        }

        val responseTypes = mapOf<String?, String?>(
            // Deliberately repeating myself because I don't want to imply a hard specification for the name of response packets.
            "motd" to "motd_response",
            "autocomplete" to "autocomplete_response",
            "exec" to "exec_response",
            "terminate" to "terminate_response",
            "read" to "read_response",
            "assign" to "assign_response",
            "delete" to "delete_response",
            "dir" to "dir_response",
            "keys" to "keys_response",
            "length" to "length_response",
            "contains" to "contains_response",
            "callable" to "callable_response",
            "args" to "args_response",
            "docstring" to "docstring_response"
        )

        fun enforceIsResponse(original: ScriptingPacket, response: ScriptingPacket): ScriptingPacket {
            if (!(
                (response.action == responseTypes[original.action]!!)
                && response.identifier == original.identifier
            )) {
                throw IllegalStateException("Scripting packet response does not match request ID and type: ${original}, ${response}")
            }
            return response
        }

    }

    object makeActionRequests {
        fun motd() = ScriptingPacket(
            "motd",
            makeUniqueId(),
            JsonNull,
            listOf(KnownFlag.PassMic.value)
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

    fun trySaveObject(obj: Any?): Any? {
        if (instanceSaver != null) {
            instanceSaver.add(obj)
        }
        return obj
    }

    fun makeActionResponse(packet: ScriptingPacket): ScriptingPacket {
//        var action: String? = null
        val action = ScriptingProtocol.responseTypes[packet.action]!!
        var data: JsonElement? = null
        var flags = mutableListOf<String>()
        var value: JsonElement = JsonNull
        var exception: JsonElement = JsonNull
        when (packet.action) {
            // There's a lot of repetition here, because I don't want to enforce any specification on what form the request and response data fields for actions must take.
            // I prefer to try to keep the code for each response type independent enough to be readable on its own.
            // This is kinda the reference (and only) implementation of the protocol spec. So the serialization and such can be and is handled with functions, but the actual structure and logic of each response should be hardcoded manually IMO.
            "read" -> {
                try {
                    value = TokenizingJson.getJsonElement(trySaveObject(
                        Reflection.resolveInstancePath(
                            scope,
                            TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                        )
                    ))
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "assign" -> {
                try {
                    Reflection.setInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!),
                        TokenizingJson.getJsonReal((packet.data as JsonObject)["value"]!!)
                    )
                } catch (e: Exception) {
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = exception
            }
            "delete" -> {
                try {
                    Reflection.removeInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                } catch (e: Exception) {
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = exception
            }
            "dir" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf!!::class.members.map{it.name})
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "keys" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement((leaf as Map<Any, *>).keys)
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "length" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    try {
                        value = TokenizingJson.getJsonElement((leaf as Array<*>).size)
                        // Once/If I get Reflection working with properties/Transients/whatever, I could replace these casts with a single reflective call.
                    } catch (e: Exception) {
                        try {
                            value = TokenizingJson.getJsonElement((leaf as Map<*, *>).size)
                        } catch (e: Exception) {
                            value = TokenizingJson.getJsonElement((leaf as Collection<*>).size)
                        }
                    }
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "contains" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    val _check = TokenizingJson.getJsonReal((packet.data as JsonObject)["value"]!!)
                    try {
                        value = TokenizingJson.getJsonElement(_check in (leaf as Map<Any?, Any?>))
                    } catch (e: Exception) {
                        value = TokenizingJson.getJsonElement(_check in (leaf as Collection<Any?>))
                    }
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "callable" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf is KCallable<*>)
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "args" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement((leaf as KCallable<*>).parameters.map { listOf<String>(it.name.toString(), it.type.toString()) })
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "docstring" -> {
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf.toString())
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(stringifyException(e))
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            else -> {
                throw IllegalArgumentException("Unknown action received in scripting request packet: ${packet.action}")
            }
        }
        return ScriptingPacket(action, packet.identifier, data, flags)
    }

}


