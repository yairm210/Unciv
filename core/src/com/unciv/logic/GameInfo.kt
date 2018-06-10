package com.unciv.logic

import com.unciv.logic.automation.Automation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom

class GameInfo {
    var notifications = mutableListOf<Notification>()
    var tutorial = mutableListOf<String>()
    var civilizations = mutableListOf<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 0
    @Transient var tilesToCities = HashMap<TileInfo,CityInfo>()


    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]
    fun getBarbarianCivilization(): CivilizationInfo = civilizations[1]

    fun nextTurn() {
        notifications.clear()

        for (civInfo in civilizations){
            if(civInfo.tech.techsToResearch.isEmpty()){  // should belong in automation? yes/no?
                val researchableTechs = GameBasics.Technologies.values
                        .filter { !civInfo.tech.isResearched(it.name) && civInfo.tech.canBeResearched(it.name) }
                civInfo.tech.techsToResearch.add(researchableTechs.minBy { it.cost }!!.name)
            }
            civInfo.endTurn()
        }

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations.filterNot { it.isPlayerCivilization() }){
            civInfo.startTurn()
            Automation().automateCivMoves(civInfo)
        }

        if(turns%10 == 0){ // every 10 turns add a barbarian in a random place
            placeBarbarianUnit(null)
        }

        // Start our turn immediately before the player can made decisions - affects whether our units can commit automated actions and then be attacked immediately etc.

        getPlayerCivilization().startTurn()

        turns++
    }

    fun placeBarbarianUnit(tileToPlace: TileInfo?) {
        var tile = tileToPlace
        if(tileToPlace==null) {
            val playerViewableTiles = getPlayerCivilization().getViewableTiles().toHashSet()
            val viableTiles = tileMap.values.filterNot { playerViewableTiles.contains(it) || it.militaryUnit != null || it.civilianUnit!=null}
            if(viableTiles.isEmpty()) return // no place for more barbs =(
            tile=viableTiles.getRandom()
        }
        tileMap.placeUnitNearTile(tile!!.position,"Warrior",getBarbarianCivilization())
    }

    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        for (civInfo in civilizations) {
            civInfo.gameInfo = this
            civInfo.setTransients()
        }

        val civNameToCiv = civilizations.associateBy ({ it.civName},{it})

        for (tile in tileMap.values) {
            if (tile.militaryUnit != null) tile.militaryUnit!!.civInfo = civNameToCiv[tile.militaryUnit!!.owner]!!
            if (tile.civilianUnit!= null) tile.civilianUnit!!.civInfo = civNameToCiv[tile.civilianUnit!!.owner]!!
        }


        for (civInfo in civilizations)
            for (cityInfo in civInfo.cities)
                cityInfo.cityStats.update()

        updateTilesToCities()
    }

    fun updateTilesToCities(){
        tilesToCities.clear()
        for (city in civilizations.flatMap { it.cities }){
            for (tile in city.getTiles()) tilesToCities.put(tile,city)
        }
    }
}

