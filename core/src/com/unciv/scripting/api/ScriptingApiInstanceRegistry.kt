package com.unciv.scripting.api

import java.util.Collections.newSetFromMap
import java.util.WeakHashMap
import kotlin.NoSuchElementException
import kotlin.concurrent.thread

object AllScriptingApiInstanceRegistries {
    private val registries = newSetFromMap(WeakHashMap<ScriptingApiInstanceRegistry, Boolean>())
    // Apparently this will be a WeakSet? Oh, all sets just wrap Maps?
    fun init() {
        Runtime.getRuntime().addShutdownHook(
            // TODO: Move uses of this into UncivGame.dispose()?
            // TODO: Allow arbitrary callbacks to be added for UncivGame.dispose().
            // TODO: Also, doesn't actually work.
            thread(start = false, name = "Check ScriptingApiInstanceRegistry()s are empty.") {
                val allkeys = getAllKeys()
                if (allkeys.isNotEmpty()) {
                    println("WARNING: ${allkeys.size} ScriptingApiInstanceRegistry()s still have keys in them:")
                    println("\t" + allkeys.map { "${it.value.size} keys in ${it.key}\n\t\t"+it.value.joinToString("\n\t\t") }.joinToString("\n\t"))
                }
            }
        )
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
object ScriptingApiInstanceRegistry: MutableMap<String, Any?> {
    private val backingMap = mutableMapOf<String, Any?>()
//    fun init() {
//        AllScriptingApiInstanceRegistries.add(this)
//    }
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
    override fun get(key: String): Any? { //FIXME: Operator modifiers?
        if (key !in this) {
            throw NoSuchElementException("\"${key}\" not in ${this}.")
        }
        return backingMap.get(key)
    }
    override fun isEmpty() = backingMap.isEmpty()
    override fun clear() = backingMap.clear()
    override fun put(key: String, value: Any?): Any? {
        println("\nAssigning ${key} directly in ScriptingApiInstanceRegistry(). It is recommended that every script/mod do this only once per application lifespan, creating its own mapping under the registry for further assignments named according to the following format:\n\t<Language>-<'mod'|'module'|'package'>:<Author>/<Filename>\n\tE.G.: \"python-module:myName/myCoolScript\"\n")
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
