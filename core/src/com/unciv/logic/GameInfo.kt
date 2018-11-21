package com.unciv.logic

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom

class GameInfo {
    var notifications = mutableListOf<Notification>()
    @Deprecated("As of 2.6.9")
    var tutorial = mutableListOf<String>()
    var civilizations = mutableListOf<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 0

    //region pure functions
    fun clone(): GameInfo {
        val toReturn = GameInfo()
        toReturn.tileMap = tileMap.clone()
        toReturn.civilizations.addAll(civilizations.map { it.clone() })
        toReturn.notifications.addAll(notifications)
        toReturn.turns = turns
        return toReturn
    }

    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]
    fun getBarbarianCivilization(): CivilizationInfo = civilizations[1]
    //endregion

    fun nextTurn() {
        notifications.clear()
        val player = getPlayerCivilization()

        for (civInfo in civilizations) {
            if (civInfo.tech.techsToResearch.isEmpty()) {  // should belong in automation? yes/no?
                val researchableTechs = GameBasics.Technologies.values
                        .filter { !civInfo.tech.isResearched(it.name) && civInfo.tech.canBeResearched(it.name) }
                civInfo.tech.techsToResearch.add(researchableTechs.minBy { it.cost }!!.name)
            }
            civInfo.endTurn()
        }

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations.filterNot { it == player || (it.isDefeated() && !it.isBarbarianCivilization()) }) {
            civInfo.startTurn()
            NextTurnAutomation().automateCivMoves(civInfo)
        }

        if (turns % 10 == 0) { // every 10 turns add a barbarian in a random place
            placeBarbarianUnit(null)
        }

        // Start our turn immediately before the player can made decisions - affects whether our units can commit automated actions and then be attacked immediately etc.

        player.startTurn()

        val enemyUnitsCloseToTerritory = player.viewableTiles
                .filter {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo != player
                            && player.isAtWarWith(it.militaryUnit!!.civInfo)
                            && (it.getOwner() == player || it.neighbors.any { neighbor -> neighbor.getOwner() == player })
                }
        for (enemyUnitTile in enemyUnitsCloseToTerritory) {
            val inOrNear = if (enemyUnitTile.getOwner() == player) "in" else "near"
            val unitName = enemyUnitTile.militaryUnit!!.name
            player.addNotification("An enemy [$unitName] was spotted $inOrNear our territory", enemyUnitTile.position, Color.RED)
        }

        turns++
    }

    fun placeBarbarianUnit(tileToPlace: TileInfo?) {
        var tile = tileToPlace
        if (tileToPlace == null) {
            // Barbarians will only spawn in places that no one can see
            val allViewableTiles = civilizations.filterNot { it.isBarbarianCivilization() }
                    .flatMap { it.viewableTiles }.toHashSet()
            val viableTiles = tileMap.values.filterNot { allViewableTiles.contains(it) || it.militaryUnit != null || it.civilianUnit != null }
            if (viableTiles.isEmpty()) return // no place for more barbs =(
            tile = viableTiles.getRandom()
        }
        tileMap.placeUnitNearTile(tile!!.position, "Warrior", getBarbarianCivilization())
    }

    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        // this is separated into 2 loops because when we activate updateViewableTiles in civ.setTransients,
        //  we try to find new civs, and we check if civ is barbarian, which we can't know unless the gameInfo is already set.
        for (civInfo in civilizations) civInfo.gameInfo = this
        for (civInfo in civilizations) civInfo.setTransients()

        for (civInfo in civilizations) {
            // we have to remove hydro plants from all cities BEFORE we update a single one,
            // because updating leads to getting the building uniques from the civ info,
            // which in turn leads to us trying to get info on all the building in all the cities...
            // which can fail i there's an "unregistered" building anywhere
            for (cityInfo in civInfo.cities) {
                val cityConstructions = cityInfo.cityConstructions
                // As of 2.9.6, removed hydro plant, since it requires rivers, which we do not yet have
                if ("Hydro Plant" in cityConstructions.builtBuildings)
                    cityConstructions.builtBuildings.remove("Hydro Plant")
                if (cityConstructions.currentConstruction == "Hydro Plant") {
                    cityConstructions.currentConstruction = ""
                    cityConstructions.chooseNextConstruction()
                }
            }

            for (cityInfo in civInfo.cities) cityInfo.cityStats.update()
        }
    }

}