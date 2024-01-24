package com.unciv.models.ruleset.unique

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.ruleset.GlobalUniques
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.validation.UniqueValidator
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.getConditionals
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.models.translations.getPlaceholderText
import kotlin.random.Random


class Unique(val text: String, val sourceObjectType: UniqueTarget? = null, val sourceObjectName: String? = null) {
    /** This is so the heavy regex-based parsing is only activated once per unique, instead of every time it's called
     *  - for instance, in the city screen, we call every tile unique for every tile, which can lead to ANRs */
    val placeholderText = text.getPlaceholderText()
    /** Does not include conditional params */
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

    /** Includes conditional params */
    val allParams = params + conditionals.flatMap { it.params }

    val isLocalEffect = params.contains("in this city") || conditionals.any { it.type == UniqueType.ConditionalInThisCity }

    fun hasFlag(flag: UniqueFlag) = type != null && type.flags.contains(flag)
    fun isHiddenToUsers() = hasFlag(UniqueFlag.HiddenToUsers) || conditionals.any { it.type == UniqueType.ModifierHiddenFromUsers }

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
        // Always allow Timed conditional uniques. They are managed elsewhere
        if (conditionals.any { it.isOfType(UniqueType.ConditionalTimedUnique) }) return true
        for (condition in conditionals) {
            if (!conditionalApplies(condition, state)) return false
        }
        return true
    }

    fun getDeprecationAnnotation(): Deprecated? = type?.getDeprecationAnnotation()

    fun getSourceNameForUser(): String {
        return when (sourceObjectType) {
            null -> ""
            UniqueTarget.Global -> GlobalUniques.getUniqueSourceDescription(this)
            UniqueTarget.Wonder -> "Wonders"
            UniqueTarget.Building -> "Buildings"
            UniqueTarget.Policy -> "Policies"
            UniqueTarget.CityState -> Constants.cityStates
            else -> sourceObjectType.name
        }
    }

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
                true
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

        val relevantUnit by lazy {
            if (state.ourCombatant != null && state.ourCombatant is MapUnitCombatant) state.ourCombatant.unit
            else state.unit
        }

        val relevantTile by lazy { state.attackedTile
            ?: state.tile
            // We need to protect against conditionals checking tiles for units pre-placement - see #10425, #10512
            ?: relevantUnit?.run { if (hasTile()) getTile() else null }
            ?: state.city?.getCenterTile()
        }

        val relevantCity by lazy {
            state.city
            ?: relevantTile?.getCity()
        }

        val stateBasedRandom by lazy { Random(state.hashCode()) }

        fun getResourceAmount(resourceName: String): Int {
            if (relevantCity != null) return relevantCity!!.getResourceAmount(resourceName)
            if (state.civInfo != null) return state.civInfo.getResourceAmount(resourceName)
            return 0
        }

        /** Helper to simplify conditional tests requiring a Civilization */
        fun checkOnCiv(predicate: (Civilization.() -> Boolean)): Boolean {
            if (state.civInfo == null) return false
            return state.civInfo.predicate()
        }

        /** Helper to simplify conditional tests requiring a City */
        fun checkOnCity(predicate: (City.() -> Boolean)): Boolean {
            if (relevantCity == null) return false
            return relevantCity!!.predicate()
        }

        /** Helper to simplify the "compare civ's current era with named era" conditions */
        fun compareEra(eraParam: String, compare: (civEra: Int, paramEra: Int) -> Boolean): Boolean {
            if (state.civInfo == null) return false
            val era = state.civInfo.gameInfo.ruleset.eras[eraParam] ?: return false
            return compare(state.civInfo.getEraNumber(), era.eraNumber)
        }

        /** Helper for ConditionalWhenAboveAmountStatResource and its below counterpart */
        fun checkResourceOrStatAmount(compare: (current: Int, limit: Int) -> Boolean): Boolean {
            if (state.civInfo == null) return false
            val limit = condition.params[0].toInt()
            val resourceOrStatName = condition.params[1]
            if (state.civInfo.gameInfo.ruleset.tileResources.containsKey(resourceOrStatName))
                return compare(getResourceAmount(resourceOrStatName), limit)
            val stat = Stat.safeValueOf(resourceOrStatName)
                ?: return false
            return compare(state.civInfo.getStatReserve(stat), limit)
        }

        /** Helper for ConditionalWhenAboveAmountStatSpeed and its below counterpart */
        fun checkResourceOrStatAmountWithSpeed(compare: (current: Int, limit: Float) -> Boolean): Boolean {
            if (state.civInfo == null) return false
            val limit = condition.params[0].toInt()
            val resourceOrStatName = condition.params[1]
            var gameSpeedModifier = state.civInfo.gameInfo.speed.modifier

            if (state.civInfo.gameInfo.ruleset.tileResources.containsKey(resourceOrStatName))
                return compare(getResourceAmount(resourceOrStatName), limit * gameSpeedModifier)
            val stat = Stat.safeValueOf(resourceOrStatName)
                ?: return false

            gameSpeedModifier = state.civInfo.gameInfo.speed.statCostModifiers[stat]!!
            return compare(state.civInfo.getStatReserve(stat), limit * gameSpeedModifier)
        }

        return when (condition.type) {
            // These are 'what to do' and not 'when to do' conditionals
            UniqueType.ConditionalTimedUnique -> true
            UniqueType.ModifierHiddenFromUsers -> true  // allowed to be attached to any Unique to hide it, no-op otherwise

            UniqueType.ConditionalChance -> stateBasedRandom.nextFloat() < condition.params[0].toFloat() / 100f
            UniqueType.ConditionalEveryTurns -> checkOnCiv { gameInfo.turns % condition.params[0].toInt() == 0}
            UniqueType.ConditionalBeforeTurns -> checkOnCiv { gameInfo.turns < condition.params[0].toInt() }
            UniqueType.ConditionalAfterTurns -> checkOnCiv { gameInfo.turns >= condition.params[0].toInt() }

            UniqueType.ConditionalCivFilter -> checkOnCiv { matchesFilter(condition.params[0]) }
            UniqueType.ConditionalWar -> checkOnCiv { isAtWar() }
            UniqueType.ConditionalNotWar -> checkOnCiv { !isAtWar() }
            UniqueType.ConditionalWithResource -> getResourceAmount(condition.params[0]) > 0
            UniqueType.ConditionalWithoutResource -> getResourceAmount(condition.params[0]) <= 0

            UniqueType.ConditionalWhenAboveAmountStatResource ->
                checkResourceOrStatAmount { current, limit -> current > limit }
            UniqueType.ConditionalWhenBelowAmountStatResource ->
                checkResourceOrStatAmount { current, limit -> current < limit }
            UniqueType.ConditionalWhenAboveAmountStatResourceSpeed ->
                checkResourceOrStatAmountWithSpeed { current, limit -> current > limit }  // Note: Int.compareTo(Float)!
            UniqueType.ConditionalWhenBelowAmountStatResourceSpeed ->
                checkResourceOrStatAmountWithSpeed { current, limit -> current < limit }  // Note: Int.compareTo(Float)!

            UniqueType.ConditionalHappy -> checkOnCiv { stats.happiness >= 0 }
            UniqueType.ConditionalBetweenHappiness ->
                checkOnCiv { stats.happiness in condition.params[0].toInt() until condition.params[1].toInt() }
            UniqueType.ConditionalBelowHappiness -> checkOnCiv { stats.happiness < condition.params[0].toInt() }
            UniqueType.ConditionalGoldenAge -> checkOnCiv { goldenAges.isGoldenAge() }
            UniqueType.ConditionalBeforeEra -> compareEra(condition.params[0]) { current, param -> current < param }
            UniqueType.ConditionalStartingFromEra -> compareEra(condition.params[0]) { current, param -> current >= param }
            UniqueType.ConditionalDuringEra -> compareEra(condition.params[0]) { current, param -> current == param }
            UniqueType.ConditionalIfStartingInEra -> checkOnCiv { gameInfo.gameParameters.startingEra == condition.params[0] }
            UniqueType.ConditionalTech -> checkOnCiv { tech.isResearched(condition.params[0]) }
            UniqueType.ConditionalNoTech -> checkOnCiv { !tech.isResearched(condition.params[0]) }
            UniqueType.ConditionalAfterPolicyOrBelief ->
                checkOnCiv { policies.isAdopted(condition.params[0]) || religionManager.religion?.hasBelief(condition.params[0]) == true }
            UniqueType.ConditionalBeforePolicyOrBelief ->
                checkOnCiv { !policies.isAdopted(condition.params[0]) && religionManager.religion?.hasBelief(condition.params[0]) != true }
            UniqueType.ConditionalBeforePantheon ->
                checkOnCiv { religionManager.religionState == ReligionState.None }
            UniqueType.ConditionalAfterPantheon ->
                checkOnCiv { religionManager.religionState != ReligionState.None }
            UniqueType.ConditionalBeforeReligion ->
                checkOnCiv { religionManager.religionState < ReligionState.Religion }
            UniqueType.ConditionalAfterReligion ->
                checkOnCiv { religionManager.religionState >= ReligionState.Religion }
            UniqueType.ConditionalBeforeEnhancingReligion ->
                checkOnCiv { religionManager.religionState < ReligionState.EnhancedReligion }
            UniqueType.ConditionalAfterEnhancingReligion ->
                checkOnCiv { religionManager.religionState >= ReligionState.EnhancedReligion }
            UniqueType.ConditionalBuildingBuilt ->
                checkOnCiv { cities.any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) } }
            UniqueType.ConditionalBuildingBuiltByAnybody ->
                checkOnCiv { gameInfo.getCities().any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) } }

            // Filtered via city.getMatchingUniques
            UniqueType.ConditionalInThisCity -> true
            UniqueType.ConditionalWLTKD -> checkOnCity { isWeLoveTheKingDayActive() }
            UniqueType.ConditionalCityWithBuilding ->
                checkOnCity { cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }
            UniqueType.ConditionalCityWithoutBuilding ->
                checkOnCity { !cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }
            UniqueType.ConditionalPopulationFilter ->
                checkOnCity { population.getPopulationFilterAmount(condition.params[1]) >= condition.params[0].toInt() }
            UniqueType.ConditionalWhenGarrisoned ->
                checkOnCity { getCenterTile().militaryUnit?.canGarrison() == true }

            UniqueType.ConditionalVsCity -> state.theirCombatant?.matchesFilter("City") == true
            UniqueType.ConditionalVsUnits -> state.theirCombatant?.matchesFilter(condition.params[0]) == true
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
                state.unit == null || // So we get the action as a valid action in BaseUnit.hasUnique()
                    state.unit.abilityToTimesUsed.isEmpty()
            UniqueType.ConditionalInTiles ->
                relevantTile?.matchesFilter(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalInTilesNot ->
                relevantTile?.matchesFilter(condition.params[0], state.civInfo) == false
            UniqueType.ConditionalAdjacentTo -> relevantTile?.isAdjacentTo(condition.params[0], state.civInfo) == true
            UniqueType.ConditionalNotAdjacentTo -> relevantTile?.isAdjacentTo(condition.params[0], state.civInfo) == false
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
            UniqueType.ConditionalForeignContinent -> checkOnCiv {
                    relevantTile != null && (
                        cities.isEmpty() || getCapital() == null
                        || getCapital()!!.getCenterTile().getContinent() != relevantTile!!.getContinent()
                    )
                }
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
                relevantTile != null
                && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo)
                } in condition.params[0].toInt()..condition.params[1].toInt()
            UniqueType.ConditionalNeighborTilesAnd ->
                relevantTile != null
                && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], state.civInfo)
                    && it.matchesFilter(condition.params[3], state.civInfo)
                } in condition.params[0].toInt()..condition.params[1].toInt()

            UniqueType.ConditionalOnWaterMaps -> state.region?.continentID == -1
            UniqueType.ConditionalInRegionOfType -> state.region?.type == condition.params[0]
            UniqueType.ConditionalInRegionExceptOfType -> state.region?.type != condition.params[0]

            UniqueType.ConditionalFirstCivToResearch ->
                state.civInfo != null && sourceObjectType == UniqueTarget.Tech
                && state.civInfo.gameInfo.civilizations.none {
                    it != state.civInfo && it.isMajorCiv()
                        && it.tech.isResearched(sourceObjectName!!) // guarded by the sourceObjectType check
                }
            UniqueType.ConditionalFirstCivToAdopt ->
                state.civInfo != null && sourceObjectType == UniqueTarget.Policy
                && state.civInfo.gameInfo.civilizations.none {
                    it != state.civInfo && it.isMajorCiv()
                        && it.policies.isAdopted(sourceObjectName!!) // guarded by the sourceObjectType check
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
        return getAllUniques().filter { unique ->
            unique.conditionals.any { it.type == trigger }
            && unique.conditionalsApply(stateForConditionals)
        }
    }
}


class TemporaryUnique() : IsPartOfGameInfoSerialization {

    constructor(uniqueObject: Unique, turns: Int) : this() {
        val turnsText = uniqueObject.conditionals.first { it.isOfType(UniqueType.ConditionalTimedUnique) }.text
        unique = uniqueObject.text.replaceFirst("<$turnsText>", "")
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
