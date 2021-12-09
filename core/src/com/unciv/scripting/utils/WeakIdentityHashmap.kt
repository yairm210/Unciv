package com.unciv.scripting.utils

import java.lang.ref.WeakReference


// TODO: These will need tests.


// WeakReference with equality comparison by referential equality between its and other WeakIdentityMapKey()s' referents.

// Has value equality with itself.
// Has value equality with any other WeakIdentityMapKey()s that points to the same living referent.
// If referent has been garbage collected, points to null, and does not have value equality to any other WeakIdentityMapKey()s.
// Does not have value equality with anything else.

// Should have valid hashCode behaviour given this.

//// Has value equality with its own keyPlaceholder property.
class WeakIdentityMapKey<T>(referent: T): WeakReference<T>(referent) {
    // Two states:
    //  1. Behaviour with living referent, such as when added to Map or used for containment check or index access. Should equal any other WeakIdentityMapKey with the same referent.
    //  3. Behaviour with with dead referent, such as when removed from map. Referent has become null, so can't use that. Should equal itself to still be removable, and should not equal anything else.
    //
//    // Class used to remove a WeakIdentityMapKey() from a MutableMap() when its referent .
//    // Has value equality to the supplied WeakIdentityReference<*>.
//    // Has value equality to itself.
//    // Does not have value equality to anything else.
//
//    // Has valid hashCode behaviour given this.
//    class BrokenWeakIdentityReference(val weakIdentityReference: WeakIdentityReference<*>) {
//        override fun hashCode() = weakIdentityReference.hashCode()
//        // Fulfills reflexive, symmetric, consistent, and null inequality, contracts.
//        // Breaks transitivity contract, as
//        override fun equals(other: Any?): Boolean {
//            return this === other || (other is WeakIdentityReference<*> && weakIdentityReference === other)
//        }
//    }
//    val keyPlaceholder = BrokenWeakIdentityReference(this)
    val hashCode = System.identityHashCode(referent) // Keep hash immutable, and keep this in the same Map bucket.
    override fun hashCode() = hashCode
    override fun equals(other: Any?): Boolean {
        // Fulfills reflexive, symmetric, consistent, null inequality, and transitivity contracts.
        return if (other === this) {
                true // Makes sure removal can always be done by itself.
            } else if (other is WeakIdentityMapKey<*>) {
                val resolved = get()
                if (resolved == null)
                    false
                else
                    resolved === other.get() // Allows containment to be checked and access to be done by new WeakIdentityMapKey()s with the same referent.
            } else {
                false
            }
            // Originally, I wanted to have it equal the referent. But there's no way to make that symmetric/commutative, is there?
    //// And it would make BrokenWeakIdentityReference.equals() less transitive.
////            is BrokenWeakIdentityReference -> other.equals(this) // Allows broken references to be removed as keys— Or actually, given the reflexive equality contract, why don't I just use itself?
        //return get() === if (other is WeakIdentityReference<*>) other.get() else other
    }
}

// Map-like class that uses special WeakReferences as keys.

// For now, clean() must be called manually.
class WeakIdentityMap<K, V>(): MutableMap<K, V> {
    private val backingMap = mutableMapOf<WeakIdentityMapKey<K>, V>()
    override val entries get() = throw NotImplementedError() // backingMap.entries // TODO: Make IdentityWeakReference transparent.
    override val keys get() = throw NotImplementedError() //backingMap.keys.map { it.get() }
    override val size get() = backingMap.size
    override val values get() = backingMap.values // Does exposing state make any sense?
    override fun clear() = backingMap.clear()
    override fun containsKey(key: K) = backingMap.containsKey(WeakIdentityMapKey(key))
    override fun containsValue(value: V) = backingMap.containsValue(value)
    override fun get(key: K) = backingMap.get(WeakIdentityMapKey(key))
    override fun isEmpty() = backingMap.isEmpty()
    override fun put(key: K, value: V) = backingMap.put(WeakIdentityMapKey(key), value)
    override fun putAll(from: Map<out K, V>) = backingMap.putAll(from.entries.associate { WeakIdentityMapKey(it.key) to it.value })
    override fun remove(key: K) = backingMap.remove(WeakIdentityMapKey(key))
    // Free up all invalid
    fun clean() {
        val badkeys = mutableListOf<WeakIdentityMapKey<K>>()
        for (k in backingMap.keys) {
            val referent = k.get()
            if (referent == null)
                badkeys.add(k)
        }
        for (k in badkeys) {
            backingMap.remove(k)
        }
    }
    override fun toString() = "{${backingMap.entries.joinToString(", ") { "${it.key.get()}=${it.value}" }}}"
}

fun <K, V> weakIdentityMapOf(vararg pairs: Pair<K, V>): WeakIdentityMap<K, V> {
    val map = WeakIdentityMap<K, V>()
    map.putAll(pairs)
    return map
}

//mapOf(mutableListOf<Any>(1,2) to 5)[mutableListOf<Any>(1,2)]
//mapOf(WeakReference(mutableListOf<Any>(1,2)) to 5)[WeakReference(mutableListOf<Any>(1,2))]
//var l=mutableListOf<Any>(1,2); mapOf(WeakReference(l) to 5)[WeakReference(l)]

//var a=mutableListOf(1,2); var b=mutableListOf(1); var m=mutableMapOf(a to 1, b to 2)
//m[listOf(1,2)]
//m[listOf(1)]
//b.add(2)

//var a=mutableListOf(1,2); var b=mutableListOf(1); var m=weakIdentityMapOf(a to 1, b to 2)
//m[a] // 1
//m[b] // 2
//m[listOf(1,2)] // null
//m[listOf(1)] // null
//m[a.toList()] // null
//m[b.toList()] // null
//b.add(2)
//m[a] // 1
//m[b] // 2
//m.remove(a)
//m[a] // null
//m[b] // 2
//m.remove(b)
//m[a] // null
//m[b] // null

//object o {var a=mutableListOf(1,2); var b=mutableListOf(1); var c=mutableListOf(1,2)}; var m=weakIdentityMapOf(o.a to 1, o.b to 2, o.c to 3)
//listOf(m[o.a], m[o.b], m[o.c]) // 1,2,3
//var c = WeakIdentityMapKey(o.c)
//o.c = mutableListOf()
//for (i in 1..2000) {(1..i).map{it to object{var x = i; val y=it}}.associate{it}} // GC cannot be forced on JVM, apparently, but this should be fairly strong hint.
//val x = WeakReference(object{val x=5}); while (x.get() != null){System.gc()} // Stronger hint yet.
////GC will never run no matter what in Kotlin REPL, seemingly.
//println(c.get()) // null
//println(m)
//println(m.size) // 3
//m.clean()
//println(m)
//println(m.size) // 2
