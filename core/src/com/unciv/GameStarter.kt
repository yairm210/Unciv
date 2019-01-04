package com.unciv

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.GameBasics
import com.unciv.ui.utils.getRandom
import java.util.*


class GameParameters{
    var difficulty="Prince"
    var nation="Babylon"
    var mapRadius=20
    var numberOfEnemies=3
    var mapType= MapType.Perlin
}

class GameStarter{
    fun startNewGame(newGameParameters: GameParameters): GameInfo {
        val gameInfo = GameInfo()

        gameInfo.gameParameters = newGameParameters
        gameInfo.tileMap = TileMap(newGameParameters.mapRadius, newGameParameters.mapType)
        gameInfo.tileMap.gameInfo = gameInfo // need to set this transient before placing units in the map
        val startingLocations = getStartingLocations(newGameParameters.numberOfEnemies+1,gameInfo.tileMap)


        val playerCiv = CivilizationInfo(newGameParameters.nation)
        gameInfo.difficulty=newGameParameters.difficulty
        playerCiv.playerType=PlayerType.Human
        gameInfo.civilizations.add(playerCiv) // first one is player civ

        val barbarianCivilization = CivilizationInfo()
        gameInfo.civilizations.add(barbarianCivilization)// second is barbarian civ

        for (nationName in GameBasics.Nations.keys.filterNot { it=="Barbarians" || it==newGameParameters.nation }.shuffled()
                .take(newGameParameters.numberOfEnemies)) {
            val civ = CivilizationInfo(nationName)
            gameInfo.civilizations.add(civ)
        }


        barbarianCivilization.civName = "Barbarians"

        gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameinfo set

        for (civInfo in gameInfo.civilizations.filter {!it.isBarbarianCivilization() && !it.isPlayerCivilization()}) {
            for (tech in gameInfo.getDifficulty().aiFreeTechs)
                civInfo.tech.addTechnology(tech)
        }

        // and only now do we add units for everyone, because otherwise both the gameInfo.setTransients() and the placeUnit will both add the unit to the civ's unit list!

        for (civ in gameInfo.civilizations.filter { !it.isBarbarianCivilization() }) {
            val startingLocation = startingLocations.pop()!!

            civ.placeUnitNearTile(startingLocation.position, "Settler")
            civ.placeUnitNearTile(startingLocation.position, "Warrior")
            civ.placeUnitNearTile(startingLocation.position, "Scout")
        }

        return gameInfo
    }

    fun getStartingLocations(numberOfPlayers:Int,tileMap: TileMap): Stack<TileInfo> {
        for(minimumDistanceBetweenStartingLocations in 7 downTo 0){
            val freeTiles = tileMap.values
                    .filter { it.isLand() && vectorIsWithinNTilesOfEdge(it.position,3,tileMap)}
                    .toMutableList()

            val startingLocations = ArrayList<TileInfo>()
            for(player in 0..numberOfPlayers){
                if(freeTiles.isEmpty()) break // we failed to get all the starting locations with this minimum distance
                val randomLocation = freeTiles.getRandom()
                startingLocations.add(randomLocation)
                freeTiles.removeAll(tileMap.getTilesInDistance(randomLocation.position,minimumDistanceBetweenStartingLocations))
            }
            if(startingLocations.size < numberOfPlayers) continue // let's try again with less minimum distance!
            val stack = Stack<TileInfo>()
            stack.addAll(startingLocations)
            return stack
        }
        throw Exception("Didn't manage to get starting locations even with distance of 1?")
    }

    fun vectorIsWithinNTilesOfEdge(vector: Vector2,n:Int, tileMap: TileMap): Boolean {
        val arrayXIndex = vector.x.toInt()-tileMap.leftX
        val arrayYIndex = vector.y.toInt()-tileMap.bottomY

        return arrayXIndex < tileMap.tileMatrix.size-n
                && arrayXIndex > n
                && arrayYIndex < tileMap.tileMatrix[arrayXIndex].size-n
                && arrayYIndex > n
    }

}