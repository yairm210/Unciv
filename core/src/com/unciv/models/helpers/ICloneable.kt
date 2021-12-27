package com.unciv.models.helpers

/**
 * Interface for Unciv data models that implement some sort of a deep copy or "clone" functionality.
 *
 * @param C This type itself.
 */
interface ICloneable<C: ICloneable<C>> {
    /** @return A recursive copy of this instance. */
    fun clone(): C
}

/** @return A new List from calling [ICloneable.clone] on the elements of this one. */
fun <C: ICloneable<C>> List<C>.copy() = this.map { it.clone() }
// Saves a loop when returned list implementation doesn't matter.
// "`.copy`" instead of "`.clone`" because extension functions can't seem to shadow Object.clone(). Also sets apart from ICloneable.

/** @return A new ArrayList MutableList from calling [ICloneable.clone] on the elements of this one. */
fun <C: ICloneable<C>> MutableList<C>.copy() = ArrayList((this as List<C>).copy())
// Receiver type as generic as possible, and return type as specific as possible, to cover the most use cases.

/** @return A new Map from reusing the keys and calling [ICloneable.clone] on the values of this one. */
fun <K, V: ICloneable<V>> Map<K, V>.copy() = this.entries.associate { (key, value) -> key to value.clone() }

/** @return A new HashMap from reusing the keys and calling [ICloneable.clone] on the values of this one. */
fun <K, V: ICloneable<V>> HashMap<K, V>.copy() = HashMap((this as Map<K, V>).copy())
// HashMap receiver, instead of MutableMap receiver, because ordering behaviour is different.
