package com.unciv.utils

import com.badlogic.gdx.utils.Array
import yairm210.purity.annotations.Pure
import kotlin.random.Random

/** Get one random element of a given List.
 *
 * The probability for each element is proportional to the value of its corresponding element in the [weights] List.
 */
fun <T> List<T>.randomWeighted(weights: List<Float>, random: Random = Random): T {
    if (this.isEmpty()) throw NoSuchElementException("Empty list.")
    if (this.size != weights.size) throw UnsupportedOperationException("Weights size does not match this list size.")

    val totalWeight = weights.sum()
    val randDouble = random.nextDouble()
    var sum = 0f

    for (i in weights.indices) {
        sum += weights[i] / totalWeight
        if (randDouble <= sum)
            return this[i]
    }
    return this.last()
}

/** Get one random element of a given List.
 *
 * The probability for each element is proportional to the result of [getWeight] (evaluated only once).
 */
fun <T> List<T>.randomWeighted(random: Random = Random, getWeight: (T) -> Float): T =
    randomWeighted(map(getWeight), random)

/** Gets a clone of an [ArrayList] with an additional item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous arrayList can keep using it because it hasn't changed
 */
fun <T> ArrayList<T>.withItem(item: T): ArrayList<T> {
    val newArrayList = ArrayList(this)
    newArrayList.add(item)
    return newArrayList
}

/** Gets a clone of a [HashSet] with an additional item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous hashSet can keep using it because it hasn't changed
 */
fun <T> HashSet<T>.withItem(item: T): HashSet<T> {
    val newHashSet = HashSet(this)
    newHashSet.add(item)
    return newHashSet
}

/** Gets a clone of an [ArrayList] without a given item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous arrayList can keep using it because it hasn't changed
 */
fun <T> ArrayList<T>.withoutItem(item: T): ArrayList<T> {
    val newArrayList = ArrayList(this)
    newArrayList.remove(item)
    return newArrayList
}

/** Gets a clone of a [HashSet] without a given item
 *
 * Solves concurrent modification problems - everyone who had a reference to the previous hashSet can keep using it because it hasn't changed
 */
fun <T> HashSet<T>.withoutItem(item: T): HashSet<T> {
    val newHashSet = HashSet(this)
    newHashSet.remove(item)
    return newHashSet
}

fun <T> Iterable<T>.toGdxArray(): Array<T> {
    val arr = if (this is Collection) Array<T>(size) else Array<T>()
    for (it in this) arr.add(it)
    return arr
}
fun <T> Sequence<T>.toGdxArray(): Array<T> {
    val arr = Array<T>()
    for (it in this) arr.add(it)
    return arr
}

/** [yield][SequenceScope.yield]s [element] if it's not null */
suspend fun <T> SequenceScope<T>.yieldIfNotNull(element: T?) {
    if (element != null) yield(element)
}
/** [yield][SequenceScope.yield]s all elements of [elements] if it's not null */
@Pure
suspend fun <T> SequenceScope<T>.yieldAllNotNull(elements: Iterable<T>?) {
    if (elements != null) yieldAll(elements)
}

/**
 *  Simplifies adding to a map of sets where the map entry where the new element belongs is not
 *  guaranteed to be already present in the map (sparse map).
 *
 *  @param key The key identifying the Set to add [element] to
 *  @param element The new element to be added to the Set for [key]
 *  @return `false` if the element was already present, `true` if it was new (same as `Set.add()`)
 */
fun <KT, ET> HashMap<KT, HashSet<ET>>.addToMapOfSets(key: KT, element: ET) =
    getOrPut(key) { hashSetOf() }.add(element)

/** Simplifies testing whether in a sparse map of sets the [element] exists for [key]. */
fun <KT, ET> HashMap<KT, HashSet<ET>>.contains(key: KT, element: ET) =
    get(key)?.contains(element) == true


/** This is for arraylists that replace hashmaps - they contain nulls where we don't know the answer yet */
fun <T> ArrayList<T?>.getOrPut(index: Int, getValue: () -> T): T {
    val currentValue = getOrNull(index)
    if (currentValue != null) return currentValue

    val value = getValue()

    // grow the arraylist if required - if not, these are no-ops
    ensureCapacity(index + 1) // So we don't need to copy the array multiple times if adding a lot
    while (size <= index) add(null) // Fill with nulls until we reach the index

    this[index] = value // Now we can safely set the value
    return value
}
