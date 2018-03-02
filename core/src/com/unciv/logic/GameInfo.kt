package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.Notification
import com.unciv.logic.map.TileMap
import com.unciv.models.linq.Linq

class GameInfo {

    var notifications = Linq<Notification>()

    var tutorial = Linq<String>()
    var civilizations = Linq<CivilizationInfo>()
    var tileMap: TileMap = TileMap()
    var turns = 1

    fun getPlayerCivilization(): CivilizationInfo = civilizations[0]

    fun addNotification(text: String, location: Vector2?) {
        notifications.add(Notification(text, location))
    }

    fun nextTurn() {
        notifications.clear()

        for (civInfo in civilizations) civInfo.nextTurn()

        for (tile in tileMap.values.where { it.unit != null })
            tile.nextTurn()

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        for (civInfo in civilizations)
            for (city in civInfo.cities)
                city.cityStats.update()

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
}
