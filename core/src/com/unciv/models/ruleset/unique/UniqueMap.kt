package com.unciv.models.ruleset.unique

import yairm210.purity.annotations.Readonly
import java.util.*

open class UniqueMap() {
    @PublishedApi internal val tagUniqueMap = HashMap<String, ArrayList<Unique>>()

    // *shares* the list of uniques with the other map, to save on memory and allocations
    // This is a memory/speed tradeoff, since there are *600 unique types*,
    // 750 including deprecated, and EnumMap creates a N-sized array where N is the number of objects in the enum
    @PublishedApi internal val typedUniqueMap = EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java)

    constructor(uniques: Sequence<Unique>) : this() {
        addUniques(uniques.asIterable())
    }

    /** Adds one [unique] unless it has a ConditionalTimedUnique conditional */
    open fun addUnique(unique: Unique) {
        val existingArrayList = tagUniqueMap[unique.placeholderText]
        if (existingArrayList != null) existingArrayList.add(unique)
        else tagUniqueMap[unique.placeholderText] = arrayListOf(unique)
        
        if (unique.type == null) return
        if (typedUniqueMap[unique.type] != null) return
        typedUniqueMap[unique.type] = tagUniqueMap[unique.placeholderText]
    }

    /** Calls [addUnique] on each item from [uniques] */
    fun addUniques(uniques: Iterable<Unique>) {
        for (unique in uniques) addUnique(unique)
    }

    fun removeUnique(unique: Unique) {
        val existingArrayList = tagUniqueMap[unique.placeholderText]
        existingArrayList?.remove(unique)
    }
    
    fun clear() {
        tagUniqueMap.clear()
        typedUniqueMap.clear()
    }
    
    @Readonly
    fun isEmpty(): Boolean = tagUniqueMap.isEmpty()
    
    @Readonly
    inline fun hasUnique(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) =
        firstUniqueOrNull(uniqueType) { it.conditionalsApply(state) && !it.isTimedTriggerable } != null

    @Readonly
    inline fun hasUnique(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        firstTagUniqueOrNull(uniqueTag) { it.conditionalsApply(state) && !it.isTimedTriggerable } != null

    @Readonly
    fun hasTagUnique(tagUnique: String) =
        tagUniqueMap.containsKey(tagUnique)

    // 160ms vs 1000-1250ms/30s
    @Readonly
    /** forEachUnique is faster, for cases that require high perf */
    fun getUniques(uniqueType: UniqueType) = typedUniqueMap[uniqueType]
        ?.asSequence()
        ?: emptySequence()

    @Readonly
    inline fun forEachUnique(uniqueType: UniqueType, crossinline op: (Unique)->Unit)
        = firstUniqueOrNull(uniqueType) { op(it); false }

    @Readonly
    inline fun firstUniqueOrNull(uniqueType: UniqueType, predicate: (Unique)->Boolean): Unique? {
        val uniques = typedUniqueMap[uniqueType] ?: return null
        for (i in 0..< uniques.size)
            if (predicate(uniques[i])) return uniques[i]
        return null
    }

    @Readonly
    /** forEachTagUnique is faster, for cases that require high perf */
    fun getTagUniques(uniqueTag: String) = tagUniqueMap[uniqueTag]
        ?.asSequence()
        ?: emptySequence()

    @Readonly
    inline fun forEachTagUnique(uniqueTag: String, crossinline op: (Unique)->Unit)
        = firstTagUniqueOrNull(uniqueTag) { op(it); false }

    @Readonly
    inline fun firstTagUniqueOrNull(uniqueTag: String, predicate: (Unique)->Boolean): Unique? {
        val uniques = tagUniqueMap[uniqueTag] ?: return null
        for (i in 0..< uniques.size)
            if (predicate(uniques[i])) return uniques[i]
        return null
    }

    @Readonly
    /** forEachMatchingUnique faster, for cases that require high perf */ 
    fun getMatchingUniques(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) = 
        getUniques(uniqueType)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }

    /** @return A freshly allocated [List] snapshot of the uniques matching [uniqueType], for cases that need to mutate
     *  this map (or otherwise cannot use a live view) while iterating the result. */
    @Readonly
    fun getMatchingUniquesSnapshot(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState): List<Unique> {
        val list = ArrayList<Unique>()
        forEachMatchingUnique(uniqueType, state) { list.add(it) }
        return list
    }

    @Readonly
    inline fun forEachMatchingUnique(uniqueType: UniqueType, gameContext: GameContext, crossinline op: (Unique)->Unit)
        = forEachMatchingUnique(uniqueType, gameContext, NO_UNIQUE_FILTER, op)
    @Readonly
    inline fun forEachMatchingUnique(uniqueType: UniqueType, gameContext: GameContext, crossinline filter:(Unique)->Boolean, crossinline op: (Unique)->Unit)
        = firstMatchingUniqueOrNull(uniqueType, gameContext, filter) { op(it); false }
    

    @Readonly
    inline fun firstMatchingUniqueOrNull(uniqueType: UniqueType, gameContext: GameContext, crossinline predicate: (Unique)->Boolean): Unique?
        = firstMatchingUniqueOrNull(uniqueType, gameContext, NO_UNIQUE_FILTER, predicate)
    @Readonly
    inline fun firstMatchingUniqueOrNull(uniqueType: UniqueType, gameContext: GameContext, crossinline filter:(Unique)->Boolean, crossinline predicate: (Unique)->Boolean): Unique? {
        val list = typedUniqueMap[uniqueType] ?: return null
        return firstMatchingUniqueOrNull(list, gameContext, filter, predicate)
    }

    @Readonly
    /** forEachMatchingTagUnique faster, for cases that require high perf */
    fun getMatchingTagUniques(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        getTagUniques(uniqueTag)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }

    /** @return A freshly allocated [List] snapshot of the uniques matching [uniqueTag], for cases that need to mutate
     *  this map (or otherwise cannot use a live view) while iterating the result. */
    @Readonly
    fun getMatchingTagUniquesSnapshot(uniqueTag: String, state: GameContext = GameContext.EmptyState): List<Unique> {
        val list = ArrayList<Unique>()
        forEachMatchingTagUnique(uniqueTag, state, NO_UNIQUE_FILTER) { list.add(it) }
        return list
    }

    @Readonly
    inline fun forEachMatchingTagUnique(uniqueTag: String, gameContext: GameContext, crossinline filter:(Unique)->Boolean, crossinline op: (Unique)->Unit)
        = firstMatchingTagUniqueOrNull(uniqueTag, gameContext, filter) { op(it); false }

    @Readonly
    inline fun firstMatchingTagUniqueOrNull(uniqueTag: String, gameContext: GameContext, crossinline filter:(Unique)->Boolean, crossinline predicate: (Unique)->Boolean): Unique? {
        val list = tagUniqueMap[uniqueTag] ?: return null
        return firstMatchingUniqueOrNull(list, gameContext, filter, predicate)
    }

    @Readonly
    inline fun forEachMatchingUnique(list: List<Unique>, gameContext: GameContext, crossinline filter:(Unique)->Boolean, crossinline op: (Unique)->Unit) {
        firstMatchingUniqueOrNull(list, gameContext, filter) { op(it); false }
    }

    @Readonly
    inline fun firstMatchingUniqueOrNull(list: List<Unique>, gameContext: GameContext, filter:(Unique)->Boolean, crossinline predicate: (Unique)->Boolean): Unique? {
        for (i in 0..<list.size) {
            val unique = list[i]
            if (unique.isTimedTriggerable) continue
            if (!filter(unique)) continue
            if (!unique.conditionalsApply(gameContext)) continue
            // This seems like it shouldn't be needed, but actually is since it loops over *active* Uniques,
            // so we need to call the predicate for each multiplied unique, not each base unique.
            val result = unique.firstMultipliedOrNull(gameContext, predicate)
            if (result != null) return result        }
        return null
    }
    
    @Readonly
    inline fun hasMatchingUnique(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) =
        firstUniqueOrNull(uniqueType) { it.conditionalsApply(state) } != null

    @Readonly
    inline fun hasMatchingTagUnique(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        firstTagUniqueOrNull(uniqueTag) { it.conditionalsApply(state) } != null

    @Readonly
    fun getAllUniques() = tagUniqueMap.values.asSequence().flatten()
    
    @Readonly
    // UniqueMap lacks a way to iterate over all Uniques without allocations, so this is not *dramatically* faster than getLocalTriggeredUniques
    inline fun forEachUnique(crossinline op: (Unique)->Unit) = getAllUniques().forEach(op)
    @Readonly
    inline fun forEachUnique(filter: (Unique)->Boolean, crossinline op: (Unique)->Unit) {
        firstUniqueOrNull(filter) { op(it); false }
    }

    @Readonly
    // UniqueMap lacks a way to iterate over all Uniques without allocations, so this is not *dramatically* faster than getAllUniques
    fun firstUniqueOrNull(predicate: (Unique)->Boolean): Unique? = getAllUniques().firstOrNull(predicate)
    @Readonly
    inline fun firstUniqueOrNull(filter: (Unique)->Boolean, crossinline predicate: (Unique)->Boolean): Unique? {
        for (unique in getAllUniques())
            if (filter(unique) && predicate(unique)) return unique
        return null
    }

    @Readonly
    fun getTriggeredUniques(trigger: UniqueType, gameContext: GameContext,
                            triggerFilter: (Unique) -> Boolean = { true }): Sequence<Unique> {
        return typedUniqueMap.values.asSequence().flatten().filter { unique ->
            unique.getModifiers(trigger).any(triggerFilter) && unique.conditionalsApply(gameContext)
        }.flatMap { it.getMultiplied(gameContext) }
    }

    @Readonly
    inline fun forEachTriggeredUnique(trigger: UniqueType, gameContext: GameContext,
                               crossinline triggerFilter: (Unique) -> Boolean = { true }, crossinline op: (Unique)->Unit) {
        firstTriggeredUniqueOrNull(trigger, gameContext, triggerFilter) { op(it); false }
    }

    @Readonly
    inline fun firstTriggeredUniqueOrNull(trigger: UniqueType, gameContext: GameContext,
                                    triggerFilter: (Unique) -> Boolean = { true }, crossinline predicate: (Unique)->Boolean): Unique? {
        for (unique in typedUniqueMap.values.asSequence().flatten()) {
            if (!unique.getModifiers(trigger).any(triggerFilter) || !unique.conditionalsApply(gameContext))
                continue
            // This seems like it shouldn't be needed, but actually is since it loops over *active* Uniques,
            // so we need to call the predicate for each multiplied unique, not each base unique.
            val result = unique.firstMultipliedOrNull(gameContext, predicate)
            if (result != null) return result
        }
        return null
    }
    
    companion object{
        val EMPTY = UniqueMap()
        val NO_UNIQUE_FILTER = { _: Unique -> true }
    }
}
