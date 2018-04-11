package com.unciv.logic.city

class CityExpansionManager {

    @Transient
    lateinit var cityInfo: CityInfo
    var cultureStored: Int = 0
    private var tilesClaimed: Int = 0

    // This one has conflicting sources -
    // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
    // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
    //   (per game XML files) at 6*(t+0.4813)^1.3
    // The second seems to be more based, so I'll go with that
    //Speciality of Angkor Wat
    val cultureToNextTile: Int
        get() {
            var cultureToNextTile = 6 * Math.pow(tilesClaimed + 1.4813, 1.3)
            if (cityInfo.civInfo.buildingUniques.contains("NewTileCostReduction")) cultureToNextTile *= 0.75
            if (cityInfo.civInfo.policies.isAdopted("Tradition")) cultureToNextTile *= 0.75
            return Math.round(cultureToNextTile).toInt()
        }

    private fun addNewTileWithCulture() {
        cultureStored -= cultureToNextTile

        for (i in 2..3) {
            val tiles = cityInfo.civInfo.gameInfo.tileMap.getTilesInDistance(cityInfo.cityLocation, i).filter { it.owner == null }
            if (tiles.isEmpty()) continue
            val chosenTile = tiles.maxBy { cityInfo.rankTile(it) }
            chosenTile!!.owner = cityInfo.civInfo.civName
            tilesClaimed++
            return
        }
    }

    fun nextTurn(culture: Float) {

        cultureStored += culture.toInt()
        if (cultureStored >= cultureToNextTile) {
            addNewTileWithCulture()
            cityInfo.civInfo.gameInfo.addNotification(cityInfo.name + " has expanded its borders!", cityInfo.cityLocation)
        }
    }

}
