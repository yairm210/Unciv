package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getConditionals
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import kotlin.random.Random


class Unique(val text: String, val sourceObjectType: UniqueTarget? = null, val sourceObjectName: String? = null) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    val params = text.getPlaceholderParameters()
    val type = UniqueType.uniqueTypeMap[placeholderText]

    val stats: Stats by lazy {
        val firstStatParam = params.firstOrNull { Stats.isStats(it) }
        if (firstStatParam == null) Stats() // So badly-defined stats don't crash the entire game
        else Stats.parse(firstStatParam)
    }
    val conditionals: List<Unique> = text.getConditionals()

    val isTriggerable = type != null && (
        type.targetTypes.contains(UniqueTarget.Triggerable)
            || type.targetTypes.contains(UniqueTarget.UnitTriggerable)
            || conditionals.any { it.type == UniqueType.ConditionalTimedUnique }
        )

    val allParams = params + conditionals.flatMap { it.params }

    val isLocalEffect = params.contains("in this city") || conditionals.any { it.type == UniqueType.ConditionalInThisCity }

    fun hasFlag(flag: UniqueFlag) = type != null && type.flags.contains(flag)

    fun hasTriggerConditional(): Boolean {
        if (conditionals.none()) return false
        return conditionals.any { conditional ->
            conditional.type?.targetTypes?.any {
                it.canAcceptUniqueTarget(UniqueTarget.TriggerCondition) || it.canAcceptUniqueTarget(UniqueTarget.UnitActionModifier)
            }
            ?: false
        }
    }

    fun isOfType(uniqueType: UniqueType) = uniqueType == type

    fun conditionalsApply(civInfo: Civilization? = null, city: City? = null): Boolean {
        return conditionalsApply(StateForConditionals(civInfo, city))
    }

    fun conditionalsApply(state: StateForConditionals = StateForConditionals()): Boolean {
        if (state.ignoreConditionals) return true
        for (condition in conditionals) {
            if (!conditionalApplies(condition, state)) return false
        }
        return true
    }

    fun getDeprecationAnnotation(): Deprecated? = type?.getDeprecationAnnotation()

    fun getReplacementText(ruleset: Ruleset): String {
        val deprecationAnnotation = getDeprecationAnnotation() ?: return ""
        val replacementUniqueText = deprecationAnnotation.replaceWith.expression
        val deprecatedUniquePlaceholders = type!!.text.getPlaceholderParameters()
        val possibleUniques = replacementUniqueText.split(Constants.uniqueOrDelimiter)

        // Here, for once, we DO want the conditional placeholder parameters together with the regular ones,
        //  so we cheat the conditional detector by removing the '<'
        //  note this is only done for the replacement, not the deprecated unique, thus parameters of
        //  conditionals on the deprecated unique are ignored

        val finalPossibleUniques = ArrayList<String>()

        for (possibleUnique in possibleUniques) {
            var resultingUnique = possibleUnique
            for (parameter in possibleUnique.replace('<', ' ').getPlaceholderParameters()) {
                val parameterHasSign = parameter.startsWith('-') || parameter.startsWith('+')
                val parameterUnsigned = if (parameterHasSign) parameter.drop(1) else parameter
                val parameterNumberInDeprecatedUnique = deprecatedUniquePlaceholders.indexOf(parameterUnsigned)
                if (parameterNumberInDeprecatedUnique !in params.indices) continue
                val positionInDeprecatedUnique = type.text.indexOf("[$parameterUnsigned]")
                var replacementText = params[parameterNumberInDeprecatedUnique]
                if (UniqueParameterType.Number in type.parameterTypeMap[parameterNumberInDeprecatedUnique]) {
                    // The following looks for a sign just before [amount] and detects replacing "-[-33]" with "[+33]" and similar situations
                    val deprecatedHadPlusSign = positionInDeprecatedUnique > 0 && type.text[positionInDeprecatedUnique - 1] == '+'
                    val deprecatedHadMinusSign = positionInDeprecatedUnique > 0 && type.text[positionInDeprecatedUnique - 1] == '-'
                    val deprecatedHadSign = deprecatedHadPlusSign || deprecatedHadMinusSign
                    val positionInNewUnique = possibleUnique.indexOf("[$parameter]")
                    val newHasMinusSign = positionInNewUnique > 0 && possibleUnique[positionInNewUnique - 1] == '-'
                    val replacementHasMinusSign = replacementText.startsWith('-')
                    val replacementHasPlusSign = replacementText.startsWith('+')
                    val replacementIsSigned = replacementHasPlusSign || replacementHasMinusSign
                    val replacementTextUnsigned = if (replacementIsSigned) replacementText.drop(1) else replacementText
                    val replacementShouldBeNegative = if (deprecatedHadMinusSign == newHasMinusSign) replacementHasMinusSign else !replacementHasMinusSign
                    val replacementShouldBeSigned = deprecatedHadSign && !newHasMinusSign || parameterHasSign
                    replacementText = when {
                        !(deprecatedHadSign || newHasMinusSign || replacementIsSigned) -> replacementText
                        replacementShouldBeNegative -> "-$replacementTextUnsigned"
                        replacementShouldBeSigned -> "+$replacementTextUnsigned"
                        else -> replacementTextUnsigned
                    }
                }
                resultingUnique = resultingUnique.replace("[$parameter]", "[$replacementText]")
            }
            finalPossibleUniques += resultingUnique
        }
        if (finalPossibleUniques.size == 1) return finalPossibleUniques.first()

        // filter out possible replacements that are obviously wrong
        val uniquesWithNoErrors = finalPossibleUniques.filter {
            val unique = Unique(it)
            val errors = UniqueValidator(ruleset).checkUnique(
                unique, true, null,
                UniqueType.UniqueComplianceErrorSeverity.RulesetSpecific
            )
            errors.isEmpty()
        }
        if (uniquesWithNoErrors.size == 1) return uniquesWithNoErrors.first()

        val uniquesToUnify = uniquesWithNoErrors.ifEmpty { possibleUniques }
        return uniquesToUnify.joinToString("\", \"")
    }

    private fun conditionalApplies(
        condition: Unique,
        state: StateForConditionals
    ): Boolean {

        if (condition.type?.targetTypes?.any { it.modifierType == UniqueTarget.ModifierType.Other } == true)
            return true // not a filtering condition

        fun ruleset() = state.civInfo!!.gameInfo.ruleset

        val relevantUnit by lazy {
            if (state.ourCombatant != null && state.ourCombatant is MapUnitCombatant) state.ourCombatant.unit
            else state.unit
        }

        val relevantTile by lazy { state.attackedTile
            ?: state.tile
            ?: relevantUnit?.getTile()
            ?: state.city?.getCenterTile()
        }

        val stateBasedRandom by lazy { Random(state.hashCode()) }

        fun getResourceAmount(resourceName: String): Int {
            if (state.city != null) return state.city.getResourceAmount(resourceName)
            if (state.civInfo != null) return state.civInfo.getResourceAmount(resourceName)
            return 0
        }

        return when (condition.type) {
            // These are 'what to do' and not 'when to do' conditionals
            UniqueType.ConditionalTimedUnique -> true

            UniqueType.ConditionalChance -> stateBasedRandom.nextFloat() < condition.params[0].toFloat() / 100f
            UniqueType.ConditionalBeforeTurns -> state.civInfo != null && state.civInfo.gameInfo.turns < condition.params[0].toInt()
            UniqueType.ConditionalAfterTurns -> state.civInfo != null && state.civInfo.gameInfo.turns >= condition.params[0].toInt()


            UniqueType.ConditionalNationFilter -> state.civInfo?.nation?.matchesFilter(condition.params[0]) == true
            UniqueType.ConditionalWar -> state.civInfo?.isAtWar() == true
            UniqueType.ConditionalNotWar -> state.civInfo?.isAtWar() == false
            UniqueType.ConditionalWithResource -> getResourceAmount(condition.params[0]) > 0
            UniqueType.ConditionalWithoutResource -> getResourceAmount(condition.params[0]) <= 0
            UniqueType.ConditionalWhenAboveAmountResource -> getResourceAmount(condition.params[1]) > condition.params[0].toInt()
            UniqueType.ConditionalWhenBelowAmountResource -> getResourceAmount(condition.params[1]) < condition.params[0].toInt()
            UniqueType.ConditionalHappy ->
                state.civInfo != null && state.civInfo.stats.happiness >= 0
            UniqueType.ConditionalBetweenHappiness ->
                state.civInfo != null
                && condition.params[0].toInt() <= state.civInfo.stats.happiness
                && state.civInfo.stats.happiness < condition.params[1].toInt()
            UniqueType.ConditionalBelowHappiness ->
                state.civInfo != null && state.civInfo.stats.happiness < condition.params[0].toInt()
            UniqueType.ConditionalGoldenAge ->
                state.civInfo != null && state.civInfo.goldenAges.isGoldenAge()
            UniqueType.ConditionalWLTKD ->
                state.city != null && state.city.isWeLoveTheKingDayActive()
            UniqueType.ConditionalBeforeEra ->
                state.civInfo != null && ruleset().eras.containsKey(condition.params[0])
                    && state.civInfo.getEraNumber() < ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalStartingFromEra ->
                state.civInfo != null && ruleset().eras.containsKey(condition.params[0])
                    &&  state.civInfo.getEraNumber() >= ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalDuringEra ->
                state.civInfo != null && ruleset().eras.containsKey(condition.params[0])
                    &&  state.civInfo.getEraNumber() == ruleset().eras[condition.params[0]]!!.eraNumber
            UniqueType.ConditionalIfStartingInEra ->
                state.civInfo != null && state.civInfo.gameInfo.gameParameters.startingEra == condition.params[0]
            UniqueType.ConditionalTech ->
                state.civInfo != null && state.civInfo.tech.isResearched(condition.params[0])
            UniqueType.ConditionalNoTech ->
                state.civInfo != null && !state.civInfo.tech.isResearched(condition.params[0])
            UniqueType.ConditionalAfterPolicyOrBelief ->
                state.civInfo != null && (state.civInfo.policies.isAdopted(condition.params[0])
                    || state.civInfo.religionManager.religion?.hasBelief(condition.params[0]) == true)
            UniqueType.ConditionalBeforePolicyOrBelief ->
                state.civInfo != null && !state.civInfo.policies.isAdopted(condition.params[0])
                    && state.civInfo.religionManager.religion?.hasBelief(condition.params[0]) != true
            UniqueType.ConditionalBeforePantheon ->
                state.civInfo != null && state.civInfo.religionManager.religionState == ReligionState.None
            UniqueType.ConditionalAfterPantheon ->
                state.civInfo != null && state.civInfo.religionManager.religionState != ReligionState.None
            UniqueType.ConditionalBeforeReligion ->
                state.civInfo != null && state.civInfo.religionManager.religionState < ReligionState.Religion
            UniqueType.ConditionalAfterReligion ->
                state.civInfo != null && state.civInfo.religionManager.religionState >= ReligionState.Religion
            UniqueType.ConditionalBeforeEnhancingReligion ->
                state.civInfo != null && state.civInfo.religionManager.religionState < ReligionState.EnhancedReligion
            UniqueType.ConditionalAfterEnhancingReligion ->
                state.civInfo != null && state.civInfo.religionManager.religionState >= ReligionState.EnhancedReligion
            UniqueType.ConditionalBuildingBuilt ->
                state.civInfo != null && state.civInfo.cities.any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }

            // Filtered via city.getMatchingUniques
            UniqueType.ConditionalInThisCity -> true
            UniqueType.ConditionalCityWithBuilding ->
                state.city != null && state.city.cityConstructions.containsBuildingOrEquivalent(condition.params[0])
            UniqueType.ConditionalCityWithoutBuilding ->
                state.city != null && !state.city.cityConstructions.containsBuildingOrEquivalent(condition.params[0])
            UniqueType.ConditionalPopulationFilter ->
                state.city != null && state.city.population.getPopulationFilterAmount(condition.params[1]) >= condition.params[0].toInt()
            UniqueType.ConditionalWhenGarrisoned ->
                state.city != null && state.city.getCenterTile().militaryUnit != null && state.city.getCenterTile().militaryUnit!!.canGarrison()

            UniqueType.ConditionalVsCity -> state.theirCombatant?.matchesCategory("City") == true
            UniqueType.ConditionalVsUnits -> state.theirCombatant?.matchesCategory(condition.params[0]) == true
            UniqueType.ConditionalOurUnit, UniqueType.ConditionalOurUnitOnUnit ->
                relevantUnit?.matchesFilter(condition.params[0]) == true
            UniqueType.ConditionalUnitWithPromotion -> relevantUnit?.promotions?.promotions?.contains(condition.params[0]) == true
            UniqueType.ConditionalUnitWithoutPromotion -> relevantUnit?.promotions?.promotions?.contains(condition.params[0]) == false
            UniqueType.ConditionalAttacking -> state.combatAction == CombatAction.Attack
            UniqueType.ConditionalDefending -> state.combatAction == CombatAction.Defend
            UniqueType.ConditionalAboveHP ->
                state.ourCombatant != null && state.ourCombatant.getHealth() > condition.params[0].toInt()
            UniqueType.ConditionalBelowHP ->
                state.ourCombatant != null && state.ourCombatant.getHealth() < condition.params[0].toInt()
            UniqueType.ConditionalHasNotUsedOtherActions ->
                state.unit != null &&
                    // OLD format
                state.unit.run { limitedActionsUnitCanDo().all { abilityUsesLeft[it] == maxAbilityUses[it] } }
                    // NEW format
                    && state.unit.abilityToTimesUsed.isEmpty()

            UniqueType.ConditionalInTiles ->
                relevantTile?.matchesFilter(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalInTilesNot ->
                relevantTile?.matchesFilter(condition.params[0], state.civInfo) == false
            UniqueType.ConditionalFightingInTiles ->
                state.attackedTile?.matchesFilter(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalInTilesAnd ->
                relevantTile != null && relevantTile!!.matchesFilter(condition.params[0], state.civInfo)
                        && relevantTile!!.matchesFilter(condition.params[1], state.civInfo)
            UniqueType.ConditionalNearTiles ->
                relevantTile != null && relevantTile!!.getTilesInDistance(condition.params[0].toInt()).any {
                    it.matchesFilter(condition.params[1])
                }

            UniqueType.ConditionalVsLargerCiv -> {
                val yourCities = state.civInfo?.cities?.size ?: 1
                val theirCities = state.theirCombatant?.getCivInfo()?.cities?.size ?: 0
                yourCities < theirCities
            }
            UniqueType.ConditionalForeignContinent ->
                state.civInfo != null && relevantTile != null
                    && (state.civInfo.cities.isEmpty() || state.civInfo.getCapital() == null
                        || state.civInfo.getCapital()!!.getCenterTile().getContinent()
                            != relevantTile!!.getContinent()
                    )
            UniqueType.ConditionalAdjacentUnit ->
                state.civInfo != null
                && relevantUnit != null
                && relevantTile!!.neighbors.any {
                    it.militaryUnit != null
                    && it.militaryUnit != relevantUnit
                    && it.militaryUnit!!.civ == state.civInfo
                    && it.militaryUnit!!.matchesFilter(condition.params[0])
                }

            UniqueType.ConditionalNeighborTiles ->
                relevantTile != null &&
                        relevantTile!!.neighbors.count {
                            it.matchesFilter(condition.params[2], state.civInfo)
                        } in (condition.params[0].toInt())..(condition.params[1].toInt())
            UniqueType.ConditionalNeighborTilesAnd ->
                relevantTile != null
                && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo)
                    && it.matchesFilter(condition.params[3], state.civInfo)
                } in (condition.params[0].toInt())..(condition.params[1].toInt())

            UniqueType.ConditionalOnWaterMaps -> state.region?.continentID == -1
            UniqueType.ConditionalInRegionOfType -> state.region?.type == condition.params[0]
            UniqueType.ConditionalInRegionExceptOfType -> state.region?.type != condition.params[0]

            UniqueType.ConditionalFirstCivToResearch -> sourceObjectType == UniqueTarget.Tech
                    && state.civInfo != null
                    && state.civInfo.gameInfo.civilizations.none {
                it != state.civInfo && it.isMajorCiv() && (it.tech.isResearched(sourceObjectName!!) || it.policies.isAdopted(sourceObjectName))
            }

            else -> false
        }
    }

    override fun toString() = if (type == null) "\"$text\"" else "$type (\"$text\")"
}

