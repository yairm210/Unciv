package com.unciv.models.ruleset.unique

import org.jetbrains.annotations.Contract
import java.util.*

open class UniqueMap() {
    private val innerUniqueMap = HashMap<String, ArrayList<Unique>>()

    // *shares* the list of uniques with the other map, to save on memory and allocations
    // This is a memory/speed tradeoff, since there are *600 unique types*,
    // 750 including deprecated, and EnumMap creates a N-sized array where N is the number of objects in the enum
    private val typedUniqueMap = EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java)

    constructor(uniques: Sequence<Unique>) : this() {
        addUniques(uniques.asIterable())
    }

    /** Adds one [unique] unless it has a ConditionalTimedUnique conditional */
    open fun addUnique(unique: Unique) {
        val existingArrayList = innerUniqueMap[unique.placeholderText]
        if (existingArrayList != null) existingArrayList.add(unique)
        else innerUniqueMap[unique.placeholderText] = arrayListOf(unique)
        
        if (unique.type == null) return
        if (typedUniqueMap[unique.type] != null) return
        typedUniqueMap[unique.type] = innerUniqueMap[unique.placeholderText]
    }

    /** Calls [addUnique] on each item from [uniques] */
    fun addUniques(uniques: Iterable<Unique>) {
        for (unique in uniques) addUnique(unique)
    }

    fun removeUnique(unique: Unique) {
        val existingArrayList = innerUniqueMap[unique.placeholderText]
        existingArrayList?.remove(unique)
    }
    
    fun clear() {
        innerUniqueMap.clear()
        typedUniqueMap.clear()
    }
    
    @Contract("readonly")
    fun isEmpty(): Boolean = innerUniqueMap.isEmpty()
    
    @Contract("readonly")
    fun hasUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueType).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    @Contract("readonly")
    fun hasUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    @Contract("readonly")
    fun hasTagUnique(tagUnique: String) =
        innerUniqueMap.containsKey(tagUnique)

    // 160ms vs 1000-1250ms/30s
    @Contract("readonly")
    fun getUniques(uniqueType: UniqueType) = typedUniqueMap[uniqueType]
        ?.asSequence()
        ?: emptySequence()

    @Contract("readonly")
    fun getUniques(uniqueTag: String) = innerUniqueMap[uniqueTag]
        ?.asSequence()
        ?: emptySequence()

    @Contract("readonly")
    fun getMatchingUniques(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) = 
        getUniques(uniqueType)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }

    @Contract("readonly")
    fun getMatchingUniques(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag)
            // Same as .filter | .flatMap, but more cpu/mem performant (7.7 GB vs ?? for test)
            .flatMap {
                when {
                    it.isTimedTriggerable -> emptySequence()
                    !it.conditionalsApply(state) -> emptySequence()
                    else -> it.getMultiplied(state)
                }
            }

    @Contract("readonly")
    fun hasMatchingUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) = 
        getUniques(uniqueType).any { it.conditionalsApply(state) }

    @Contract("readonly")
    fun hasMatchingUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag)
            .any { it.conditionalsApply(state) }

    @Contract("readonly")
    fun getAllUniques() = innerUniqueMap.values.asSequence().flatten()

    @Contract("readonly")
    fun getTriggeredUniques(trigger: UniqueType, stateForConditionals: StateForConditionals,
                            triggerFilter: (Unique) -> Boolean = { true }): Sequence<Unique> {
        return getAllUniques().filter { unique ->
            unique.getModifiers(trigger).any(triggerFilter) && unique.conditionalsApply(stateForConditionals)
        }.flatMap { it.getMultiplied(stateForConditionals) }
    }
    
    companion object{
        val EMPTY = UniqueMap()
    }
}
