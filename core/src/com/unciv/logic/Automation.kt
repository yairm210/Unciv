package com.unciv.logic

import com.unciv.UnCivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.models.gamebasics.Building
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom
import com.unciv.ui.worldscreen.unit.UnitActions

class Automation {


    internal fun rankTile(tile: TileInfo, civInfo: CivilizationInfo): Float {
        val stats = tile.getTileStats(null, civInfo)
        var rank = 0.0f
        if (stats.food <= 2) rank += stats.food
        else rank += (2 + (stats.food - 2) / 2f) // 1 point for each food up to 2, from there on half a point
        rank += (stats.gold / 2)
        rank += stats.production
        rank += stats.science
        rank += stats.culture
        if (tile.improvement == null) rank += 0.5f // improvement potential!
        if (tile.resource != null) rank += 1.0f
        return rank
    }

    fun rankTileAsCityCenter(tileInfo: TileInfo, civInfo: CivilizationInfo): Float {
        val bestTilesFromOuterLayer = tileInfo.tileMap.getTilesAtDistance(tileInfo.position,2)
                .sortedByDescending { rankTile(it,civInfo) }.take(2)
        val top5Tiles = tileInfo.neighbors.union(bestTilesFromOuterLayer)
                .sortedByDescending { rankTile(it,civInfo) }
                .take(5)
        return top5Tiles.map { rankTile(it,civInfo) }.sum()
    }

    fun automateCivMoves(civInfo: CivilizationInfo) {
        if (civInfo.tech.techsToResearch.isEmpty()) {
            val researchableTechs = GameBasics.Technologies.values.filter { civInfo.tech.canBeResearched(it.name) }
            val techToResearch = researchableTechs.minBy { it.cost }
            civInfo.tech.techsResearched.add(techToResearch!!.name)
        }

        for (unit in civInfo.getCivUnits()) {
            automateUnitMoves(unit)
        }

        // train settler?
        if (civInfo.cities.any()
                && civInfo.happiness > 2*civInfo.cities.size +5
                && civInfo.getCivUnits().none { it.name == "Settler" }
                && civInfo.cities.none { it.cityConstructions.currentConstruction == "Settler" }) {

            val bestCity = civInfo.cities.maxBy { it.cityStats.currentCityStats.production }!!
            if(bestCity.cityConstructions.builtBuildings.size > 1) // 2 buildings or more, otherwisse focus on self first
                bestCity.cityConstructions.currentConstruction = "Settler"
        }

        for (city in civInfo.cities) {
            if (city.health < city.getMaxHealth()) trainCombatUnit(city)
        }

    }

    private fun trainCombatUnit(city: CityInfo) {
        city.cityConstructions.currentConstruction = "Archer" // when we have more units then we'll see.
    }

    fun automateUnitMoves(unit: MapUnit) {

        if (unit.name == "Settler") {
            automateSettlerActions(unit)
            return
        }

        if (unit.name == "Worker") {
            WorkerAutomation().automateWorkerAction(unit)
            return
        }


        fun healUnit() {
            // If we're low on health then heal
            // todo: go to a more defensible place if there is one
            val tilesInDistance = unit.getDistanceToTiles().keys
            val unitTile = unit.getTile()

            // Go to friendly tile if within distance - better healing!
            val friendlyTile = tilesInDistance.firstOrNull { it.getOwner()?.civName == unit.owner && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && friendlyTile != null) {
                unit.moveToTile(friendlyTile)
                return
            }

            // Or at least get out of enemy territory yaknow
            val neutralTile = tilesInDistance.firstOrNull { it.getOwner() == null && it.unit == null }
            if (unitTile.getOwner()?.civName != unit.owner && unitTile.getOwner() != null && neutralTile != null) {
                unit.moveToTile(neutralTile)
                return
            }
        }

        if (unit.health < 50) {
            healUnit()
            return
        } // do nothing but heal

        // if there is an attackable unit in the vicinity, attack!
        val attackableTiles = unit.civInfo.getViewableTiles()
                .filter { it.unit != null && it.unit!!.owner != unit.civInfo.civName && !it.isCityCenter }.toHashSet()
        val distanceToTiles = unit.getDistanceToTiles()
        val unitTileToAttack = distanceToTiles.keys.firstOrNull { attackableTiles.contains(it) }

        if (unitTileToAttack != null) {
            val unitToAttack = unitTileToAttack.unit!!
            if (unitToAttack.getBaseUnit().unitType == UnitType.Civilian) { // kill
                unitToAttack.civInfo.addNotification("Our " + unitToAttack.name + " was destroyed by an enemy " + unit.name + "!", unitTileToAttack.position)
                unitTileToAttack.unit = null
                unit.headTowards(unitTileToAttack.position)
                return
            }

            val damageToAttacker = Battle(unit.civInfo.gameInfo).calculateDamageToAttacker(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))

            if (damageToAttacker < unit.health) { // don't attack if we'll die from the attack
                if(unit.getBaseUnit().unitType == UnitType.Melee)
                    unit.headTowards(unitTileToAttack.position)
                Battle(unit.civInfo.gameInfo).attack(MapUnitCombatant(unit), MapUnitCombatant(unitToAttack))
                return
            }
        }