/** Used to cache results of getMatchingUniques
 * Must only be used when we're sure the matching uniques will not change in the meantime */
class LocalUniqueCache(val cache:Boolean = true) {
    // This stores sequences *that iterate directly on a list* - that is, pre-resolved
    private val keyToUniques = HashMap<String, Sequence<Unique>>()

    fun forCityGetMatchingUniques(
        city: City,
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(city.civ, city)
    ): Sequence<Unique> {
        // City uniques are a combination of *global civ* uniques plus *city relevant* uniques (see City.getMatchingUniques())
        // We can cache the civ uniques separately, so if we have several cities using the same cache,
        //   we can cache the list of *civ uniques* to reuse between cities.

        val citySpecificUniques = get(
            "city-${city.id}-${uniqueType.name}",
            city.getLocalMatchingUniques(uniqueType, StateForConditionals.IgnoreConditionals)
        ).filter { it.conditionalsApply(stateForConditionals) }

        val civUniques = forCivGetMatchingUniques(city.civ, uniqueType, stateForConditionals)

        return citySpecificUniques + civUniques
    }

    fun forCivGetMatchingUniques(
        civ: Civilization,
        uniqueType: UniqueType,
        stateForConditionals: StateForConditionals = StateForConditionals(
            civ
        )
    ): Sequence<Unique> {
        val sequence = civ.getMatchingUniques(uniqueType, StateForConditionals.IgnoreConditionals)
        // The uniques CACHED are ALL civ uniques, regardless of conditional matching.
        // The uniques RETURNED are uniques AFTER conditional matching.
        // This allows reuse of the cached values, between runs with different conditionals -
        //   for example, iterate on all tiles and get StatPercentForObject uniques relevant for each tile,
        //   each tile will have different conditional state, but they will all reuse the same list of uniques for the civ
        return get(
            "civ-${civ.civName}-${uniqueType.name}",
            sequence
        ).filter { it.conditionalsApply(stateForConditionals) }
    }

