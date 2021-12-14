package com.unciv.scripting.api

import com.unciv.UncivGame
import java.util.Collections.newSetFromMap
import java.util.WeakHashMap
import kotlin.NoSuchElementException

object AllScriptingApiInstanceRegistries {
    private val registries = newSetFromMap(WeakHashMap<ScriptingApiInstanceRegistry, Boolean>())
    // Apparently this will be a WeakSet? Oh, all sets just wrap Maps?
    init {
        UncivGame.Current.disposeCallbacks.add { // Runtime.getRuntime().addShutdownHook() also works, but I probably prefer to unify with existing shutdown behaviour.
            val allKeys = getAllKeys()
            if (allKeys.isNotEmpty()) {
                println("WARNING: ${allKeys.size} ScriptingApiInstanceRegistry()s still have keys in them:")
                println("\t" + allKeys.map { "${it.value.size} keys in ${it.key}\n\t\t"+it.value.joinToString("\n\t\t") }.joinToString("\n\t"))
            }
        }
    }
    fun add(registry: ScriptingApiInstanceRegistry) {
        registries.add(registry)
    }
    fun getAllKeys(): Map<ScriptingApiInstanceRegistry, Set<String>> {
        return registries.filter { it.isNotEmpty() }.associateWith { it.keys }
    }
}

/**
 * Namespace in ScriptingScope().apiHelpers, for scripts to do their own memory management by keeping references to objects alive.
 *
 * Wraps a MutableMap<>().
 *
 * @throws IllegalArgumentException On an attempted assignment colliding with an existing key.
 * @throws NoSuchElementException For reads and removals at non-existent keys.
 */
object ScriptingApiInstanceRegistry: MutableMap<String, Any?> { // This is a singleton as ScriptingScope is a singleton now, but it's probably best to keep it with the same semantics as a class.
    init {
        AllScriptingApiInstanceRegistries.add(this)
    }
    private val backingMap = mutableMapOf<String, Any?>()
    override val entries
        get() = backingMap.entries
    override val keys
        get() = backingMap.keys
    override val values
        get() = backingMap.values
    override val size
        get() = backingMap.size
    override fun containsKey(key: String) = backingMap.containsKey(key)
    override fun containsValue(value: Any?) = backingMap.containsValue(value)
    override fun get(key: String): Any? {
        if (key !in this) {
            throw NoSuchElementException("\"${key}\" not in ${this}.")
        }
        return backingMap.get(key)
    }
    override fun isEmpty() = backingMap.isEmpty()
    override fun clear() = backingMap.clear()
    override fun put(key: String, value: Any?): Any? {
        println("""

            INFO: Assigning ${key} directly in ScriptingApiInstanceRegistry(). It is recommended that every script/mod do this only once per application lifespan, creating its own mapping under the registry for further assignments named according to the following format:
                <Language>-<'mod'|'module'|'package'>:<Author>/<Filename>
                E.G.: registeredInstances["python-module:myName/myCoolScript"] = {"some_name": someToken}

            """.trimIndent())
        if (key in this) {
            throw IllegalArgumentException("\"${key}\" already in ${this}.")
        }
        return backingMap.put(key, value)
    }
    override fun putAll(from: Map<out String, Any?>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }
    override fun remove(key: String): Any? {
        if (key !in this) {
            throw NoSuchElementException("\"${key}\" not in ${this}.")
        }
        return backingMap.remove(key)
    }
}
