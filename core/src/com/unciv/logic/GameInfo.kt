package com.unciv.logic

import com.badlogic.gdx.graphics.Color
import com.unciv.GameParameters
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics

class GameInfo {
    var civilizations = mutableListOf<CivilizationInfo>()
    var difficulty="Chieftain" // difficulty is game-wide, think what would happen if 2 human players could play on different difficulties?
    var tileMap: TileMap = TileMap()
    var gameParameters=GameParameters()
    var turns = 0
    var oneMoreTurnMode=false
    var currentPlayer=""

    //region pure functions
    fun clone(): GameInfo {
        val toReturn = GameInfo()
        toReturn.tileMap = tileMap.clone()
        toReturn.civilizations.addAll(civilizations.map { it.clone() })
        toReturn.currentPlayer=currentPlayer
        toReturn.turns = turns
        toReturn.difficulty=difficulty
        toReturn.gameParameters = gameParameters
        return toReturn
    }

    fun getCivilization(civName:String) = civilizations.first { it.civName==civName }
    fun getCurrentPlayerCivilization() = getCivilization(currentPlayer)
    fun getBarbarianCivilization() = getCivilization("Barbarians")
    fun getDifficulty() = GameBasics.Difficulties[difficulty]!!
    //endregion

    fun nextTurn() {
        val previousHumanPlayer = getCurrentPlayerCivilization()
        var thisPlayer = previousHumanPlayer // not calling is currentPlayer because that's alreay taken and I can't think of a better name
        var currentPlayerIndex = civilizations.indexOf(thisPlayer)

        fun switchTurn(){
            thisPlayer.endTurn()
            currentPlayerIndex = (currentPlayerIndex+1) % civilizations.size
            if(currentPlayerIndex==0){
                turns++
                if (turns % 10 == 0 && !gameParameters.noBarbarians) { // every 10 turns add a barbarian in a random place
                    placeBarbarianUnit(null)
                }
            }
            thisPlayer = civilizations[currentPlayerIndex]
            thisPlayer.startTurn()
        }

        switchTurn()

        while(thisPlayer.playerType==PlayerType.AI){
            NextTurnAutomation().automateCivMoves(thisPlayer)
            switchTurn()
        }

        currentPlayer=thisPlayer.civName

        // Start our turn immediately before the player can made decisions - affects whether our units can commit automated actions and then be attacked immediately etc.

        val enemyUnitsCloseToTerritory = thisPlayer.viewableTiles
                .filter {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo != thisPlayer
                            && thisPlayer.isAtWarWith(it.militaryUnit!!.civInfo)
                            && (it.getOwner() == thisPlayer || it.neighbors.any { neighbor -> neighbor.getOwner() == thisPlayer })
                }

        for (enemyUnitTile in enemyUnitsCloseToTerritory) {
            val inOrNear = if (enemyUnitTile.getOwner() == thisPlayer) "in" else "near"
            val unitName = enemyUnitTile.militaryUnit!!.name
            thisPlayer.addNotification("An enemy [$unitName] was spotted $inOrNear our territory", enemyUnitTile.position, Color.RED)
        }

    }

    fun placeBarbarianUnit(tileToPlace: TileInfo?) {
        var tile = tileToPlace
        if (tileToPlace == null) {
            // Barbarians will only spawn in places that no one can see
            val allViewableTiles = civilizations.filterNot { it.isBarbarianCivilization() }
                    .flatMap { it.viewableTiles }.toHashSet()
            val viableTiles = tileMap.values.filterNot { allViewableTiles.contains(it) || it.militaryUnit != null || it.civilianUnit != null }
            if (viableTiles.isEmpty()) return // no place for more barbs =(
            tile = viableTiles.random()
        }

        // if we don't make this into a separate list then the retain() will happen on the Tech keys,
        // which effectively removes those techs from the game and causes all sorts of problems
        val allResearchedTechs = GameBasics.Technologies.keys.toMutableList()
        for (civ in civilizations.filter { !it.isBarbarianCivilization() && !it.isDefeated() }) {
            allResearchedTechs.retainAll(civ.tech.techsResearched)
        }
        val unitList = GameBasics.Units.values.filter { !it.unitType.isCivilian() && it.uniqueTo == null }
                .filter{ allResearchedTechs.contains(it.requiredTech)
                        && (it.obsoleteTech == null || !allResearchedTechs.contains(it.obsoleteTech!!)) }
        val unit = if (unitList.isEmpty()) "Warrior" else unitList.random().name

        tileMap.placeUnitNearTile(tile!!.position, unit, getBarbarianCivilization())
    }

    // All cross-game data which needs to be altered (e.g. when removing or changing a name of a building/tech)
    // will be done here, and not in CivInfo.setTransients or CityInfo
    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        if(currentPlayer=="") currentPlayer=civilizations[0].civName

        // this is separated into 2 loops because when we activate updateViewableTiles in civ.setTransients,
        //  we try to find new civs, and we check if civ is barbarian, which we can't know unless the gameInfo is already set.
        for (civInfo in civilizations) civInfo.gameInfo = this

        // PlayerType was only added in 2.11.1, so we need to adjust for older saved games
        if(civilizations.all { it.playerType==PlayerType.AI })
            getCurrentPlayerCivilization().playerType=PlayerType.Human
        if(getCurrentPlayerCivilization().difficulty!="Chieftain")
            difficulty= getCurrentPlayerCivilization().difficulty

        // We have to remove all deprecated buildings from all cities BEFORE we update a single one, or run setTransients on the civs,
        // because updating leads to getting the building uniques from the civ info,
        // which in turn leads to us trying to get info on all the building in all the cities...
        // which can fail if there's an "unregistered" building anywhere
        for (civInfo in civilizations) {
            for (cityInfo in civInfo.cities) {
                val cityConstructions = cityInfo.cityConstructions

                // As of 2.9.6, removed hydro plant, since it requires rivers, which we do not yet have
                if ("Hydro Plant" in cityConstructions.builtBuildings)
                    cityConstructions.builtBuildings.remove("Hydro Plant")
                if (cityConstructions.currentConstruction == "Hydro Plant") {
                    cityConstructions.currentConstruction = ""
                    cityConstructions.chooseNextConstruction()
                }

                // As of 2.14.1, changed Machu Pichu to Machu Picchu
                val oldMachuName = "Machu Pichu"
                val newMachuName = "Machu Picchu"
                if(cityConstructions.builtBuildings.contains(oldMachuName)){
                    cityConstructions.builtBuildings.remove(oldMachuName)
                    cityConstructions.builtBuildings.add(newMachuName)
                }
                if (cityConstructions.currentConstruction == oldMachuName)
                    cityConstructions.currentConstruction = newMachuName
                if (cityConstructions.inProgressConstructions.containsKey(oldMachuName)) {
                    cityConstructions.inProgressConstructions[newMachuName] = cityConstructions.inProgressConstructions[oldMachuName]!!
                    cityConstructions.inProgressConstructions.remove(oldMachuName)
                }
            }
        }

        for (civInfo in civilizations) civInfo.setTransients()
        for (civInfo in civilizations){
            for (cityInfo in civInfo.cities) cityInfo.cityStats.update()
        }
    }

}
