package com.unciv.models.ruleset

import com.unciv.models.stats.Stats
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText


class Unique(val text:String) {
    val placeholderText = text.getPlaceholderText()
    val params = text.getPlaceholderParameters()
    val type = UniqueType.values().firstOrNull { it.placeholderText == placeholderText }

    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }


    fun isOfType(uniqueType: UniqueType) = uniqueType == type

    /** We can't save compliance errors in the unique, since it's ruleset-dependant */
    fun matches(uniqueType: UniqueType, ruleset: Ruleset) = isOfType(uniqueType)
            && uniqueType.getComplianceErrors(this, ruleset).isEmpty()
}


class UniqueMap:HashMap<String, ArrayList<Unique>>() {
    fun addUnique(unique: Unique) {
        if (!containsKey(unique.placeholderText)) this[unique.placeholderText] = ArrayList()
        this[unique.placeholderText]!!.add(unique)
    }

    fun getUniques(placeholderText: String): Sequence<Unique> {
        val result = this[placeholderText]
        if (result == null) return sequenceOf()
        else return result.asSequence()
    }

    fun getUniques(uniqueType: UniqueType) = getUniques(uniqueType.placeholderText)

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }
}