    /** Get cached results as a sequence */
    private fun get(key: String, sequence: Sequence<Unique>): Sequence<Unique> {
        if (!cache) return sequence
        if (keyToUniques.containsKey(key)) return keyToUniques[key]!!
        // Iterate the sequence, save actual results as a list, as return a sequence to that
        val results = sequence.toList().asSequence()
        keyToUniques[key] = results
        return results
    }
}

class UniqueMap: HashMap<String, ArrayList<Unique>>() {
    //todo Once all untyped Uniques are converted, this should be  HashMap<UniqueType, *>
    // For now, we can have both map types "side by side" each serving their own purpose,
    // and gradually this one will be deprecated in favor of the other

    /** Adds one [unique] unless it has a ConditionalTimedUnique conditional */
    fun addUnique(unique: Unique) {
        if (unique.conditionals.any { it.type == UniqueType.ConditionalTimedUnique }) return

        val existingArrayList = get(unique.placeholderText)
        if (existingArrayList != null) existingArrayList.add(unique)
        else this[unique.placeholderText] = arrayListOf(unique)
    }

    /** Calls [addUnique] on each item from [uniques] */
    fun addUniques(uniques: Iterable<Unique>) {
        for (unique in uniques) addUnique(unique)
    }

    fun getUniques(placeholderText: String): Sequence<Unique> {
        return this[placeholderText]?.asSequence() ?: emptySequence()
    }

