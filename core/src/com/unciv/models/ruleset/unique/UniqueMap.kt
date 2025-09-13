package com.unciv.models.ruleset.unique

import yairm210.purity.annotations.Readonly
import java.util.*

open class UniqueMap() {
    private val tagUniqueMap = HashMap<String, ArrayList<Unique>>()

    // *shares* the list of uniques with the other map, to save on memory and allocations
    // This is a memory/speed tradeoff, since there are *600 unique types*,
    // 750 including deprecated, and EnumMap creates a N-sized array where N is the number of objects in the enum
    private val typedUniqueMap = EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java)

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
    fun hasUnique(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) =
        getUniques(uniqueType).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    @Readonly
    fun hasUnique(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        getTagUniques(uniqueTag).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    @Readonly
    fun hasTagUnique(tagUnique: String) =
        tagUniqueMap.containsKey(tagUnique)

    // 160ms vs 1000-1250ms/30s
    @Readonly
    fun getUniques(uniqueType: UniqueType) = typedUniqueMap[uniqueType]
        ?.asSequence()
        ?: emptySequence()

    @Readonly
    fun getTagUniques(uniqueTag: String) = tagUniqueMap[uniqueTag]
        ?.asSequence()
        ?: emptySequence()

    @Readonly
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

    @Readonly
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

    @Readonly
    fun hasMatchingUnique(uniqueType: UniqueType, state: GameContext = GameContext.EmptyState) = 
        getUniques(uniqueType).any { it.conditionalsApply(state) }

    @Readonly
    fun hasMatchingTagUnique(uniqueTag: String, state: GameContext = GameContext.EmptyState) =
        getTagUniques(uniqueTag)
            .any { it.conditionalsApply(state) }

    @Readonly
    fun getAllUniques() = tagUniqueMap.values.asSequence().flatten()

    @Readonly
    fun getTriggeredUniques(trigger: UniqueType, gameContext: GameContext,
                            triggerFilter: (Unique) -> Boolean = { true }): Sequence<Unique> {
        return typedUniqueMap.values.asSequence().flatten().filter { unique ->
            unique.getModifiers(trigger).any(triggerFilter) && unique.conditionalsApply(gameContext)
        }.flatMap { it.getMultiplied(gameContext) }
    }
    
    companion object{
        val EMPTY = UniqueMap()
    }
}
