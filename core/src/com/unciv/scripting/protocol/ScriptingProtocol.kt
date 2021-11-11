package com.unciv.scripting.protocol

import com.unciv.scripting.AutocompleteResults
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.reflection.Reflection
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

/*
    A single IPC action consists of one request packet and one response packet.
    A request packet should always be followed by a response packet if it has an action.
    If a request packet has a null action, then it should not be followed by a response. This is to let flags be sent without generating useless responses.

    Responses do not have to be sent in the same order as their corresponding requests. New requests can be sent out while old ones are left "open"— E.G., if creating a response requires requesting new information.
    
    (So far, I think all the requests and responses follow a "stack" model, though. If request B is sent out before response A is received, then response B still gets received before response A, like parentheses. The time two requests remain open can be completely nested subsets or supersets, or they can be completely separated in series, but they don't partially overlap.)
    
    (That said, none of these are hard requirements. If you want to do something fancy with coroutines or whatever and dispatch to multiple open request handlers, and you can make it both stable and language-aggnostic, go right ahead.)

    Both the Kotlin side and the script interpreter can send and receive packets, but not necessarily at all times.

    (The current loop is described in a comment in ScriptingReplManager.kt. Kotlin initiates a scripting exec, during which the script interpreter can request values from Kotlin, and at the end of which the script interpreter sends its STDOUT response to the Kotlin side.)

    A single packet is a JSON string of the form:

    ```
    {
        "action": String?,
        "identifier": String?,
        "data": Any?,
        "flags": Collection<String>
    }
    ```

    Identifiers should be set to a unique value in each request.
    Each response should have the same identifier as its corresponding request.
    Upon receiving a response, both its action and identifier should be checked to match the relevant request.
    
    ---
    
    The data field is allowed to represent any hierarchy, of objects of any types.
    If it must represent objects that are not possible or not useful to serialize, then unique identifying token strings should be generated and sent in their place.
    If those strings are received at any hierarchical depth in the data field of any later packets, then they are to be substituted with their original objects in all uses of the information from those packets.
    If the original object of a received token string no longer exists, then an exception should be thrown, and handled as would any exception at the point where the object is to be accessed.
    
    (Caveats in practice: The scripting API design is highly asymmetric in that script interpreter needs a lot of access to the Kotlin side's state, but the Kotlin side should rarely or never need the script interpreter's state, so the script interpreter doesn't have to bother implementing its own arbitrary object serialization. Requests sent by the Kotlin side also all have very simple response formats because of this, while access to and use of complicated Kotlin-side objects is always initiated by a request from the script interpreter while in the execution loop of its REPL, so the Kotlin side bothers implementing arbitrary object tokenization only when receiving requests and not when receiving responses. Exceptions from reifying invalid received tokens on the Kotlin side should be handled as would any other exceptions at their code paths, but because such tokens are only used on the Kotlin side when preparing a response to a received request from the scripting side, that currently means sending a response packet that is marked in some way as representing an exception and then carrying on as normal.)
    
    ---

    Some action types, data formats, and expected response data formats for packets sent from the Kotlin side to the script interpreter include:
    
        ```
        'motd': null ->
            'motd_response': String
        ```
            
        ```
        'autocomplete': {'command': String, 'cursorpos': Int} ->
            'autocomplete_response': Collection<String> or String
            //List of matches, or help text to print.
        ```
            
        ```
        'exec': String ->
            'exec_response': String
            //REPL print.
        ```
            
        ```
        'terminate': null ->
            'terminate_response': String?
            //Error message or null.
        ```
            
    The above are basically a mirror of ScriptingBackendBase, so the same interface can be implemented in the scripting language.
    
    ---

    Some action types, data formats, and expected response types and formats for packets sent from the script interpreter to the Kotlin side include:
    
        ```
        'read': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'read_reponse': {'value': Any?, 'exception': String?}
            //Attribute/property access, by list of `PathElement` properties.
        ```
            
        ```
        //'call': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>, 'args': Collection<Any>, 'kwargs': Map<String, Any?>} ->
            //'call_response': {'value': Any?, 'exception': String?}
            //Method/function call.
            //Deprecated and removed. Instead, use `"action":"read"` with a "path" that has `"type":"call"` element(s) as per `PathElement`.
        ```
            
        ```
        'assign': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>, 'value': Any} ->
            'assign_response': String?
            //Error message or null.
        ```
            
        ```
        'dir': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'dir_response': {'value': Collection<String>, 'exception': String?}
            //Names of all members/properties/attributes/methods.
        ```
            
        ```
        'keys': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'keys_response': {'value': Collection<Any?>, 'exception': String?}
        ```
            
        ```
        'length': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'length_response': {'value': Int?, 'exception': String?}
        ```
        
        ```
        'contains': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>, 'value': Any?} ->
            'contains_response': {'value': Boolean?, 'exception': String?}
        ```
        
        ```
        'callable': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'callable_response': {'value': Boolean?, 'exception': String?}
            //Used by Python autocompleter to add opening bracket to methods and function suggestions. Quite useful for exploring API at a glance.
        ```
            
        ```
        'args': 'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'args_response': {'value': List<Pair<String?, String>>?, 'exception': String?}
            //Names and types of arguments accepted by a function.
            //Currently just used by Python autocompleter to generate help text.
            //Could also be used to control functions in scripting environment. If so, then names of types should be standardized.
        ```
        
        ```
        'docstring': {'path': List<{'type':String, 'name':String, 'params':List<Any?>}>} ->
            'docstring_response': {'value': String?, 'exception': String?}
            //Used by Python wrappers and autocompleter to get help text showing arguments and types for callables. Useful for exploring API without having to browse code.
        ```
        
    The paths fields in some of the data fields mirror PathElement.
    
    ---
        
    Flags are string values for communicating extra information that doesn't need a separate packet or response. Depending on the flag and action, they may be contextual to the packet, or they may not. I think I see them mostly as a way to semantically separate meta-communication about the protocol from actual requests for actions:
        ```
        'PassMic'
            //Indicates that the sending side will now begin listening instead, and the receiving side should send the next request.
            //Sent by Kotlin side at start of script engine startup/MOTD, autocompletion, and execution to allow script to request values, and should be sent by script interpreter immediately before sending response with results of execution.
        ```
        
        ```
        //'Exception'
            //Indicates that this packet is associated with an error.
            //Not implemented. Currently Kotlin-side exceptions while processing a request are communicated to the script interpreter with an error message at an expected place in the data field of the response, and exceptions in the script interpreter while executing a command are stringified and returned to the Kotlin side the same as any REPL output.
        ```
        
    ---
        
    Thus, at the IPC level, all foreign backends will actually use the same language, which is this JSON-based protocol. Differences between Python, JS, Lua, etc. will all be down to how they interpret the "exec", "autocomplete", and "motd" actions differently, which each high-level scripting language is free to implement as works best for it.

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
        fun fromJson(string: String): ScriptingPacket = TokenizingJson.json.decodeFromString<ScriptingPacket>(string)
    }
    
    fun toJson() = TokenizingJson.json.encodeToString(this)
    
    fun hasFlag(flag: ScriptingProtocol.KnownFlag) = flag.value in flags
    
}


class ScriptingProtocol(val scope: Any) {
    
    enum class KnownFlag(val value: String) {
        PassMic("PassMic")
    }

    companion object {
        
        fun makeUniqueId(): String {
            return "${System.nanoTime()}-${Random.nextBits(30)}-${UUID.randomUUID().toString()}"
        }
        
        val responseTypes = mapOf(
            "motd" to "motd_response",
            "autocomplete" to "autocomplete_response",
            "exec" to "exec_response" // TODO
        )
    
        fun enforceIsResponse(original: ScriptingPacket, response: ScriptingPacket): ScriptingPacket {
            if (!(
                ((response.action == null && original.action == null) || response.action == original.action.toString() + "_response")
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

    fun makeActionResponse(packet: ScriptingPacket): ScriptingPacket {
        var action: String? = null
        var data: JsonElement? = null
        var flags = mutableListOf<String>()
        var value: JsonElement = JsonNull
        var exception: JsonElement = JsonNull
        when (packet.action) {
            // There's a lot of repetition here, because I don't want to enforce any specification on what form the request and response data fields for actions must take.
            // I prefer to try to keep the code for each response type independent enough to be readable on its own.
            "read" -> {
                action = "read_response"
                try {
                    value = TokenizingJson.getJsonElement(
                        Reflection.resolveInstancePath(
                            scope,
                            TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
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
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!),
                        TokenizingJson.getJsonReal((packet.data as JsonObject)["value"]!!)
                    )
                } catch (e: Exception) {
                    exception = JsonPrimitive(e.toString())
                }
                data = exception
            }
            "dir" -> {
                action = "dir_response"
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf!!::class.members.map{it.name})
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "length" -> {
                action = "length_response"
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
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "keys" -> {
                action = "keys_response"
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement((leaf as Map<Any, *>).keys)
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "contains" -> {
                action = "contains_response"
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
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "callable" -> {
                action = "callable_response"
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf is KCallable<*>)
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "args" -> {
                action = "args_response"
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement((leaf as KCallable<*>).parameters.map { listOf<String>(it.name.toString(), it.type.toString()) })
                } catch (e: Exception) {
                    value = JsonNull
                    exception = JsonPrimitive(e.toString())
                }
                data = JsonObject(mapOf(
                    "value" to value,
                    "exception" to exception
                ))
            }
            "docstring" -> {
                action = "docstring_response"
                try {
                    val leaf = Reflection.resolveInstancePath(
                        scope,
                        TokenizingJson.json.decodeFromJsonElement<List<Reflection.PathElement>>((packet.data as JsonObject)["path"]!!)
                    )
                    value = TokenizingJson.getJsonElement(leaf.toString())
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
                throw IllegalArgumentException("Unknown action received in scripting request packet: ${packet.action}")
            }
        }
        return ScriptingPacket(action, packet.identifier, data, flags)
    }

}


