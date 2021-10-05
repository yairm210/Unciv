package com.unciv.models.ruleset.unique

import com.unciv.logic.battle.CombatAction
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

    fun conditionalsApply(civInfo: CivilizationInfo? = null, city: CityInfo? = null): Boolean {
        return conditionalsApply(StateForConditionals(civInfo, city))
    }

    fun conditionalsApply(state: StateForConditionals?): Boolean {
        if (state == null) return conditionals.isEmpty() 
        for (condition in conditionals) {
            if (!conditionalApplies(condition, state)) return false
        }
        return true
    }

    private fun conditionalApplies(
        condition: Unique,
        state: StateForConditionals
    ): Boolean {
        return when (condition.type) {
            UniqueType.ConditionalWar -> state.civInfo?.isAtWar() == true
            UniqueType.ConditionalNotWar -> state.civInfo?.isAtWar() == false
            UniqueType.ConditionalHappy -> 
                state.civInfo != null && state.civInfo.statsForNextTurn.happiness >= 0
            UniqueType.ConditionalGoldenAge ->
                state.civInfo != null && state.civInfo.goldenAges.isGoldenAge()

            UniqueType.ConditionalSpecialistCount -> 
                state.cityInfo != null && state.cityInfo.population.getNumberOfSpecialists() >= condition.params[0].toInt()

            UniqueType.ConditionalVsCity ->
                state.theirCombatant != null && state.theirCombatant.matchesCategory("City")
            UniqueType.ConditionalVsUnits ->
                state.theirCombatant != null && state.theirCombatant.matchesCategory(condition.params[0])
            UniqueType.ConditionalOurUnit ->
                (state.ourCombatant != null && state.ourCombatant.matchesCategory(condition.params[0]))
                || (state.unit != null && state.unit.matchesFilter(condition.params[0]))
            UniqueType.ConditionalAttacking -> state.combatAction == CombatAction.Attack
            UniqueType.ConditionalDefending -> state.combatAction == CombatAction.Defend
            UniqueType.ConditionalInTiles -> 
                state.attackedTile != null && state.attackedTile.matchesFilter(condition.params[0])
            UniqueType.ConditionalVsLargerCiv -> {
                val yourCities = state.civInfo?.cities?.size ?: 1
                val theirCities = state.theirCombatant?.getCivInfo()?.cities?.size ?: 0
                yourCities < theirCities
            }

            UniqueType.ConditionalNeighborTiles ->
                state.cityInfo != null &&
                state.cityInfo.getCenterTile().neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo)
                } in (condition.params[0].toInt())..(condition.params[1].toInt())
            UniqueType.ConditionalNeighborTilesAnd ->
                state.cityInfo != null 
                && state.cityInfo.getCenterTile().neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo) &&
                    it.matchesFilter(condition.params[3], state.civInfo)
                } in (condition.params[0].toInt())..(condition.params[1].toInt())
            else -> false
        }
    }

    override fun toString() = if (type == null) "\"$text\"" else "$type (\"$text\")"
}


class UniqueMap: HashMap<String, ArrayList<Unique>>() {
    //todo Once all untyped Uniques are converted, this should be  HashMap<UniqueType, *>
    fun addUnique(unique: Unique) {
        if (!containsKey(unique.placeholderText)) this[unique.placeholderText] = ArrayList()
        this[unique.placeholderText]!!.add(unique)
    }

    fun getUniques(placeholderText: String): Sequence<Unique> {
        return this[placeholderText]?.asSequence() ?: sequenceOf()
    }

    fun getUniques(uniqueType: UniqueType) = getUniques(uniqueType.placeholderText)

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }
}
