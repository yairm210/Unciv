package com.unciv.logic.city

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.automation.Automation
import com.unciv.logic.map.TileInfo

class CityExpansionManager {
    @Transient
    lateinit var cityInfo: CityInfo
    var cultureStored: Int = 0


    fun clone(): CityExpansionManager {
        val toReturn = CityExpansionManager()
        toReturn.cultureStored=cultureStored
        return toReturn
    }

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that

    fun getCultureToNextTile(): Int {
        val numTilesClaimed = cityInfo.tiles.size - 7
        var cultureToNextTile = 6 * Math.pow(numTilesClaimed + 1.4813, 1.3)
        if (cityInfo.civInfo.getBuildingUniques().contains("Cost of acquiring new tiles reduced by 25%")) cultureToNextTile *= 0.75 //Speciality of Angkor Wat
        if (cityInfo.civInfo.policies.isAdopted("Tradition")) cultureToNextTile *= 0.75
        return Math.round(cultureToNextTile).toInt()
    }


    fun chooseNewTileToOwn(): TileInfo? {
        for (i in 2..5) {
            val tiles = cityInfo.getCenterTile().getTilesInDistance(i)
                    .filter {it.getOwner() == null && it.neighbors.any { tile->tile.getOwner()==cityInfo.civInfo }}
            if (tiles.isEmpty()) continue
            val chosenTile = tiles.maxBy { Automation().rankTile(it,cityInfo.civInfo) }
            return chosenTile
        }
        return null
    }

    //region state-changing functions
    fun reset() {
        for(tile in cityInfo.tiles.map { cityInfo.tileMap[it] })
            relinquishOwnership(tile)

        cityInfo.getCenterTile().getTilesInDistance(1).forEach { takeOwnership(it) }
    }

    private fun addNewTileWithCulture() {
        cultureStored -= getCultureToNextTile()

        val chosenTile = chooseNewTileToOwn()
        if(chosenTile!=null){
            takeOwnership(chosenTile)
        }
    }

    fun relinquishOwnership(tileInfo: TileInfo){
        cityInfo.tiles.remove(tileInfo.position)
        if(cityInfo.workedTiles.contains(tileInfo.position))
            cityInfo.workedTiles.remove(tileInfo.position)
        tileInfo.owningCity=null
    }

    private fun takeOwnership(tileInfo: TileInfo){
        if(tileInfo.getCity()!=null) tileInfo.getCity()!!.expansion.relinquishOwnership(tileInfo)

        cityInfo.tiles.add(tileInfo.position)
        tileInfo.owningCity = cityInfo
        cityInfo.population.autoAssignPopulation()

        for(unit in tileInfo.getUnits())
            if(!unit.civInfo.canEnterTiles(cityInfo.civInfo))
                unit.movementAlgs().teleportToClosestMoveableTile()
    }


    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            addNewTileWithCulture()
            cityInfo.civInfo.addNotification(cityInfo.name + " {has expanded its borders}!", cityInfo.location, Color.PURPLE)
        }
    }

    fun setTransients(){
        for(tile in cityInfo.tiles.map { cityInfo.tileMap[it] })
            tile.owningCity=cityInfo
    }
    //endregion
}