package com.unciv.logic.automation.unit

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionModifiers.canUse
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import yairm210.purity.annotations.Readonly
import com.unciv.logic.automation.Timers.Companion.timeThis

object CivilianUnitAutomation {

    @Readonly
    fun shouldClearTileForAddInCapitalUnits(unit: MapUnitView, tile: TileView) =
        tile.isCityCenter() && tile.getCity()?.isCapital() == true
        && !unit.hasUnique(UniqueType.AddInCapital)
        && unit.civ().getUnits().any { it.hasUnique(UniqueType.AddInCapital) }

    fun automateCivilianUnit(unit: MapUnitView, dangerousTiles: HashSet<Tile>): Unit = timeThis("automateCivilianUnit") {
        // Temporary - logic-layer automation below still works on MapUnit
        val mapUnit = unit.getUnit()

        // To allow "found city" actions that can only trigger a limited number of times
        
        // Slightly modified getUsableUnitActionUniques() to allow for settlers with *conditional* settling uniques
        @Readonly
        fun hasSettlerAction(uniqueType: UniqueType) =
            mapUnit.getMatchingUniques(uniqueType, GameContext.IgnoreConditionals)
                .filter { unique -> !unique.hasModifier(UniqueType.UnitActionExtraLimitedTimes) }
                .any { canUse(mapUnit, it) }
        
        val hasSettlerUnique = hasSettlerAction(UniqueType.FoundCity) || hasSettlerAction(UniqueType.FoundPuppetCity)
        
        if (hasSettlerUnique && !(unit.civ().isCityState && unit.isMilitary()))
            return SpecificUnitAutomation.automateSettlerActions(mapUnit, dangerousTiles)

        if (tryRunAwayIfNeccessary(unit)) return

        if (shouldClearTileForAddInCapitalUnits(unit, unit.getTile())) {
            // First off get out of the way, then decide if you actually want to do something else
            val tilesCanMoveTo = mapUnit.movement.getDistanceToTiles()
                .filter { mapUnit.movement.canMoveTo(it.key) }
            if (tilesCanMoveTo.isNotEmpty())
                mapUnit.movement.moveToTile(tilesCanMoveTo.minByOrNull { it.value.totalMovement }!!.key)
        }

        if (unit.isAutomatingRoadConnection())
            return mapUnit.civ.getWorkerAutomation().roadToAutomation.automateConnectRoad(mapUnit, dangerousTiles)

        if (unit.hasUniqueToBuildImprovements())
            return mapUnit.civ.getWorkerAutomation().automateWorkerAction(mapUnit, dangerousTiles)

        if (unit.hasUniqueToCreateWaterImprovements()) {
            if (!mapUnit.civ.getWorkerAutomation().automateWorkBoats(mapUnit))
                UnitAutomation.tryExplore(mapUnit)
            return
        }

        if (unit.hasUnique(UniqueType.MayFoundReligion)
            && mapUnit.civ.religionManager.religionState < ReligionState.Religion
            && mapUnit.civ.religionManager.mayFoundReligionAtAll()
        )
            return ReligiousUnitAutomation.foundReligion(mapUnit)
        
        if (unit.hasUnique(UniqueType.MayFoundReligion) && unit.civ().isCityState){
            // We have literally nothing to do with this unit, at least stop costing money
            unit.tryDisband()
            return 
        }
            

        if (unit.hasUnique(UniqueType.MayEnhanceReligion)
            && mapUnit.civ.religionManager.religionState < ReligionState.EnhancedReligion
            && mapUnit.civ.religionManager.mayEnhanceReligionAtAll()
        )
            return ReligiousUnitAutomation.enhanceReligion(mapUnit)

        // We try to add any unit in the capital we can, though that might not always be desirable
        // For now its a simple option to allow AI to win a science victory again
        if (unit.hasUnique(UniqueType.AddInCapital))
            return SpecificUnitAutomation.automateAddInCapital(mapUnit)

        //todo this now supports "Great General"-like mod units not combining 'aura' and citadel
        // abilities, but not additional capabilities if automation finds no use for those two
        if (unit.hasStrengthBonusInRadiusUnique()
            && SpecificUnitAutomation.automateGreatGeneral(mapUnit))
            return
        if (unit.hasCitadelPlacementUnique() && SpecificUnitAutomation.automateCitadelPlacer(mapUnit))
            return

        if (mapUnit.civ.religionManager.maySpreadReligionAtAll(mapUnit))
            return ReligiousUnitAutomation.automateMissionary(mapUnit)

        if (unit.hasUnique(UniqueType.PreventSpreadingReligion) || unit.hasUnique(UniqueType.CanRemoveHeresy))
            return ReligiousUnitAutomation.automateInquisitor(mapUnit)

        val isLateGame = isLateGame(mapUnit.civ)
        // Great scientist -> Hurry research if late game
        // Great writer -> Hurry policy  if late game
        if (isLateGame) {
            val hurriedResearch = UnitActions.invokeUnitAction(mapUnit, UnitActionType.HurryResearch)
            if (hurriedResearch) return

            val hurriedPolicy = UnitActions.invokeUnitAction(mapUnit, UnitActionType.HurryPolicy)
            if (hurriedPolicy) return
            //TODO: save up great scientists/writers for late game (8 turns after research labs/broadcast towers resp.)
        }

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
                SpecificUnitAutomation.conductTradeMission(mapUnit)
            if (tradeMissionCanBeConductedEventually)
                return
        }