    fun getUniques(uniqueType: UniqueType) = getUniques(uniqueType.placeholderText)

    fun getMatchingUniques(uniqueType: UniqueType, state: StateForConditionals) = getUniques(uniqueType)
        .filter { it.conditionalsApply(state) }

    fun getAllUniques() = this.asSequence().flatMap { it.value.asSequence() }

    fun getTriggeredUniques(trigger: UniqueType, stateForConditionals: StateForConditionals): Sequence<Unique> {
        val result = getAllUniques().filter { it.conditionals.any { it.type == trigger } }
            .filter { it.conditionalsApply(stateForConditionals) }
        return result
    }
}


class TemporaryUnique() : IsPartOfGameInfoSerialization {

    constructor(uniqueObject: Unique, turns: Int) : this() {
        unique = uniqueObject.text
        sourceObjectType = uniqueObject.sourceObjectType
        sourceObjectName = uniqueObject.sourceObjectName
        turnsLeft = turns
    }

    var unique: String = ""

    var sourceObjectType: UniqueTarget? = null
    var sourceObjectName: String? = null

    @delegate:Transient
    val uniqueObject: Unique by lazy { Unique(unique, sourceObjectType, sourceObjectName) }

    var turnsLeft: Int = 0
}

fun ArrayList<TemporaryUnique>.endTurn() {
        for (unique in this) {
            if (unique.turnsLeft >= 0)
                unique.turnsLeft -= 1
        }
        removeAll { it.turnsLeft == 0 }
    }

fun ArrayList<TemporaryUnique>.getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals): Sequence<Unique> {
        return this.asSequence()
            .map { it.uniqueObject }
            .filter { it.isOfType(uniqueType) && it.conditionalsApply(stateForConditionals) }
    }
