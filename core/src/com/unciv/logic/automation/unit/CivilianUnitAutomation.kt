package com.unciv.logic.automation.unit

import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions

object CivilianUnitAutomation {

    fun shouldClearTileForAddInCapitalUnits(unit: MapUnit, tile:Tile) = tile.getCity()?.isCapital() == true
        && !unit.hasUnique(UniqueType.AddInCapital)
        && unit.civ.units.getCivUnits().any { unit.hasUnique(UniqueType.AddInCapital) }

    fun automateCivilianUnit(unit: MapUnit) {
        if (tryRunAwayIfNeccessary(unit)) return

        if (shouldClearTileForAddInCapitalUnits(unit, unit.currentTile)) {
            // First off get out of the way, then decide if you actually want to do something else
            val tilesCanMoveTo = unit.movement.getDistanceToTiles()
                .filter { unit.movement.canMoveTo(it.key) }
            if (tilesCanMoveTo.isNotEmpty())
                unit.movement.moveToTile(tilesCanMoveTo.minByOrNull { it.value.totalDistance }!!.key)
        }

        val tilesWhereWeWillBeCaptured = unit.civ.threatManager.getEnemyMilitaryUnitsInDistance(unit.getTile(),5)
            .flatMap { it.movement.getReachableTilesInCurrentTurn() }
            .filter { it.militaryUnit?.civ != unit.civ }
            .toSet()

        if (unit.hasUnique(UniqueType.FoundCity))
            return SpecificUnitAutomation.automateSettlerActions(unit, tilesWhereWeWillBeCaptured)

        if(unit.isAutomatingRoadConnection())
            return unit.civ.getWorkerAutomation().automateConnectRoad(unit, tilesWhereWeWillBeCaptured)

        if (unit.cache.hasUniqueToBuildImprovements)
            return unit.civ.getWorkerAutomation().automateWorkerAction(unit, tilesWhereWeWillBeCaptured)

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
        if (unit.cache.hasCitadelPlacementUnique || unit.cache.hasStrengthBonusInRadiusUnique)
            return SpecificUnitAutomation.automateGreatGeneralFallback(unit)

        if (unit.civ.religionManager.maySpreadReligionAtAll(unit))
            return ReligiousUnitAutomation.automateMissionary(unit)

        if (unit.hasUnique(UniqueType.PreventSpreadingReligion) || unit.canDoLimitedAction(Constants.removeHeresy))
            return ReligiousUnitAutomation.automateInquisitor(unit)

        val isLateGame = isLateGame(unit.civ)
        // Great scientist -> Hurry research if late game
        if (isLateGame) {
            val hurriedResearch = UnitActions.invokeUnitAction(unit, UnitActionType.HurryResearch)
            if (hurriedResearch) return
        }

        // Great merchant -> Conduct trade mission if late game and if not at war.
        // TODO: This could be more complex to walk to the city state that is most beneficial to
        //  also have more influence.
        if (unit.hasUnique(UniqueType.CanTradeWithCityStateForGoldAndInfluence)
            // Don't wander around with the great merchant when at war. Barbs might also be a
            // problem, but hopefully by the time we have a great merchant, they're under control.
            && !unit.civ.isAtWar()
            && isLateGame
        ) {
            val tradeMissionCanBeConductedEventually =
                SpecificUnitAutomation.conductTradeMission(unit)
            if (tradeMissionCanBeConductedEventually)
                return
        }

        // Great engineer -> Try to speed up wonder construction if late game
        if (isLateGame &&
            (unit.hasUnique(UniqueType.CanSpeedupConstruction)
                || unit.hasUnique(UniqueType.CanSpeedupWonderConstruction))) {
            val wonderCanBeSpedUpEventually = SpecificUnitAutomation.speedupWonderConstruction(unit)
            if (wonderCanBeSpedUpEventually)
                return
        }


        // This has to come after the individual abilities for the great people that can also place
        // instant improvements (e.g. great scientist).
        if (unit.hasUnique(UniqueType.ConstructImprovementInstantly)) {
            // catch great prophet for civs who can't found/enhance/spread religion
            // includes great people plus moddable units
            val improvementCanBePlacedEventually =
                SpecificUnitAutomation.automateImprovementPlacer(unit)
            if (!improvementCanBePlacedEventually)
                UnitActions.invokeUnitAction(unit, UnitActionType.StartGoldenAge)
        }

        // TODO: The AI tends to have a lot of great generals. Maybe there should be a cutoff
        //  (depending on number of cities) and after that they should just be used to start golden
        //  ages?

        return // The AI doesn't know how to handle unknown civilian units
    }

    private fun isLateGame(civ: Civilization): Boolean {
        val researchCompletePercent =
            (civ.tech.researchedTechnologies.size * 1.0f) / civ.gameInfo.ruleset.technologies.size
        return researchCompletePercent >= 0.8f
    }

    /** Returns whether the civilian spends its turn hiding and not moving */
    private fun tryRunAwayIfNeccessary(unit: MapUnit): Boolean {
        // This is a little 'Bugblatter Beast of Traal': Run if we can attack an enemy
        // Cheaper than determining which enemies could attack us next turn
        val enemyUnitsInWalkingDistance = unit.movement.getDistanceToTiles().keys
            .filter { unit.civ.threatManager.doesTileHaveMilitaryEnemy(it) }

        if (enemyUnitsInWalkingDistance.isNotEmpty() && !unit.baseUnit.isMilitary()
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
        val tileFurthestFromEnemy = reachableTiles.keys
            .filter { unit.movement.canMoveTo(it) && unit.getDamageFromTerrain(it) < unit.health }
            .maxByOrNull { unit.civ.threatManager.getDistanceToClosestEnemyUnit(unit.getTile(), 4, false) }
            ?: return // can't move anywhere!
        unit.movement.moveToTile(tileFurthestFromEnemy)
    }

}
