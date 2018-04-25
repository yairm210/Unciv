package com.unciv.logic.city

import com.unciv.logic.Automation

class CityExpansionManager {

    @Transient
    lateinit var cityInfo: CityInfo
    var cultureStored: Int = 0

    fun reset() {
        cityInfo.tiles = ArrayList(cityInfo.getCenterTile().getTilesInDistance(1).map { it.position })
    }

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that

    fun getCultureToNextTile(): Int {
        val numTilesClaimed = cityInfo.tiles.size - 7
        var cultureToNextTile = 6 * Math.pow(numTilesClaimed + 1.4813, 1.3)
        if (cityInfo.civInfo.buildingUniques.contains("NewTileCostReduction")) cultureToNextTile *= 0.75 //Speciality of Angkor Wat
        if (cityInfo.civInfo.policies.isAdopted("Tradition")) cultureToNextTile *= 0.75
        return Math.round(cultureToNextTile).toInt()
    }

    private fun addNewTileWithCulture() {
        cultureStored -= getCultureToNextTile()

        for (i in 2..3) {
            val tiles = cityInfo.getCenterTile().getTilesInDistance(i).filter { it.getOwner() == null }
            if (tiles.isEmpty()) continue
            val chosenTile = tiles.maxBy { Automation().rankTile(it,cityInfo.civInfo) }
            cityInfo.tiles.add(chosenTile!!.position)
            return
        }
    }

    fun nextTurn(culture: Float) {
        cultureStored += culture.toInt()
        if (cultureStored >= getCultureToNextTile()) {
            addNewTileWithCulture()
            cityInfo.civInfo.addNotification(cityInfo.name + " has expanded its borders!", cityInfo.location)
        }
    }

}
