package com.unciv.utils

import com.unciv.utils.ImmutableIntIntArrayMap.Companion.ReusableEntry
import yairm210.purity.annotations.InternalState
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
import java.util.BitSet
import java.util.Objects
import java.util.stream.IntStream
import kotlin.let

/*
 * Immutable Pseudo-Map of Int to Int.
 * 
 * Real Maps are generic, forcing boxing.  This has the same methods as a Map, except:
 * - Generic methods are not generic, bypassing boxing.
 * - It implements Collection<ReusableEntry> (including #contains and #containsAll)
 * - "entries" returns a Sequence<ReusableEntry>, since there's no easy way to implement that "Set" without boxing.
 * - iterator() returns a Iterator<ReusableEntry>, since Iterator requires boxing.
 * - #map, #mapKeys, #mapValues, #maxBy, #maxof, #maxOfOrNull, #minBy, #minOf, #minOfOrNull,
 *   all have #xGeneric overloads that allow generic transforms.
 * - #maxBy, #maxof, #minBy, #minOf, #minOfOrNull,
 *   all have #xGeneric overloads that allow generic transforms.
 * 
 */
@Suppress("unused", "NOTHING_TO_INLINE")
@InternalState
open class ImmutableIntIntArrayMap
protected constructor(
    private val map: LongArray,
    override val size: Int)
    : Collection<ReusableEntry> {
    // Any methods that "find" an element cache the index of that element.
    // This allows subsequent calls to that same element to be immediate, bypassing the lookup.
    // This is ALWAYS a valid index (unless size==0)
    private var lastEntryIndex = 0

    @Pure inline fun validateNotEmpty() { if (size == 0) throw NoSuchElementException("Map is empty") }
    @Pure inline fun validateIndex(index: Int): Int = if (index !in 0..<size) throw IndexOutOfBoundsException("Index $index is out of bounds for map of size $size") else index

    // Retreive Entries by index
    @Pure fun atIndexUnchecked(index: Int) = Entry(map[index])
    @Readonly inline fun atIndex(index: Int): Entry {
        cacheIndex(validateIndex(index))
        return atIndexUnchecked(index)
    }
    @Suppress("purity")
    @Readonly fun cacheIndex(index: Int) { lastEntryIndex = validateIndex(index) }
    @Readonly fun getLastEntry() = Entry(map[lastEntryIndex])

    // Find indecies by key or by predicate
    @Suppress("purity")
    @Readonly fun indexOfKey(key: Int): Int {
        var low = -1
        var high = size
        // both high and low are EXCLUSIVE bounds, so that lastEntryIndex is always a valid index
        while (true) {
            val midVal = Entry(map[lastEntryIndex]).key
            when {
                midVal < key -> low = lastEntryIndex
                midVal > key -> high = lastEntryIndex
                else -> return lastEntryIndex // key found
            }
            if (low + 1 == high) return -1
            lastEntryIndex = (high - low) / 2 + low
        }
    }
    @Readonly fun indexOrThrow(key: Int): Int {
        val index = indexOfKey(key)
        if (index < 0) throw NoSuchElementException("Key $key not found in map")
        return index
    }
    @Readonly inline fun indexOfFirst(action: (Entry,Int)->Boolean): Int {
        for (i in 0 until size) {
            if (action(atIndexUnchecked(i), i))
                return i
        }
        return -1
    }
    @Readonly inline fun indexOfFirstOrThrow(action: (Entry,Int)->Boolean): Int {
        val index = indexOfFirst(action)
        if (index < 0) throw NoSuchElementException("Matching Entry not found in map")
        return index
    }

    // Map methods
    val entries: Sequence<ReusableEntry> = sequence {
        val entry = ReusableEntry(0, 0)
        for (i in 0 until size) {
            entry.copyFrom(atIndexUnchecked(i))
            yield(entry)
        }
    }
    val keys: IntStream = IntStream.range(0, size).map { atIndexUnchecked(it).key }
    val values: IntStream = IntStream.range(0, size).map { atIndexUnchecked(it).value }
    @Readonly inline fun all(predicate: (Entry,Int)->Boolean) = indexOfFirst(predicate) < 0
    @Readonly inline fun any(predicate: (Entry,Int)->Boolean) = indexOfFirst(predicate) >= 0
    @Readonly fun asIterable() = this
    @Readonly fun asSequence() = entries
    @Readonly fun contains(key: Int) = containsKey(key)
    override fun contains(element: ReusableEntry): Boolean {
        val index = indexOfKey(element.key)
        if (index > 0) {
            require(element.value == atIndexUnchecked(index).value) {
                "Map contains key ${element.key} but with different value ${atIndexUnchecked(index).value} (expected ${element.value})"
            }
            return true
        }
        return false
    }
    override fun containsAll(elements: Collection<ReusableEntry>): Boolean = elements.all { contains(it) }
    @Readonly fun containsKey(key: Int) = indexOfKey(key) >= 0
    @Readonly fun containsValue(value: Int) = indexOfFirst { e,_ -> e.value == value } >= 0
    @Readonly fun count() = size
    @Readonly inline fun count(predicate: (Entry,Int)->Boolean): Int {
        var count = 0
        forEach { e, i -> if (predicate(e,i)) count++ }
        return count
    }
    @Readonly inline fun filter(predicate: (Entry,Int)->Boolean): ImmutableIntIntArrayMap  {
        val filtered = BitSet(size)
        forEach { e, i -> if (predicate(e, i)) filtered.set(i) }
        val copyCount = filtered.cardinality()
        @LocalState
        val builder = Builder(copyCount)
        filtered.forEachSetBit { builder.plus(atIndexUnchecked(it)) }
        return builder.build()
    }
    @Readonly inline fun filterKeys(predicate: (Int)->Boolean): ImmutableIntIntArrayMap = filter { e, _ -> predicate(e.key) }
    @Readonly inline fun filterNot(predicate: (Entry,Int)->Boolean) = filter { e,i -> !predicate(e, i) }
    @Readonly inline fun filterValues(predicate:(Int)->Boolean) = filter { e, _ -> predicate(e.value) }
    @Readonly inline fun first(predicate: (Entry,Int)->Boolean): Entry = atIndexUnchecked(indexOfFirstOrThrow(predicate))
    @Readonly inline fun <T> firstNotNullOf(transform: (Entry,Int)->T?): T =
        firstNotNullOfOrNull(transform) ?: throw NoSuchElementException("No entry returned a non-null value")
    @Readonly inline fun <T> firstNotNullOfOrNull(transform: (Entry,Int)->T?): T? {
        for (i in 0 until size)
            transform(atIndexUnchecked(i), i)?.let { cacheIndex(i); return it }
        return null
    }
    @Readonly inline fun <T> flatMap(transform: (Entry,Int)->Iterable<T>): List<T> {
        val list = ArrayList<T>()
        forEach { e, i -> list.addAll(transform(e, i)) }
        return list
    }
    @Readonly inline fun forEach(action: (Entry,Int)->Unit) {
        for (i in 0 until size) {
            action(atIndexUnchecked(i), i)
        }
    }
    @Readonly operator fun get(key: Int) = atIndexUnchecked(indexOrThrow(key)).value
    @Readonly fun getOrDefault(key: Int, default: Int): Int {
        val index = indexOfKey(key)
        return if (index >= 0) atIndexUnchecked(index).value else default
    }
    @Readonly inline fun getOrElse(key: Int, default: ()->Int): Int {
        val index = indexOfKey(key)
        return if (index >= 0) atIndexUnchecked(index).value else default()
    }
    @Readonly fun getValue(key: Int) = get(key)
    @Readonly override fun isEmpty() = size == 0
    @Readonly fun isNotEmpty() = size > 0
    @Readonly override fun iterator() = ImmutableSmallFlatIntIntMapIterator(this)
    @Readonly inline fun <T> mapGeneric(transform: (Entry,Int)->T): List<T> {
        val list = ArrayList<T>(size)
        forEach { e, i -> transform(e, i)?.let { list.plus(this) }}
        return list
    }
    @Readonly inline fun map(transform: (Entry,Int)->Long): LongArray {
        @LocalState
        val array = LongArray(size)
        forEach { e, i -> array[i] = transform(e, i) }
        return array
    }
    @Readonly inline fun map(transform: (Entry,Int)->Int): IntArray {
        @LocalState
        val array = IntArray(size)
        forEach { e, i -> array[i] = transform(e, i) }
        return array
    }
    @Readonly inline fun mapEntries(transform: (Entry,Int)->Entry): ImmutableIntIntArrayMap  {
        @LocalState
        val builder = Builder(size)
        forEach { e,i -> builder.plus(transform(e, i)) }
        return builder.build()
    }
    @Readonly inline fun <T> mapKeysGeneric(transform: (Entry,Int)->T): Map<T, Int> {
        @LocalState
        val r = HashMap<T, Int>(size + size/4)
        forEach { e, i -> transform(e, i)?.let { r[it] = e.value } }
        return r
    }
    @Readonly inline fun mapKeysInt(transform: (Entry, Int)->Int): ImmutableIntIntArrayMap
        = mapEntries { e, i -> Entry(transform(e, i), e.value) }
    @Readonly inline fun <T> mapNotNull(transform: (Entry,Int)->T?): List<T> {
        @LocalState
        val r = ArrayList<T>(size)
        forEach { e, i -> transform(e, i)?.apply { r.add(this) } }
        return r
    }
    @Readonly inline fun <T> mapValuesGeneric(transform: (Entry,Int)->T): Map<Int, T> {
        @LocalState
        val r = HashMap<Int, T>(size + size/4)
        forEach { e, i -> transform(e, i)?.let { r[e.key] = it } }
        return r
    }
    @Readonly inline fun mapValuesInt(transform: (Entry, Int)->Int): ImmutableIntIntArrayMap
        = mapEntries { e, i -> Entry(e.key, transform(e, i)) }
    @Readonly inline fun <T: Comparable<T>> maxByGeneric(noinline selector: (Entry, Int)->T): Entry {
        maxOfGeneric(selector)
        return getLastEntry()
    }
    @Readonly inline fun maxBy(noinline selector: (Entry,Int)->Long): Entry {
        maxOfLong(selector)
        return getLastEntry()
    }
    @Readonly inline fun maxByDouble(noinline selector: (Entry, Int)->Double): Entry {
        maxOfDouble(selector)
        return getLastEntry()
    }
    @Readonly inline fun <T: Comparable<T>> maxByOrNull(noinline selector: (Entry, Int)->T): Entry?
        = if (size == 0) null else maxByGeneric(selector)
    @Readonly inline fun <T: Comparable<T>> maxOfGeneric(noinline selector: (Entry, Int)->T): T
        = maxOfWith(Comparator.naturalOrder(), selector)
    @Readonly inline fun maxOfLong(noinline selector: (Entry, Int)->Long): Long = minOfLong { e, i -> -selector(e,i) }
    @Readonly inline fun maxOfDouble(noinline selector: (Entry, Int)->Double): Double
        = minOfDouble { e, i -> -selector(e, i) }

    @Readonly inline fun <T: Comparable<T>> maxOfOrNull(noinline selector: (Entry, Int)->T): T?
        = if (size == 0) null else maxOfGeneric(selector)
    @Readonly inline fun <T> maxOfWith(comparator: Comparator<T>, noinline selector: (Entry, Int)->T): T
        = minOfWith({ e1, e2 -> comparator.compare(e2, e1) }, selector)
    @Readonly inline fun <T> maxOfWithOrNull(comparator: Comparator<T>, noinline selector: (Entry, Int)->T): T?
        = if (size == 0) null else maxOfWith(comparator, selector)
    @Readonly inline fun maxWith(noinline comparator: (Entry, Entry)->Int): Entry = minWith { e1, e2 -> comparator(e2, e1) }
    @Readonly inline fun maxWithOrNull(noinline comparator: (Entry, Entry)->Int): Entry? = if (size == 0) null else maxWith(comparator)
    @Readonly inline fun <T: Comparable<T>> minByGeneric(noinline selector: (Entry,Int)->T): Entry {
        minOfGeneric(selector)
        return getLastEntry()
    }
    @Readonly inline fun minBy(noinline selector: (Entry,Int)->Long): Entry {
        minOfLong(selector)
        return getLastEntry()
    }
    @Readonly inline fun minByDouble(noinline selector: (Entry, Int)->Double): Entry {
        minOfDouble(selector)
        return getLastEntry()
    }
    @Readonly inline fun <T: Comparable<T>> minByOrNull(noinline selector: (Entry, Int)->T): Entry?
        = if (size == 0) null else minByGeneric(selector)
    @Readonly inline fun <T: Comparable<T>> minOfGeneric(noinline selector: (Entry, Int)->T): T
        = minOfWith(Comparator.naturalOrder(), selector)
    @Readonly fun minOfLong(selector: (Entry,Int)->Long): Long {
        validateNotEmpty()
        var minIndex = 0
        var minValue = selector(atIndexUnchecked(minIndex), 0)
        forEach { e, i ->
            if (i == 0) return@forEach
            val newValue = selector(e, i)
            if (newValue < minValue) {
                minIndex = i
                minValue = newValue
            }
        }
        cacheIndex(minIndex)
        return minValue
    }
    @Readonly fun minOfDouble(selector: (Entry, Int)->Double): Double {
        validateNotEmpty()
        var minIndex = 0
        var minValue = selector(atIndexUnchecked(minIndex), 0)
        forEach { e, i ->
            if (i == 0) return@forEach
            val newValue = selector(e, i)
            if (newValue < minValue) {
                minIndex = i
                minValue = newValue
            }
        }
        cacheIndex(minIndex) // This updates lastEntryIndex to this index
        return minValue
    }
    @Readonly inline fun <T: Comparable<T>> minOfOrNull(noinline selector: (Entry, Int)->T): T?
        = if (size == 0) null else minOfGeneric(selector)
    @Readonly fun <T> minOfWith(comparator: Comparator<T>, selector: (Entry,Int)->T): T {
        validateNotEmpty()
        var minIndex = 0
        var minValue = selector(atIndexUnchecked(minIndex), 0)
        forEach { e, i ->
            if (i == 0) return@forEach
            val newValue = selector(e, i)
            if (comparator.compare(newValue, minValue) < 0) {
                minIndex = i
                minValue = newValue
            }
        }
        cacheIndex(minIndex) // This updates lastEntryIndex to this index
        return minValue
    }
    @Readonly inline fun <T> minOfWithOrNull(comparator: Comparator<T>, noinline selector: (Entry, Int)->T): T?
        = if (size == 0) null else minOfWith(comparator, selector)
    @Readonly fun minWith(comparator: (Entry,Entry)->Int): Entry {
        validateNotEmpty()
        var minIndex = 0
        var minEntry = atIndexUnchecked(minIndex)
        forEach { e, i ->
            if (i == 0) return@forEach
            if (comparator(e, minEntry) < 0) {
                minIndex = i
                minEntry = e
            }
        }
        cacheIndex(minIndex) // set the lastEntryIndex
        return minEntry
    }
    @Readonly inline fun minWithOrNull(noinline comparator: (Entry, Entry)->Int): Entry? = if (size == 0) null else minWith(comparator)
    @Readonly fun none() = size == 0
    @Readonly fun toList(): List<Entry> {
        @LocalState
        val list = mutableListOf<Entry>()
        forEach { e, _ -> list.add(e) }
        return list
    }
    @Readonly fun toMap() = this
    @Readonly fun toSortedMap() = this

    @Readonly override fun toString() = buildString {
        append(this.javaClass.name)
        append("@")
        append(Objects.toIdentityString(this))
        append("{")
        forEach { e,_ -> append(e.key).append("=").append(e.value).append(", ") }
        append("}")
    }
    @Readonly override fun equals(other: Any?): Boolean {
        if (other !is ImmutableIntIntArrayMap) return false
        if (size != other.size) return false
        for (i in 0 until size)
            if (atIndexUnchecked(i) != other.atIndexUnchecked(i)) return false
        return true
    }
    @Readonly override fun hashCode(): Int {
        var hashCode = 0
        forEach { e, _ -> hashCode += e.key shl 16 + e.key shr 16 + e.value }
        return hashCode
    }

    companion object {
        @JvmInline
        value class Entry(val bits: Long) {
            constructor(key: Int, value: Int)
                : this((key.toLong() shl 32) or (value.toLong() and 0xFFFFFFFFL))

            val key get() = (bits shr 32).toInt()
            val value get() = bits.toInt()
        }
        class ReusableEntry(var key: Int, var value: Int) {
            fun copyFrom(entry: Entry) {
                key = entry.key
                value = entry.value
            }
        }

        @InternalState
        class ImmutableSmallFlatIntIntMapIterator(
            private val map: ImmutableIntIntArrayMap
        ) : Iterator<ReusableEntry> {
            private var index = 0
            private val reusableEntry: ReusableEntry = ReusableEntry(0,0)
            override fun hasNext() = index < map.size
            @Suppress("purity")
            override fun next(): ReusableEntry {
                if (index >= map.size) throw NoSuchElementException("No more entries in map")
                reusableEntry.copyFrom(map.atIndexUnchecked(index++))
                return reusableEntry
            }
        }

        abstract class AbstractBuilder<DerivedBuilder, DerivedMap>(initialCapacity: Int = 16) {
            private var entries = LongArray(initialCapacity)
            private var size = 0
            private var sorted = true

            fun reserve(minCapacity: Int): DerivedBuilder {
                if (minCapacity <= entries.size) return self
                val newCapacity =
                    if (minCapacity < 16) 16
                    else if (minCapacity == entries.size + 1) entries.size + entries.size / 2
                    else minCapacity
                entries = entries.copyOf(newCapacity)
                return self
            }
            private fun prepare() {
                if (!sorted) {
                    entries.sort(0, size)
                    for (i in 1 until size) {
                        require(Entry(entries[i-1]).key != Entry(entries[i]).key) {
                            "Duplicate key ${Entry(entries[i]).key} in map builder"
                        }
                    }
                }
            }
            private fun rawAppend(index: Int, entry: Entry) {
                entries[index] = entry.bits
                if (size > 1 && sorted) {
                    if (entry.key > Entry(entries[index - 1]).key) {
                        sorted = false
                    } else require(entry.key != Entry(entries[index - 1]).key) {
                        "Duplicate key ${entry.key} in map builder"
                    }
                }
            }
            protected abstract val self: DerivedBuilder
            protected abstract fun build(map: LongArray, size: Int): DerivedMap

            operator fun plus(newEntry: Entry): DerivedBuilder  = set(newEntry.key, newEntry.value)
            fun put(key: Int, value: Int) = set(key, value)
            operator fun set(key: Int, value: Int): DerivedBuilder {
                this@AbstractBuilder.reserve(size+1)
                rawAppend(size, Entry(key, value))
                size++
                return self
            }
            operator fun plus(newEntries: Array<Entry>): DerivedBuilder {
                if (entries.isEmpty()) return self
                this@AbstractBuilder.reserve(size + newEntries.size)
                newEntries.forEachIndexed {i,e -> rawAppend(size + i, e) }
                size += newEntries.size
                return self
            }
            operator fun plus(newEntries: Collection<Entry>): DerivedBuilder {
                if (entries.isEmpty()) return self
                this@AbstractBuilder.reserve(size + newEntries.size)
                newEntries.forEachIndexed {i,e -> rawAppend(size + i, e) }
                size += newEntries.size
                return self
            }
            operator fun plus(newEntries: Iterable<Entry>): DerivedBuilder {
                newEntries.forEach { plus(it) }
                return self
            }
            operator fun plus(newEntries: Sequence<Entry>): DerivedBuilder {
                newEntries.forEach { plus(it) }
                return self
            }
            fun filter(oldMap: ImmutableIntIntArrayMap, predicate: (Entry,Int)->Boolean): DerivedBuilder {
                val filtered = BitSet(size)
                oldMap.forEach { e, i -> if (predicate(e, i)) filtered.set(i) }
                val copyCount = filtered.cardinality()
                this@AbstractBuilder.reserve(size + copyCount)
                filtered.forEachSetBit {
                    plus(oldMap.atIndexUnchecked(it))
                }
                return self
            }
            fun map(oldMap: ImmutableIntIntArrayMap, transform: (Entry,Int)->Entry): DerivedBuilder {
                this@AbstractBuilder.reserve(size + oldMap.size)
                oldMap.forEach { e, i -> plus(transform(e, i)) }
                return self
            }

            fun build(): DerivedMap {
                prepare()
                return build(entries, size)
            }
        }
        class Builder(capacity: Int = 32)
            : AbstractBuilder<Builder, ImmutableIntIntArrayMap>(capacity) {
            override val self: Builder get() = this
            override fun build(map: LongArray,size: Int): ImmutableIntIntArrayMap
                = ImmutableIntIntArrayMap(map, size)
        }
    }
}
