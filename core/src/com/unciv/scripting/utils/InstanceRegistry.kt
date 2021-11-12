package com.unciv.scripting.utils


class InstanceRegistry(): MutableMap<String, Any?> {
    // Namespace in ScriptingScope().apiHelpers, for scripts to do their own memory management by keeping references to objects alive. Wraps a MutableMap<>().
    // Currently all it does is throw an exception on an assignment colliding with an existing key, and reads and removals for non-existent keys..
    // Was going to have functions named to fit creating and freeing fields, but so far Python's item assignment and deletion syntaxes are plenty clean 
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
        if (key in this) {
            throw IllegalArgumentException("\"${key}\" already in ${this}.")
        }
        return backingMap.put(key, value)
    }
    override fun putAll(from: Map<out String, Any?>) = backingMap.putAll(from)
    override fun remove(key: String): Any? {
        if (key !in this) {
            throw NoSuchElementException("\"${key}\" not in ${this}.")
        }
        return backingMap.remove(key)
    }
}