        if (unit.health < 80) {
            healUnit()
            return
        } // do nothing but heal until 80 health


        // else, if there is a reachable spot from which we can attack this turn
        // (say we're an archer and there's a unit 3 tiles away), go there and attack
        // todo

        // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
        val closestUnit = unit.getTile().getTilesInDistance(5)
                .firstOrNull { attackableTiles.contains(it) }

        if (closestUnit != null) {
            unit.headTowards(closestUnit.position)
            return
        }

        if (unit.health < 100) {
            healUnit()
            return
        }

        // else, go to a random space
        unit.moveToTile(distanceToTiles.keys.filter { it.unit == null }.toList().getRandom())
    }

    private fun automateSettlerActions(unit: MapUnit) {
        val tileMap = unit.civInfo.gameInfo.tileMap

        // find best city location within 5 tiles
        val tilesNearCities = unit.civInfo.gameInfo.civilizations.flatMap { it.cities }
                .flatMap { it.getCenterTile().getTilesInDistance(2) }
        val bestCityLocation = unit.getTile().getTilesInDistance(5)
                .minus(tilesNearCities)
                .sortedByDescending { rankTileAsCityCenter(it, unit.civInfo) }
                .first()

        if (unit.getTile() == bestCityLocation)
            UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).first { it.name == "Found city" }.action()
        else {
            unit.headTowards(bestCityLocation.position)
            if (unit.currentMovement > 0 && unit.getTile() == bestCityLocation)
                UnitActions().getUnitActions(unit, UnCivGame.Current.worldScreen!!).first { it.name == "Found city" }.action()
        }
    }


    fun chooseNextConstruction(cityConstructions: CityConstructions) {
        cityConstructions.run {
            val buildableNotWonders = getBuildableBuildings().filterNot { (getConstruction(it) as Building).isWonder }
            val buildableWonders = getBuildableBuildings().filter { (getConstruction(it) as Building).isWonder }

            val civUnits = cityInfo.civInfo.getCivUnits()
            val militaryUnits = civUnits.filter { it.getBaseUnit().unitType != UnitType.Civilian }.size
            val workers = civUnits.filter { it.name == CityConstructions.Worker }.size
            val cities = cityInfo.civInfo.cities.size

            when {
                !buildableNotWonders.isEmpty() -> currentConstruction = buildableNotWonders.first()
                militaryUnits==0 -> trainCombatUnit(cityInfo)
                workers==0 -> currentConstruction = CityConstructions.Worker
                militaryUnits<cities -> trainCombatUnit(cityInfo)
                workers<cities -> currentConstruction = CityConstructions.Worker
                buildableWonders.isNotEmpty() -> currentConstruction = buildableWonders.getRandom()
                else -> trainCombatUnit(cityInfo)
            }

            if (cityInfo.civInfo == cityInfo.civInfo.gameInfo.getPlayerCivilization())
                cityInfo.civInfo.addNotification("Work has started on $currentConstruction", cityInfo.location)
        }
    }

}

