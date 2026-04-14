package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers.canUse
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import yairm210.purity.annotations.Readonly
import com.unciv.logic.automation.Timers.Companion.timeThis

object CivilianUnitAutomation {

    @Readonly
    fun shouldClearTileForAddInCapitalUnits(unit: MapUnit, tile: Tile) =
        tile.isCityCenter() && tile.getCity()!!.isCapital()
        && !unit.hasUnique(UniqueType.AddInCapital)
        && unit.civ.units.getCivUnits().any { unit.hasUnique(UniqueType.AddInCapital) }

    fun automateCivilianUnit(unit: MapUnit, uniqueActionQueue: UniqueActionQueue, dangerousTiles: HashSet<Tile>) 
        = timeThis<Unit>("automateCivilianUnit") {
        // UnitAutomation calls this after useFrequency 120f.
        
        // To allow "found city" actions that can only trigger a limited number of times
        
        // Slightly modified getUsableUnitActionUniques() to allow for settlers with *conditional* settling uniques
        @Readonly
        fun hasSettlerAction(uniqueType: UniqueType) =
            unit.getMatchingUniques(uniqueType, GameContext.IgnoreConditionals)
                .filter { unique -> !unique.hasModifier(UniqueType.UnitActionExtraLimitedTimes) }
                .any { canUse(unit, it) }
        
        val hasSettlerUnique = hasSettlerAction(UniqueType.FoundCity) || hasSettlerAction(UniqueType.FoundPuppetCity)

        // UnitActionType.FoundCity is useFrequency 80f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(80f)
        if (hasSettlerUnique && !(unit.civ.isCityState && unit.isMilitary()))
            return SpecificUnitAutomation.automateSettlerActions(unit, dangerousTiles)
        
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(78f)
        
        if (tryRunAwayIfNeccessary(unit)) return

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(76f)

        if (shouldClearTileForAddInCapitalUnits(unit, unit.currentTile)) {
            // First off get out of the way, then decide if you actually want to do something else
            val tilesCanMoveTo = unit.movement.getDistanceToTiles()
                .filter { unit.movement.canMoveTo(it.key) }
            if (tilesCanMoveTo.isNotEmpty())
                unit.movement.moveToTile(tilesCanMoveTo.minByOrNull { it.value.totalMovement }!!.key)
        }

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(74f)

        if (unit.isAutomatingRoadConnection())
            return unit.civ.getWorkerAutomation().roadToAutomation.automateConnectRoad(unit, dangerousTiles)

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(72f)
        
        if (unit.cache.hasUniqueToBuildImprovements)
            return unit.civ.getWorkerAutomation().automateWorkerAction(unit, uniqueActionQueue, dangerousTiles)


        // UnitActionType.RemoveHeresy is useFrequency 69f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(69f)
        if (unit.civ.religionManager.maySpreadReligionAtAll(unit))
            return ReligiousUnitAutomation.automateMissionary(unit)
        
        // The Actions below this line sacrifice the unit for something big
        // so they have a high useFrequency, but a low automation priority.
        // So we'll treat the automation priority as ~10x less than the useFrequency.
        
        // UnitActionType.Create(Water)Improvement is useFrequency 82f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(8.2f)
        if (unit.cache.hasUniqueToCreateWaterImprovements) {
            if (!unit.civ.getWorkerAutomation().automateWorkBoats(unit)) {
                // UnitActionType.Explore is useFrequency 5f
                uniqueActionQueue.automateUniqueActionsUntilUseFrequency(5f)
                
                UnitAutomation.tryExplore(unit)
                }
            return
        }

        // UnitActionType.FoundReligion is useFrequency 80f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(8.0f)
        if (unit.hasUnique(UniqueType.MayFoundReligion)
            && unit.civ.religionManager.religionState < ReligionState.Religion
            && unit.civ.religionManager.mayFoundReligionAtAll()
        )
            return ReligiousUnitAutomation.foundReligion(unit)
        
        if (unit.hasUnique(UniqueType.MayFoundReligion) && unit.civ.isCityState){
            // We have literally nothing to do with this unit, at least stop costing money
            unit.disband()
            return 
        }
            

        // UnitActionType.EnhanceReligion is useFrequency 79f 
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.9f)            
        if (unit.hasUnique(UniqueType.MayEnhanceReligion)
            && unit.civ.religionManager.religionState < ReligionState.EnhancedReligion
            && unit.civ.religionManager.mayEnhanceReligionAtAll()
        )
            return ReligiousUnitAutomation.enhanceReligion(unit)

