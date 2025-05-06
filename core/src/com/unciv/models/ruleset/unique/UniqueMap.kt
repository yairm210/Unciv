package com.unciv.models.ruleset.unique

import java.util.*

open class UniqueMap() {
    private val innerUniqueMap = HashMap<String, ArrayList<Unique>>()

    // *shares* the list of uniques with the other map, to save on memory and allocations
    // This is a memory/speed tradeoff, since there are *600 unique types*,
    // 750 including deprecated, and EnumMap creates a N-sized array where N is the number of objects in the enum
    private val typedUniqueMap = EnumMap<UniqueType, ArrayList<Unique>>(UniqueType::class.java)

    // Another memory-speed tradeoff - enumset is super fast and also super cheap, but it's not nothing
    // This is used to speed up triggered uniques - in other words, when we want to find all uniques with a certain modifier.
    // Rather than mapping all uniques thus triggered, this just stores whether any unique has that trigger - 
    //   because most of the time is spent iterating on uniques, in uniquemaps that have no such trigger in the first place!
    private val triggerEnumSet = EnumSet.noneOf(UniqueType::class.java)

    constructor(uniques: Sequence<Unique>) : this() {
        addUniques(uniques.asIterable())
    }

    fun isEmpty(): Boolean = innerUniqueMap.isEmpty()

    /** Adds one [unique] unless it has a ConditionalTimedUnique conditional */
    open fun addUnique(unique: Unique) {
        val existingArrayList = innerUniqueMap[unique.placeholderText]
        if (existingArrayList != null) existingArrayList.add(unique)
        else innerUniqueMap[unique.placeholderText] = arrayListOf(unique)

        if (unique.type == null) return
        if (typedUniqueMap[unique.type] != null) return
        typedUniqueMap[unique.type] = innerUniqueMap[unique.placeholderText]
        triggerEnumSet.add(unique.type)
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

    // Pure functions

    fun hasUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueType).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    fun hasUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag).any { it.conditionalsApply(state) && !it.isTimedTriggerable }

    fun hasTagUnique(tagUnique: String) =
        innerUniqueMap.containsKey(tagUnique)

    // 160ms vs 1000-1250ms/30s
    fun getUniques(uniqueType: UniqueType) = typedUniqueMap[uniqueType]
        ?.asSequence()
        ?: emptySequence()

    fun getUniques(uniqueTag: String) = innerUniqueMap[uniqueTag]
        ?.asSequence()
        ?: emptySequence()

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

    fun hasMatchingUnique(uniqueType: UniqueType, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueType).any { it.conditionalsApply(state) }

    fun hasMatchingUnique(uniqueTag: String, state: StateForConditionals = StateForConditionals.EmptyState) =
        getUniques(uniqueTag)
            .any { it.conditionalsApply(state) }

    fun getAllUniques() = innerUniqueMap.values.asSequence().flatten()

    fun getTriggeredUniques(trigger: UniqueType, stateForConditionals: StateForConditionals,
                            triggerFilter: (Unique) -> Boolean = { true }): Sequence<Unique> {
        if (!triggerEnumSet.contains(trigger)) return emptySequence() // Common case - no such unique exists
        return getAllUniques().filter { unique ->
            unique.getModifiers(trigger).any(triggerFilter) && unique.conditionalsApply(stateForConditionals)
        }.flatMap { it.getMultiplied(stateForConditionals) }
    }

    companion object{
        val EMPTY = UniqueMap()
    }
}
