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

object CivilianUnitAutomation {

    @Readonly
    fun shouldClearTileForAddInCapitalUnits(unit: MapUnit, tile: Tile) =
        tile.isCityCenter() && tile.getCity()!!.isCapital()
        && !unit.hasUnique(UniqueType.AddInCapital)
        && unit.civ.units.getCivUnits().any { unit.hasUnique(UniqueType.AddInCapital) }

    fun automateCivilianUnit(unit: MapUnit, dangerousTiles: HashSet<Tile>) {
        // To allow "found city" actions that can only trigger a limited number of times
        
        // Slightly modified getUsableUnitActionUniques() to allow for settlers with *conditional* settling uniques
        @Readonly
        fun hasSettlerAction(uniqueType: UniqueType) =
            unit.getMatchingUniques(uniqueType, GameContext.IgnoreConditionals)
                .filter { unique -> !unique.hasModifier(UniqueType.UnitActionExtraLimitedTimes) }
                .any { canUse(unit, it) }
        
        val hasSettlerUnique = hasSettlerAction(UniqueType.FoundCity) || hasSettlerAction(UniqueType.FoundPuppetCity)
        
        if (hasSettlerUnique)
            return SpecificUnitAutomation.automateSettlerActions(unit, dangerousTiles)

        if (tryRunAwayIfNeccessary(unit)) return

        if (shouldClearTileForAddInCapitalUnits(unit, unit.currentTile)) {
            // First off get out of the way, then decide if you actually want to do something else
            val tilesCanMoveTo = unit.movement.getDistanceToTiles()
                .filter { unit.movement.canMoveTo(it.key) }
            if (tilesCanMoveTo.isNotEmpty())
                unit.movement.moveToTile(tilesCanMoveTo.minByOrNull { it.value.totalMovement }!!.key)
        }

        if (unit.isAutomatingRoadConnection())
            return unit.civ.getWorkerAutomation().roadToAutomation.automateConnectRoad(unit, dangerousTiles)

        if (unit.cache.hasUniqueToBuildImprovements)
            return unit.civ.getWorkerAutomation().automateWorkerAction(unit, dangerousTiles)

        if (unit.cache.hasUniqueToCreateWaterImprovements) {
            if (!unit.civ.getWorkerAutomation().automateWorkBoats(unit))
                UnitAutomation.tryExplore(unit)
            return
        }

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
            

        if (unit.hasUnique(UniqueType.MayEnhanceReligion)
            && unit.civ.religionManager.religionState < ReligionState.EnhancedReligion
            && unit.civ.religionManager.mayEnhanceReligionAtAll()
        )
            return ReligiousUnitAutomation.enhanceReligion(unit)

        // We try to add any unit in the capital we can, though that might not always be desirable
        // For now its a simple option to allow AI to win a science victory again
        if (unit.hasUnique(UniqueType.AddInCapital))
            return SpecificUnitAutomation.automateAddInCapital(unit)

        //todo this now supports "Great General"-like mod units not combining 'aura' and citadel
        // abilities, but not additional capabilities if automation finds no use for those two
        if (unit.cache.hasStrengthBonusInRadiusUnique
            && SpecificUnitAutomation.automateGreatGeneral(unit))
            return
        if (unit.cache.hasCitadelPlacementUnique && SpecificUnitAutomation.automateCitadelPlacer(unit))
            return

        if (unit.civ.religionManager.maySpreadReligionAtAll(unit))
            return ReligiousUnitAutomation.automateMissionary(unit)

        if (unit.hasUnique(UniqueType.PreventSpreadingReligion) || unit.hasUnique(UniqueType.CanRemoveHeresy))
            return ReligiousUnitAutomation.automateInquisitor(unit)

        val isLateGame = isLateGame(unit.civ)
        // Great scientist -> Hurry research if late game
        // Great writer -> Hurry policy  if late game
        if (isLateGame) {
            val hurriedResearch = UnitActions.invokeUnitAction(unit, UnitActionType.HurryResearch)
            if (hurriedResearch) return

            val hurriedPolicy = UnitActions.invokeUnitAction(unit, UnitActionType.HurryPolicy)
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
                SpecificUnitAutomation.conductTradeMission(unit)
            if (tradeMissionCanBeConductedEventually)
                return
        }

        // Great engineer -> Try to speed up wonder construction
        if (unit.hasUnique(UniqueType.CanSpeedupConstruction)
                || unit.hasUnique(UniqueType.CanSpeedupWonderConstruction)) {
            val wonderCanBeSpedUpEventually = SpecificUnitAutomation.speedupWonderConstruction(unit)
            if (wonderCanBeSpedUpEventually)
                return
        }

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

        if (SpecificUnitAutomation.automateImprovementPlacer(unit)) return
        
        val goldenAgeAction = UnitActions.getUnitActions(unit, UnitActionType.TriggerUnique)
            .filter { it.action != null && it.associatedUnique?.type in listOf(UniqueType.OneTimeEnterGoldenAge,
                UniqueType.OneTimeEnterGoldenAgeTurns) }.firstOrNull()
        if (goldenAgeAction != null) {
            goldenAgeAction.action?.invoke()
            return
        }

        return // The AI doesn't know how to handle unknown civilian units
    }

    @Readonly
    private fun isLateGame(civ: Civilization): Boolean {
        val researchCompletePercent =
            (civ.tech.researchedTechnologies.size * 1.0f) / civ.gameInfo.ruleset.technologies.size
        return researchCompletePercent >= 0.6f
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
