package com.unciv.scripting.utils

// Functions may have side effects.
// Containers, however, should change neither their own nor other data's public state on their own.
// Therefore, for certain models of scripting language bindings, it is easier to make guarantees about and expose flexible semantics for key indexing than for function calls.
// The classes here help expose functions as Map-like instances, letting simple, side-effect-free functions with direct mappings from input to output be presented in scripting language bindings without having to worry about side effects from E.G. repeated or deferred calling.

abstract class StatelessMap<K, V>: Map<K, V> {
    protected fun noStateError(): Nothing = throw(UnsupportedOperationException("Cannot access backing state of ${this::class.simpleName} by default."))
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = noStateError()
    override val keys: MutableSet<K> get() = noStateError()
    override val values: MutableCollection<V> get() = noStateError()
    override val size: Int get() = noStateError()
    override fun containsKey(key: K): Boolean = noStateError()
    override fun containsValue(value: V): Boolean = noStateError()
    override fun isEmpty(): Boolean = noStateError()
}

//
interface InvokableMap<K, V>: Map<K, V> {
    operator fun invoke(key: K): V? = get(key)
}

// Lazy Map of indeterminate size.

// Memoizes a single-argument function.
// Generates values using the provided function the first time each key is encountered.
// Implements both invocation and indexing.

// @property func The function that returns the value for a given key.
// @property exposeState Whether to expose the content-specific members of the backing map.
class LazyMap<K, V>(val func: (K) -> V, val exposeState: Boolean = false): StatelessMap<K, V>(), InvokableMap<K, V> {
    // Benefit of a Map over a function is that because mapping access can be safely assumed by scripting language bindings to have no side effects, it's semantically easier for scripting language bindings to let the returned value be immediately called, autocompleted, indexed/attribute-read, etc.
    private val backingMap = hashMapOf<K, V>()
    override fun get(key: K): V? {
        val result: V?
        if (key !in backingMap) {
            result = func(key)
            backingMap[key] = result
        } else {
            result = backingMap[key]
        }
        return result
    }
    override val entries get() = if (exposeState) backingMap.entries else noStateError()
    override val keys get() = if (exposeState) backingMap.keys else noStateError()
    override val values get() = if (exposeState) backingMap.values else noStateError()
    override val size get() = if (exposeState) backingMap.size else noStateError()
    override fun containsKey(key: K) = if (exposeState) backingMap.containsKey(key) else noStateError()
    override fun containsValue(value: V) = if (exposeState) backingMap.containsValue(value) else noStateError()
    override fun isEmpty() = if (exposeState) backingMap.isEmpty() else noStateError()
}

class FakeMap<K, V>(private val getter: (K) -> V): StatelessMap<K, V>(), InvokableMap<K, V> {
    override fun get(key: K) = getter(key)
}
