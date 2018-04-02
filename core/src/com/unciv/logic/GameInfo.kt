package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.getRandom
import com.unciv.logic.map.TileMap

class GameInfo {

    var notifications = mutableListOf<Notification>()

    var tutorial = mutableListOf<String>()
    var civilizations = mutableListOf<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 1

    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]

    fun addNotification(text: String, location: Vector2?) {
        notifications.add(Notification(text, location))
    }

    fun nextTurn() {
        notifications.clear()

        for (civInfo in civilizations) civInfo.nextTurn()

        for (tile in tileMap.values)
            tile.nextTurn()

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations){
            for (city in civInfo.cities)
                city.cityStats.update()
            civInfo.happiness = civInfo.getHappinessForNextTurn()
            if(!civInfo.isPlayerCivilization())
                automateMoves(civInfo)
        }



        turns++
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


    private fun automateMoves(civInfo: CivilizationInfo) {
        for(unit in civInfo.getCivUnits()){
            // if there is an attackable unit in the vicinity, attack!
            val distanceToTiles = unit.getDistanceToTiles()
            val unitTileToAttack = distanceToTiles.keys.firstOrNull{ it.unit!=null && it.unit!!.owner!=civInfo.civName }
            if(unitTileToAttack!=null){
                Battle().attack(unit,unitTileToAttack.unit!!)
                continue
            }

            // else, if there is a reachable spot from which we can attack this turn
            // (say we're an archer and there's a unit 3 tiles away), go there and attack
            // todo

            // else, find the closest enemy unit that we know of within 5 spaces and advance towards it
            // todo this doesn't take into account which tiles are visible to the civ
            val closestUnit = tileMap.getTilesInDistance(unit.getTile().position, 5)
                    .firstOrNull{ it.unit!=null && it.unit!!.owner!=civInfo.civName }

            if(closestUnit!=null){
                unit.headTowards(closestUnit.position)
                continue
            }

            // else, go to a random space
            unit.moveToTile(distanceToTiles.keys.toList().getRandom())
        }
    }

}
