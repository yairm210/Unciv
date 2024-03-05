package com.unciv.models.ruleset.unique

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.CombatAction
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.models.stats.Stat
import kotlin.random.Random

object Conditionals {

    fun conditionalApplies(
        unique: Unique,
        condition: Unique,
        state: StateForConditionals
    ): Boolean {

        if (condition.type?.targetTypes?.any { it.modifierType == UniqueTarget.ModifierType.Other } == true)
            return true // not a filtering condition, includes e.g. ModifierHiddenFromUsers

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

        val relevantCiv by lazy {
            state.civInfo ?:
            relevantCity?.civ ?:
            relevantUnit?.civ
        }

        val gameInfo by lazy { relevantCiv?.gameInfo }

        val stateBasedRandom by lazy { Random(state.hashCode()) }

        fun getResourceAmount(resourceName: String): Int {
            if (relevantCity != null) return relevantCity!!.getResourceAmount(resourceName)
            if (relevantCiv != null) return relevantCiv!!.getResourceAmount(resourceName)
            return 0
        }

        /** Helper to simplify conditional tests requiring gameInfo */
        fun checkOnGameInfo(predicate: (GameInfo.() -> Boolean)): Boolean {
            if (gameInfo == null) return false
            return gameInfo!!.predicate()
        }

        /** Helper to simplify conditional tests requiring a Civilization */
        fun checkOnCiv(predicate: (Civilization.() -> Boolean)): Boolean {
            if (relevantCiv == null) return false
            return relevantCiv!!.predicate()
        }

        /** Helper to simplify conditional tests requiring a City */
        fun checkOnCity(predicate: (City.() -> Boolean)): Boolean {
            if (relevantCity == null) return false
            return relevantCity!!.predicate()
        }

        /** Helper to simplify the "compare civ's current era with named era" conditions */
        fun compareEra(eraParam: String, compare: (civEra: Int, paramEra: Int) -> Boolean): Boolean {
            if (gameInfo == null) return false
            val era = gameInfo!!.ruleset.eras[eraParam] ?: return false
            return compare(relevantCiv!!.getEraNumber(), era.eraNumber)
        }

        /** Helper for ConditionalWhenAboveAmountStatResource and its below counterpart */
        fun checkResourceOrStatAmount(
            resourceOrStatName: String, 
            lowerLimit: Float, 
            upperLimit: Float, 
            modifyByGameSpeed: Boolean = false,
            compare: (current: Int, lowerLimit: Float, upperLimit: Float) -> Boolean
        ): Boolean {
            if (gameInfo == null) return false
            var gameSpeedModifier = if (modifyByGameSpeed) gameInfo!!.speed.modifier else 1f

            if (gameInfo!!.ruleset.tileResources.containsKey(resourceOrStatName))
                return compare(getResourceAmount(resourceOrStatName), lowerLimit * gameSpeedModifier, upperLimit * gameSpeedModifier)
            val stat = Stat.safeValueOf(resourceOrStatName)
                ?: return false
            val statReserve = if (relevantCity != null) relevantCity!!.getStatReserve(stat) else relevantCiv!!.getStatReserve(stat)

            gameSpeedModifier = if (modifyByGameSpeed) gameInfo!!.speed.statCostModifiers[stat]!! else 1f
            return compare(statReserve, lowerLimit * gameSpeedModifier, upperLimit * gameSpeedModifier)
        }

        return when (condition.type) {
            // These are 'what to do' and not 'when to do' conditionals
            UniqueType.ConditionalTimedUnique -> true

            UniqueType.ConditionalChance -> stateBasedRandom.nextFloat() < condition.params[0].toFloat() / 100f
            UniqueType.ConditionalEveryTurns -> checkOnGameInfo { turns % condition.params[0].toInt() == 0 }
            UniqueType.ConditionalBeforeTurns -> checkOnGameInfo { turns < condition.params[0].toInt() }
            UniqueType.ConditionalAfterTurns -> checkOnGameInfo { turns >= condition.params[0].toInt() }

            UniqueType.ConditionalCivFilter -> checkOnCiv { matchesFilter(condition.params[0]) }
            UniqueType.ConditionalWar -> checkOnCiv { isAtWar() }
            UniqueType.ConditionalNotWar -> checkOnCiv { !isAtWar() }
            UniqueType.ConditionalWithResource -> getResourceAmount(condition.params[0]) > 0
            UniqueType.ConditionalWithoutResource -> getResourceAmount(condition.params[0]) <= 0

            UniqueType.ConditionalWhenAboveAmountStatResource ->
                checkResourceOrStatAmount(condition.params[1], condition.params[0].toFloat(), Float.MAX_VALUE) 
                    { current, lowerLimit, _ -> current > lowerLimit }
            UniqueType.ConditionalWhenBelowAmountStatResource ->
                checkResourceOrStatAmount(condition.params[1], Float.MIN_VALUE, condition.params[0].toFloat()) 
                    { current, _, upperLimit -> current < upperLimit }
            UniqueType.ConditionalWhenBetweenStatResource ->
                checkResourceOrStatAmount(condition.params[2], condition.params[0].toFloat(), condition.params[1].toFloat()) 
                    { current, lowerLimit, upperLimit -> current >= lowerLimit && current <= upperLimit }
            UniqueType.ConditionalWhenAboveAmountStatResourceSpeed ->
                checkResourceOrStatAmount(condition.params[1], condition.params[0].toFloat(), Float.MAX_VALUE, true) 
                    { current, lowerLimit, _ -> current > lowerLimit }
            UniqueType.ConditionalWhenBelowAmountStatResourceSpeed ->
                checkResourceOrStatAmount(condition.params[1], Float.MIN_VALUE, condition.params[0].toFloat(), true) 
                    { current, _, upperLimit -> current < upperLimit }
            UniqueType.ConditionalWhenBetweenStatResourceSpeed -> 
                checkResourceOrStatAmount(condition.params[2], condition.params[0].toFloat(), condition.params[1].toFloat(), true) 
                    { current, lowerLimit, upperLimit -> current >= lowerLimit && current <= upperLimit }

            UniqueType.ConditionalHappy -> checkOnCiv { stats.happiness >= 0 }
            UniqueType.ConditionalBetweenHappiness ->
                checkOnCiv { stats.happiness in condition.params[0].toInt() .. condition.params[1].toInt() }
            UniqueType.ConditionalAboveHappiness -> checkOnCiv { stats.happiness > condition.params[0].toInt() }
            UniqueType.ConditionalBelowHappiness -> checkOnCiv { stats.happiness < condition.params[0].toInt() }
            UniqueType.ConditionalGoldenAge -> checkOnCiv { goldenAges.isGoldenAge() }

            UniqueType.ConditionalBeforeEra -> compareEra(condition.params[0]) { current, param -> current < param }
            UniqueType.ConditionalStartingFromEra -> compareEra(condition.params[0]) { current, param -> current >= param }
            UniqueType.ConditionalDuringEra -> compareEra(condition.params[0]) { current, param -> current == param }
            UniqueType.ConditionalIfStartingInEra -> checkOnGameInfo { gameParameters.startingEra == condition.params[0] }
            UniqueType.ConditionalTech -> checkOnCiv { tech.isResearched(condition.params[0]) }
            UniqueType.ConditionalNoTech -> checkOnCiv { !tech.isResearched(condition.params[0]) }
            UniqueType.ConditionalWhileResearching -> checkOnCiv { tech.currentTechnologyName() == condition.params[0] }

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
            UniqueType.ConditionalAfterGeneratingGreatProphet ->
                checkOnCiv { religionManager.greatProphetsEarned() > 0 }

            UniqueType.ConditionalBuildingBuilt ->
                checkOnCiv { cities.any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) } }
            UniqueType.ConditionalBuildingBuiltAll ->
                checkOnCiv { cities.filter { it.matchesFilter(condition.params[1]) }.all {
                  it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) } }
            UniqueType.ConditionalBuildingBuiltAmount ->
                checkOnCiv { cities.count { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0])
                    && it.matchesFilter(condition.params[2]) } >= condition.params[1].toInt() }
            UniqueType.ConditionalBuildingBuiltByAnybody ->
                checkOnGameInfo { getCities().any { it.cityConstructions.containsBuildingOrEquivalent(condition.params[0]) } }

            // Filtered via city.getMatchingUniques
            UniqueType.ConditionalInThisCity -> relevantCity != null
            UniqueType.ConditionalCityFilter -> checkOnCity { matchesFilter(condition.params[0], relevantCiv) }
            UniqueType.ConditionalCityConnected -> checkOnCity { isConnectedToCapital() }
            UniqueType.ConditionalCityMajorReligion -> checkOnCity {
                religion.getMajorityReligion()?.isMajorReligion() == true }
            UniqueType.ConditionalCityEnhancedReligion -> checkOnCity {
                religion.getMajorityReligion()?.isEnhancedReligion() == true }
            UniqueType.ConditionalCityThisReligion -> checkOnCity {
                religion.getMajorityReligion() == relevantCiv?.religionManager?.religion }
            UniqueType.ConditionalWLTKD -> checkOnCity { isWeLoveTheKingDayActive() }
            UniqueType.ConditionalCityWithBuilding ->
                checkOnCity { cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }
            UniqueType.ConditionalCityWithoutBuilding ->
                checkOnCity { !cityConstructions.containsBuildingOrEquivalent(condition.params[0]) }
            UniqueType.ConditionalPopulationFilter ->
                checkOnCity { population.getPopulationFilterAmount(condition.params[1]) >= condition.params[0].toInt() }
            UniqueType.ConditionalExactPopulationFilter ->
                checkOnCity { population.getPopulationFilterAmount(condition.params[1]) == condition.params[0].toInt() }
            UniqueType.ConditionalWhenGarrisoned ->
                checkOnCity { getCenterTile().militaryUnit?.canGarrison() == true }

            UniqueType.ConditionalVsCity -> state.theirCombatant?.matchesFilter("City") == true
            UniqueType.ConditionalVsUnits,  UniqueType.ConditionalVsCombatant -> state.theirCombatant?.matchesFilter(condition.params[0]) == true
            UniqueType.ConditionalOurUnit, UniqueType.ConditionalOurUnitOnUnit ->
                relevantUnit?.matchesFilter(condition.params[0]) == true
            UniqueType.ConditionalUnitWithPromotion -> relevantUnit?.promotions?.promotions?.contains(condition.params[0]) == true
            UniqueType.ConditionalUnitWithoutPromotion -> relevantUnit?.promotions?.promotions?.contains(condition.params[0]) == false
            UniqueType.ConditionalAttacking -> state.combatAction == CombatAction.Attack
            UniqueType.ConditionalDefending -> state.combatAction == CombatAction.Defend
            UniqueType.ConditionalAboveHP -> relevantUnit != null && relevantUnit!!.health > condition.params[0].toInt()
                    || state.ourCombatant != null && state.ourCombatant.getHealth() > condition.params[0].toInt()
            UniqueType.ConditionalBelowHP -> relevantUnit != null && relevantUnit!!.health < condition.params[0].toInt()
                    ||state.ourCombatant != null && state.ourCombatant.getHealth() < condition.params[0].toInt()
            UniqueType.ConditionalHasNotUsedOtherActions ->
                state.unit == null || // So we get the action as a valid action in BaseUnit.hasUnique()
                    state.unit.abilityToTimesUsed.isEmpty()

            UniqueType.ConditionalInTiles ->
                relevantTile?.matchesFilter(condition.params[0], relevantCiv) == true
            UniqueType.ConditionalInTilesNot ->
                relevantTile?.matchesFilter(condition.params[0], relevantCiv) == false
            UniqueType.ConditionalAdjacentTo -> relevantTile?.isAdjacentTo(condition.params[0], relevantCiv) == true
            UniqueType.ConditionalNotAdjacentTo -> relevantTile?.isAdjacentTo(condition.params[0], relevantCiv) == false
            UniqueType.ConditionalFightingInTiles ->
                state.attackedTile?.matchesFilter(condition.params[0], relevantCiv) == true
            UniqueType.ConditionalInTilesAnd ->
                relevantTile != null && relevantTile!!.matchesFilter(condition.params[0], relevantCiv)
                    && relevantTile!!.matchesFilter(condition.params[1], relevantCiv)
            UniqueType.ConditionalNearTiles ->
                relevantTile != null && relevantTile!!.getTilesInDistance(condition.params[0].toInt()).any {
                    it.matchesFilter(condition.params[1])
                }

            UniqueType.ConditionalVsLargerCiv -> {
                val yourCities = relevantCiv?.cities?.size ?: 1
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
                relevantCiv != null
                    && relevantUnit != null
                    && relevantTile!!.neighbors.any {
                    it.militaryUnit != null
                        && it.militaryUnit != relevantUnit
                        && it.militaryUnit!!.civ == relevantCiv
                        && it.militaryUnit!!.matchesFilter(condition.params[0])
                }

            UniqueType.ConditionalNeighborTiles ->
                relevantTile != null
                    && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], relevantCiv)
                } in condition.params[0].toInt()..condition.params[1].toInt()
            UniqueType.ConditionalNeighborTilesAnd ->
                relevantTile != null
                    && relevantTile!!.neighbors.count {
                    it.matchesFilter(condition.params[2], relevantCiv)
                        && it.matchesFilter(condition.params[3], relevantCiv)
                } in condition.params[0].toInt()..condition.params[1].toInt()

            UniqueType.ConditionalOnWaterMaps -> state.region?.continentID == -1
            UniqueType.ConditionalInRegionOfType -> state.region?.type == condition.params[0]
            UniqueType.ConditionalInRegionExceptOfType -> state.region?.type != condition.params[0]

            UniqueType.ConditionalFirstCivToResearch ->
                unique.sourceObjectType == UniqueTarget.Tech
                    && checkOnGameInfo { civilizations.none {
                        it != relevantCiv && it.isMajorCiv()
                            && it.tech.isResearched(unique.sourceObjectName!!) // guarded by the sourceObjectType check
                    } }

            UniqueType.ConditionalFirstCivToAdopt ->
                unique.sourceObjectType == UniqueTarget.Policy
                    && checkOnGameInfo { civilizations.none {
                        it != relevantCiv && it.isMajorCiv()
                            && it.policies.isAdopted(unique.sourceObjectName!!) // guarded by the sourceObjectType check
                    } }

            else -> false
        }
    }
}
