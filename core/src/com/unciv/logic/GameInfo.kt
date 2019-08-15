package com.unciv.logic

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.GameParameters
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.Difficulty
import com.unciv.models.gamebasics.GameBasics
import java.util.*

class GameInfo {
    @Transient lateinit var difficultyObject: Difficulty // Since this is static game-wide, and was taking a large part of nextTurn
    @Transient lateinit var currentPlayerCiv:CivilizationInfo // this is called thousands of times, no reason to search for it with a find{} every time

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
    fun getCurrentPlayerCivilization() = currentPlayerCiv
    fun getBarbarianCivilization() = getCivilization("Barbarians")
    fun getDifficulty() = difficultyObject
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
                if (turns % 10 == 0 && !gameParameters.noBarbarians) {
                    val encampments = tileMap.values.filter { it.improvement==Constants.barbarianEncampment }

                    if(encampments.size < civilizations.filter { it.isMajorCiv() }.size) {
                        val newEncampmentTile = placeBarbarianEncampment(encampments)
                        if (newEncampmentTile != null)
                            placeBarbarianUnit(newEncampmentTile)
                    }

                    val totalBarbariansAllowedOnMap = encampments.size*3
                    var extraBarbarians = totalBarbariansAllowedOnMap - getBarbarianCivilization().getCivUnits().size

                    for (tile in tileMap.values.filter { it.improvement == Constants.barbarianEncampment }) {
                        if(extraBarbarians<=0) break
                        extraBarbarians--
                        placeBarbarianUnit(tile)
                    }
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

        currentPlayer = thisPlayer.civName
        currentPlayerCiv = getCivilization(currentPlayer)

        // Start our turn immediately before the player can made decisions - affects whether our units can commit automated actions and then be attacked immediately etc.

        val enemyUnitsCloseToTerritory = thisPlayer.viewableTiles
                .filter {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo != thisPlayer
                            && thisPlayer.isAtWarWith(it.militaryUnit!!.civInfo)
                            && (it.getOwner() == thisPlayer || it.neighbors.any { neighbor -> neighbor.getOwner() == thisPlayer })
                }

        // enemy units ON our territory
        addEnemyUnitNotification(
                thisPlayer,
                enemyUnitsCloseToTerritory.filter { it.getOwner()==thisPlayer },
                "in"
        )
        // enemy units NEAR our territory
        addEnemyUnitNotification(
                thisPlayer,
                enemyUnitsCloseToTerritory.filter { it.getOwner()!=thisPlayer },
                "near"
        )
    }

    private fun addEnemyUnitNotification(thisPlayer: CivilizationInfo, tiles: List<TileInfo>, inOrNear: String) {
        // don't flood the player with similar messages. instead cycle through units by clicking the message multiple times.
        if (tiles.size < 3) {
            for (tile in tiles) {
                val unitName = tile.militaryUnit!!.name
                thisPlayer.addNotification("An enemy [$unitName] was spotted $inOrNear our territory", tile.position, Color.RED)
            }
        }
        else {
            val positions = tiles.map { it.position }
            thisPlayer.addNotification("[${positions.size}] enemy units were spotted $inOrNear our territory", Color.RED, LocationAction(positions))
        }
    }

    fun placeBarbarianEncampment(existingEncampments: List<TileInfo>): TileInfo? {
        // Barbarians will only spawn in places that no one can see
        val allViewableTiles = civilizations.filterNot { it.isBarbarian() }
                .flatMap { it.viewableTiles }.toHashSet()
        val tilesWithin3ofExistingEncampment = existingEncampments.flatMap { it.getTilesInDistance(3) }
        val viableTiles = tileMap.values.filter {
            !it.getBaseTerrain().impassable && it.isLand
                    && it.terrainFeature==null
                    && it !in tilesWithin3ofExistingEncampment
                    && it !in allViewableTiles
        }
        if (viableTiles.isEmpty()) return null // no place for more barbs =(
        val tile = viableTiles.random()
        tile.improvement = Constants.barbarianEncampment
        return tile
    }

