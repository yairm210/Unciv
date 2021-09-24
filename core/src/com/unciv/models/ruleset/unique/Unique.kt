package com.unciv.models.ruleset.unique

import com.unciv.logic.city.CityInfo
import com.unciv.models.stats.Stats
import com.unciv.models.translations.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.ruleset.Ruleset


class Unique(val text: String, val sourceObjectType: UniqueTarget? = null, val sourceObjectName: String? = null) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    val params = text.removeConditionals().getPlaceholderParameters()
    val type = UniqueType.values().firstOrNull { it.placeholderText == placeholderText }

    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
    val conditionals: List<Unique> = text.getConditionals()

    fun isOfType(uniqueType: UniqueType) = uniqueType == type

    /** We can't save compliance errors in the unique, since it's ruleset-dependant */
    fun matches(uniqueType: UniqueType, ruleset: Ruleset) = isOfType(uniqueType)
        && uniqueType.getComplianceErrors(this, ruleset).isEmpty()

    // This function will get LARGE, as it will basically check for all conditionals if they apply
    // This will require a lot of parameters to be passed (attacking unit, tile, defending unit, civInfo, cityInfo, ...)
    // I'm open for better ideas, but this was the first thing that I could think of that would
    // work in all cases.
    fun conditionalsApply(civInfo: CivilizationInfo? = null, city: CityInfo? = null): Boolean {
        for (condition in conditionals) {
            if (!conditionalApplies(condition, civInfo, city)) return false
        }
        return true
    }

    private fun conditionalApplies(
        condition: Unique,
        civInfo: CivilizationInfo? = null,
        city: CityInfo? = null
    ): Boolean {
        return when (condition.placeholderText) {
            UniqueType.ConditionalNotWar.placeholderText -> civInfo?.isAtWar() == false
            UniqueType.ConditionalWar.placeholderText -> civInfo?.isAtWar() == true
            UniqueType.ConditionalSpecialistCount.placeholderText -> 
                city != null && city.population.getNumberOfSpecialists() >= condition.params[0].toInt()
            UniqueType.ConditionalHappy.placeholderText -> 
                civInfo != null && civInfo.statsForNextTurn.happiness >= 0
            else -> false
        }
    }

    override fun toString() = if (type == null) "\"$text\"" else "$type (\"$text\")"
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
