package com.unciv.models.modscripting

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue
import com.unciv.scripting.ScriptingState

class ScriptedModLoadable(val modRules: ScriptedModRules) {

}

class ScriptedModRules {// See, try to follow conventions of, TilesetAndMod maybe?

    // Getting this deserializing from JSON was a humongous, humongous pain. GDX seems to take only three levels of nested mappings before it stops knowing what to do with the arrays at the deepest level. I could have just used KotlinX instead as I'm bringing in the dependency anyway, but for some reason I seem to want to align the parts of the scripting API that interact with the existing codebase with the tools that are already used.

    // Recommend switching to KotlinX if anyone else decides to change this. See: ScriptingPacket, List<PathElement>, .decodeFromJsonElement, TokenizingJson— It's *so* easy (once you have it set up).

    companion object {
        fun fromJsonString(jsonText: String): ScriptedModRules {
            val mod = ScriptedModRules()
            val jsonValue = JsonReader().parse(jsonText)
            val json = Json().apply { setEnumNames(true) }
            mod.apply {
                name = jsonValue.getString("name") ?: "?"
                language = json.fromJson(ModScriptingLanguage::class.java, jsonValue.getString("language"))
                handlers = ScriptedModHandlerRoot.fromJson(jsonValue.get("handlers"))
            }
            return mod
        }
    }

    var name = ""
    var language: ModScriptingLanguage? = null
    var handlers = ScriptedModHandlerRoot()

    val backend by lazy { // TODO: This should be discarded when unloading.
        val backendType = language?.backendType
        if (backendType == null)
            null
        else
            ScriptingState.spawnBackend(backendType).backend
    }

    val handlersByType by lazy {
        val flatmap = mutableMapOf<HandlerType, ScriptedModHandlerSet>()
//        if (handlers == null) {
//            return@lazy flatmap.toMap()
//        }
        for ((contextId, contextHandlers) in handlers.entries) {
            for ((handlerId, handlerSet) in contextHandlers) {
                val handlerType = HANDLER_DEFINITIONS[ContextId.valueOf(contextId), HandlerId.valueOf(handlerId)]
                if (handlerType in flatmap) {
                    throw IllegalArgumentException("Handler type $handlerType defined more than once in mod $name!")
                }
                flatmap[handlerType] = handlerSet
            }
        }
        return@lazy flatmap.toMap()
    }
}



class ScriptedModHandlerRoot: HashMap<String, ScriptedModHandlerContext>() {
    companion object {
        fun fromJson(jsonValue: JsonValue?): ScriptedModHandlerRoot {
            val root = ScriptedModHandlerRoot()
            if (jsonValue == null) {
                return root
            }
            for (context in jsonValue) {
                root[context.name] = ScriptedModHandlerContext.fromJson(context)
            }
            return root
        }
    }
}

class ScriptedModHandlerContext: HashMap<String, ScriptedModHandlerSet>() {
    companion object {
        fun fromJson(jsonValue: JsonValue?): ScriptedModHandlerContext {
            val context = ScriptedModHandlerContext()
            if (jsonValue == null) {
                return context
            }
            for (handler in jsonValue) {
                context[handler.name] = ScriptedModHandlerSet.fromJson(handler)
            }
            return context
        }
    }
}

class ScriptedModHandlerSet {

    companion object {
        fun fromJson(jsonValue: JsonValue?): ScriptedModHandlerSet {
            val handlerSet = ScriptedModHandlerSet()
            if (jsonValue == null) {
                return handlerSet
            }
            handlerSet.apply {
                background = jsonValue.get("background")?.asStringArray()?.toList() ?: listOf()
                foreground = jsonValue.get("foreground")?.asStringArray()?.toList() ?: listOf()
            }
            return handlerSet
        }
    }

    var background = listOf<String>() // Run first, in worker threads.

    var foreground = listOf<String>() // Run after, in main thread. UI-affecting

}

// I mean, seriously. It's over 100 lines with poorly documented GDX functions just to deserialize three properties. Maybe there's an easier way… Rewrite it please if you know what that is.