    fun placeBarbarianUnit(tileToPlace: TileInfo) {
        // if we don't make this into a separate list then the retain() will happen on the Tech keys,
        // which effectively removes those techs from the game and causes all sorts of problems
        val allResearchedTechs = GameBasics.Technologies.keys.toMutableList()
        for (civ in civilizations.filter { !it.isBarbarian() && !it.isDefeated() }) {
            allResearchedTechs.retainAll(civ.tech.techsResearched)
        }
        val barbarianCiv = getBarbarianCivilization()
        barbarianCiv.tech.techsResearched = allResearchedTechs.toHashSet()
        val unitList = GameBasics.Units.values
                .filter { !it.unitType.isCivilian()}
                .filter { it.isBuildable(barbarianCiv) }

        val landUnits = unitList.filter { it.unitType.isLandUnit() }
        val waterUnits = unitList.filter { it.unitType.isWaterUnit() }

        val unit:String
        if(waterUnits.isNotEmpty() && tileToPlace.neighbors.any{ it.baseTerrain==Constants.coast } && Random().nextBoolean())
            unit=waterUnits.random().name
        else unit = landUnits.random().name

        tileMap.placeUnitNearTile(tileToPlace.position, unit, getBarbarianCivilization())
    }

    // All cross-game data which needs to be altered (e.g. when removing or changing a name of a building/tech)
    // will be done here, and not in CivInfo.setTransients or CityInfo
    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        if(currentPlayer=="") currentPlayer=civilizations.first { it.isPlayerCivilization() }.civName
        currentPlayerCiv=getCivilization(currentPlayer)

        // this is separated into 2 loops because when we activate updateViewableTiles in civ.setTransients,
        //  we try to find new civs, and we check if civ is barbarian, which we can't know unless the gameInfo is already set.
        for (civInfo in civilizations) civInfo.gameInfo = this

        // PlayerType was only added in 2.11.1, so we need to adjust for older saved games
        if(civilizations.all { it.playerType==PlayerType.AI })
            getCurrentPlayerCivilization().playerType=PlayerType.Human
        if(getCurrentPlayerCivilization().difficulty!="Chieftain")
            difficulty= getCurrentPlayerCivilization().difficulty
        difficultyObject = GameBasics.Difficulties[difficulty]!!

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
                changeBuildingName(cityConstructions, "Machu Pichu", "Machu Picchu")
                // As of 2.16.1, changed Colloseum to Colosseum
                changeBuildingName(cityConstructions, "Colloseum", "Colosseum")
            }
        }

        for (civInfo in civilizations) civInfo.setNationTransient()
        for (civInfo in civilizations) civInfo.setTransients()

        for (civInfo in civilizations){
            for(unit in civInfo.getCivUnits())
                unit.updateViewableTiles() // this needs to be done after all the units are assigned to their civs and all other transients are set

            // Since this depends on the cities of ALL civilizations,
            // we need to wait until we've set the transients of all the cities before we can run this.
            // Hence why it's not in CivInfo.setTransients().
            civInfo.setCitiesConnectedToCapitalTransients()

            // We need to determine the GLOBAL happiness state in order to determine the city stats
            for(cityInfo in civInfo.cities) cityInfo.cityStats.updateCityHappiness()

            for (cityInfo in civInfo.cities) cityInfo.cityStats.update()
        }
    }

    private fun changeBuildingName(cityConstructions: CityConstructions, oldBuildingName: String, newBuildingName: String) {
        if (cityConstructions.builtBuildings.contains(oldBuildingName)) {
            cityConstructions.builtBuildings.remove(oldBuildingName)
            cityConstructions.builtBuildings.add(newBuildingName)
        }
        if (cityConstructions.currentConstruction == oldBuildingName)
            cityConstructions.currentConstruction = newBuildingName
        if (cityConstructions.inProgressConstructions.containsKey(oldBuildingName)) {
            cityConstructions.inProgressConstructions[newBuildingName] = cityConstructions.inProgressConstructions[oldBuildingName]!!
            cityConstructions.inProgressConstructions.remove(oldBuildingName)
        }
    }

}