        // Great engineer -> Try to speed up wonder construction
        if (unit.hasUnique(UniqueType.CanSpeedupConstruction)
                || unit.hasUnique(UniqueType.CanSpeedupWonderConstruction)) {
            val wonderCanBeSpedUpEventually = SpecificUnitAutomation.speedupWonderConstruction(mapUnit)
            if (wonderCanBeSpedUpEventually)
                return
        }

        if (unit.hasUnique(UniqueType.GainFreeBuildings)) {
            val unique = mapUnit.getMatchingUniques(UniqueType.GainFreeBuildings).first()
            val buildingName = unique.params[0]
            // Choose the city that is closest in distance and does not have the building constructed.
            val cityToGainBuilding = mapUnit.civ.cities.filter {
                !it.cityConstructions.containsBuildingOrEquivalent(buildingName)
                    && (mapUnit.movement.canMoveTo(it.getCenterTile()) || mapUnit.currentTile == it.getCenterTile())
            }.map {
                val path = mapUnit.movement.getShortestPath(it.getCenterTile())
                // We want to calc path once, but still filter out unreachable cities
                it to path.size
            }.filter { it.second > 0 }.minByOrNull { it.second }?.first
            

            if (cityToGainBuilding != null) {
                if (mapUnit.currentTile == cityToGainBuilding.getCenterTile()) {
                    UniqueTriggerActivation.triggerUnique(unique, mapUnit.civ, unit = mapUnit, tile = mapUnit.currentTile)
                    UnitActionModifiers.activateSideEffects(mapUnit, unique)
                    return
                }
                else mapUnit.movement.headTowards(cityToGainBuilding.getCenterTile())
            }
            return
        }

        // TODO: The AI tends to have a lot of great generals. Maybe there should be a cutoff
        //  (depending on number of cities) and after that they should just be used to start golden
        //  ages?

        if (SpecificUnitAutomation.automateImprovementPlacer(mapUnit)) return
        
        val goldenAgeAction = UnitActions.getUnitActions(mapUnit, UnitActionType.TriggerUnique)
            .filter { it.action != null && it.associatedUnique?.type in listOf(UniqueType.OneTimeEnterGoldenAge,
                UniqueType.OneTimeEnterGoldenAgeTurns) }.firstOrNull()
        if (goldenAgeAction != null) {
            goldenAgeAction.action?.invoke()
            return
        }

        return // The AI doesn't know how to handle unknown civilian units
    }

    @Readonly
    fun isLateGame(civ: Civilization): Boolean {
        val researchCompletePercent =
            (civ.tech.researchedTechnologies.size * 1.0f) / civ.gameInfo.ruleset.technologies.size
        return researchCompletePercent >= 0.55f
    }

    /** Returns whether the civilian spends its turn hiding and not moving */
    fun tryRunAwayIfNeccessary(unit: MapUnitView): Boolean {
        val mapUnit = unit.getUnit()
        // This is a little 'Bugblatter Beast of Traal': Run if we can attack an enemy
        // Cheaper than determining which enemies could attack us next turn
        val enemyUnitsInWalkingDistance = mapUnit.movement.getDistanceToTiles().keys
            .filter { mapUnit.civ.threatManager.doesTileHaveMilitaryEnemy(it) }

        if (enemyUnitsInWalkingDistance.isNotEmpty() && !unit.getBaseUnit().isMilitary
            && mapUnit.getTile().militaryUnit == null && !mapUnit.getTile().isCityCenter()) {
            runAway(unit)
            return true
        }

        return false
    }

    private fun runAway(unit: MapUnitView) {
        val mapUnit = unit.getUnit()
        val reachableTiles = mapUnit.movement.getDistanceToTiles()
        val enterableCity = reachableTiles.keys
            .firstOrNull { it.isCityCenter() && mapUnit.movement.canMoveTo(it) }
        if (enterableCity != null) {
            mapUnit.movement.moveToTile(enterableCity)
            return
        }
        val defensiveUnit = reachableTiles.keys
            .firstOrNull {
                it.militaryUnit != null && it.militaryUnit!!.civ == mapUnit.civ && it.civilianUnit == null
            }
        if (defensiveUnit != null) {
            mapUnit.movement.moveToTile(defensiveUnit)
            return
        }

        val unitTile = mapUnit.getTile()
        val dangerousTiles = mapUnit.civ.threatManager.getDangerousTiles(mapUnit)
        val tileClosestToDanger = dangerousTiles
            // Priotirize capture threat over ranged attack
            .sortedByDescending { mapUnit.civ.threatManager.getEnemyUnitsOnTiles(listOf(it)).isNotEmpty() }
            .minByOrNull { it.aerialDistanceTo(unitTile) } ?: unitTile
        val tileFurthestFromDanger = reachableTiles.keys
            .filter {
                mapUnit.movement.canMoveTo(it)
                    && mapUnit.getDamageFromTerrain(it) < mapUnit.health
                    && it !in dangerousTiles }
            .sortedWith(compareByDescending<Tile> { it.aerialDistanceTo(tileClosestToDanger) } // As far away from threat
                .thenByDescending { it.isFriendlyTerritory(mapUnit.civ) }) // Priotirize friendly territory
            .firstOrNull() ?: return // can't move anywhere!

        mapUnit.movement.moveToTile(tileFurthestFromDanger)
    }
}
