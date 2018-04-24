package com.unciv.logic

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom

class GameInfo {

    var notifications = mutableListOf<Notification>()

    var tutorial = mutableListOf<String>()
    var civilizations = mutableListOf<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 1


    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]
    fun getBarbarianCivilization(): CivilizationInfo = civilizations[1]

    fun nextTurn() {
        notifications.clear()

        for (civInfo in civilizations){
            if(civInfo.tech.techsToResearch.isEmpty()){
                val researchableTechs = GameBasics.Technologies.values
                        .filter { !civInfo.tech.isResearched(it.name) && civInfo.tech.canBeResearched(it.name) }
                civInfo.tech.techsToResearch.add(researchableTechs.minBy { it.cost }!!.name)
            }
        }

        for (civInfo in civilizations)
            civInfo.nextTurn()

        tileMap.values.filter { it.unit!=null }.map { it.unit!! }.forEach { it.nextTurn() }

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations){
            if(!civInfo.isPlayerCivilization())
                Automation().automateCivMoves(civInfo)
            for (city in civInfo.cities)
                city.cityStats.update()
            civInfo.happiness = civInfo.getHappinessForNextTurn()
        }

        if(turns%10 == 0){ // every 10 turns add a barbarian in a random place
            placeBarbarianUnit()
        }

        turns++
    }

    fun placeBarbarianUnit() {
        val playerViewableTiles = getPlayerCivilization().getViewableTiles().toHashSet()
        val viableTiles = tileMap.values.filterNot { playerViewableTiles.contains(it) || it.unit!=null }
        tileMap.placeUnitNearTile(viableTiles.getRandom().position,"Warrior",getBarbarianCivilization())
    }

    fun setTransients() {
        tileMap.gameInfo = this
        tileMap.setTransients()

        for (civInfo in civilizations) {
            civInfo.gameInfo = this
            civInfo.setTransients()
        }

        for (tile in tileMap.values.filter { it.unit!=null })
            tile.unit!!.civInfo = civilizations.first { it.civName == tile.unit!!.owner }


        for (civInfo in civilizations)
            for (cityInfo in civInfo.cities)
                cityInfo.cityStats.update()
    }



}