        // UnitActionType.AddInCapital is useFrequency 80f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.88f)
        // We try to add any unit in the capital we can, though that might not always be desirable
        // For now its a simple option to allow AI to win a science victory again
        if (unit.hasUnique(UniqueType.AddInCapital))
            return SpecificUnitAutomation.automateAddInCapital(unit)

        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.86f)

        //todo this now supports "Great General"-like mod units not combining 'aura' and citadel
        // abilities, but not additional capabilities if automation finds no use for those two
        if (unit.cache.hasStrengthBonusInRadiusUnique
            && SpecificUnitAutomation.automateGreatGeneral(unit))
            return
        
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.86f)
        
        if (unit.cache.hasCitadelPlacementUnique && SpecificUnitAutomation.automateCitadelPlacer(unit))
            return

        val isLateGame = isLateGame(unit.civ)
        // Great scientist -> Hurry research if late game
        // Great writer -> Hurry policy  if late game
        if (isLateGame) {
            // UnitActionType.HurryResearch is useFrequency 76f
            uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.6f)
            val hurriedResearch = UnitActions.invokeUnitAction(unit, UnitActionType.HurryResearch)
            if (hurriedResearch) return
            
            // UnitActionType.HurryPolicy is useFrequency 76f :(
            uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.4f)
            val hurriedPolicy = UnitActions.invokeUnitAction(unit, UnitActionType.HurryPolicy)
            if (hurriedPolicy) return
            //TODO: save up great scientists/writers for late game (8 turns after research labs/broadcast towers resp.)
        }

        // UnitActionType.ConductTradeMission is useFrequency 70f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.0f)
        // Great merchant -> Conduct trade mission if late game and if not at war.
        // TODO: This could be more complex to walk to the city state that is most beneficial to
        //  also have more influence.
        if (unit.hasUnique(UniqueType.CanTradeWithCityStateForGoldAndInfluence)
            // There's a risk our merchant gets intercepted and killed by the enemy during war.
            // If such happens, it is a failure of our military unit movement to protect our merchant.
            // Barbs might also be a problem, but hopefully by the time we have a great merchant, they're under control.
            && isLateGame
        ) {
            val tradeMissionCanBeConductedEventually =
                SpecificUnitAutomation.conductTradeMission(unit)
            if (tradeMissionCanBeConductedEventually)
                return
        }

        // UnitActionType.HurryWonder is useFrequency 75f
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(7.5f)
        // Great engineer -> Try to speed up wonder construction
        if (unit.hasUnique(UniqueType.CanSpeedupConstruction)
                || unit.hasUnique(UniqueType.CanSpeedupWonderConstruction)) {
            val wonderCanBeSpedUpEventually = SpecificUnitAutomation.speedupWonderConstruction(unit)
            if (wonderCanBeSpedUpEventually)
                return
        }
        
        // UnitActionType.RemoveHeresy is useFrequency 69f 
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(6.9f)
        if (unit.hasUnique(UniqueType.PreventSpreadingReligion) || unit.hasUnique(UniqueType.CanRemoveHeresy))
            return ReligiousUnitAutomation.automateInquisitor(unit)

        // UnitActionType.TriggerUnique(GainFreeBuildings) is useFrequency 80f :(
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(6.6f)
        if (unit.hasUnique(UniqueType.GainFreeBuildings)) {
            val unique = unit.getMatchingUniques(UniqueType.GainFreeBuildings).first()
            val buildingName = unique.params[0]
            // Choose the city that is closest in distance and does not have the building constructed.
            val cityToGainBuilding = unit.civ.cities.filter {
                !it.cityConstructions.containsBuildingOrEquivalent(buildingName)
                    && (unit.movement.canMoveTo(it.getCenterTile()) || unit.currentTile == it.getCenterTile())
            }.map {
                val path = unit.movement.getShortestPath(it.getCenterTile())
                // We want to calc path once, but still filter out unreachable cities
                it to path.size
            }.filter { it.second > 0 }.minByOrNull { it.second }?.first
            

            if (cityToGainBuilding != null) {
                if (unit.currentTile == cityToGainBuilding.getCenterTile()) {
                    UniqueTriggerActivation.triggerUnique(unique, unit.civ, unit = unit, tile = unit.currentTile)
                    UnitActionModifiers.activateSideEffects(unit, unique)
                    return
                }
                else unit.movement.headTowards(cityToGainBuilding.getCenterTile())
            }
            return
        }

        // TODO: The AI tends to have a lot of great generals. Maybe there should be a cutoff
        //  (depending on number of cities) and after that they should just be used to start golden
        //  ages?

        // UnitActionType.ConstructImprovement is useFrequency 85f :(
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(6.4f)
        if (SpecificUnitAutomation.automateImprovementPlacer(unit)) return

        // UnitActionType.TriggerUnique(OneTimeEnterGoldenAgeTurns) is useFrequency 80f :(
        uniqueActionQueue.automateUniqueActionsUntilUseFrequency(6.2f)
        val goldenAgeAction = UnitActions.getUnitActions(unit, UnitActionType.TriggerUnique)
            .filter { it.action != null && it.associatedUnique?.type in listOf(UniqueType.OneTimeEnterGoldenAge,
                UniqueType.OneTimeEnterGoldenAgeTurns) }.firstOrNull()
        if (goldenAgeAction != null) {
            goldenAgeAction.action?.invoke()
            return
        }

        return
    }

    @Readonly
    fun isLateGame(civ: Civilization): Boolean {
        val researchCompletePercent =
            (civ.tech.researchedTechnologies.size * 1.0f) / civ.gameInfo.ruleset.technologies.size
        return researchCompletePercent >= 0.55f
    }

    /** Returns whether the civilian spends its turn hiding and not moving */
    fun tryRunAwayIfNeccessary(unit: MapUnit): Boolean {
        // This is a little 'Bugblatter Beast of Traal': Run if we can attack an enemy
        // Cheaper than determining which enemies could attack us next turn
        val enemyUnitsInWalkingDistance = unit.movement.getDistanceToTiles().keys
            .filter { unit.civ.threatManager.doesTileHaveMilitaryEnemy(it) }

        if (enemyUnitsInWalkingDistance.isNotEmpty() && !unit.baseUnit.isMilitary
            && unit.getTile().militaryUnit == null && !unit.getTile().isCityCenter()) {
            runAway(unit)
            return true
        }

        return false
    }

    private fun runAway(unit: MapUnit) {
        val reachableTiles = unit.movement.getDistanceToTiles()
        val enterableCity = reachableTiles.keys
            .firstOrNull { it.isCityCenter() && unit.movement.canMoveTo(it) }
        if (enterableCity != null) {
            unit.movement.moveToTile(enterableCity)
            return
        }
        val defensiveUnit = reachableTiles.keys
            .firstOrNull {
                it.militaryUnit != null && it.militaryUnit!!.civ == unit.civ && it.civilianUnit == null
            }
        if (defensiveUnit != null) {
            unit.movement.moveToTile(defensiveUnit)
            return
        }

        val unitTile = unit.getTile()
        val dangerousTiles = unit.civ.threatManager.getDangerousTiles(unit)
        val tileClosestToDanger = dangerousTiles
            // Priotirize capture threat over ranged attack
            .sortedByDescending { unit.civ.threatManager.getEnemyUnitsOnTiles(listOf(it)).isNotEmpty() }
            .minByOrNull { it.aerialDistanceTo(unitTile) } ?: unitTile
        val tileFurthestFromDanger = reachableTiles.keys
            .filter {
                unit.movement.canMoveTo(it)
                    && unit.getDamageFromTerrain(it) < unit.health
                    && it !in dangerousTiles }
            .sortedWith(compareByDescending<Tile> { it.aerialDistanceTo(tileClosestToDanger) } // As far away from threat
                .thenByDescending { it.isFriendlyTerritory(unit.civ) }) // Priotirize friendly territory
            .firstOrNull() ?: return // can't move anywhere!

        unit.movement.moveToTile(tileFurthestFromDanger)
    }
}
