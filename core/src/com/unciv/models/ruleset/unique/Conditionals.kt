package com.unciv.models.ruleset.unique

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
            UniqueType.ConditionalAfterGeneratingGreatProphet ->
                checkOnCiv { religionManager.greatProphetsEarned() > 0 }

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
            UniqueType.ConditionalVsUnits,  UniqueType.ConditionalVsCombatant -> state.theirCombatant?.matchesFilter(condition.params[0]) == true
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
                state.civInfo != null && unique.sourceObjectType == UniqueTarget.Tech
                    && state.civInfo.gameInfo.civilizations.none {
                    it != state.civInfo && it.isMajorCiv()
                        && it.tech.isResearched(unique.sourceObjectName!!) // guarded by the sourceObjectType check
                }
            UniqueType.ConditionalFirstCivToAdopt ->
                state.civInfo != null && unique.sourceObjectType == UniqueTarget.Policy
                    && state.civInfo.gameInfo.civilizations.none {
                    it != state.civInfo && it.isMajorCiv()
                        && it.policies.isAdopted(unique.sourceObjectName!!) // guarded by the sourceObjectType check
                }

            else -> false
        }
    }
}
