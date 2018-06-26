package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.tr

open class TileInfo {
    @Transient lateinit var tileMap: TileMap

    var unit:MapUnit?=null
    var militaryUnit:MapUnit?=null
    var civilianUnit:MapUnit?=null
    fun getUnits()= listOf(militaryUnit,civilianUnit).filterNotNull()

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeature: String? = null
    var resource: String? = null
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0

    fun getCity(): CityInfo? {
        return tileMap.gameInfo.tilesToCities.get(this)
        //return tileMap.gameInfo.civilizations.flatMap { it.cities }.firstOrNull{it.tiles.contains(position)}
    }

    val lastTerrain: Terrain
        get() = if (terrainFeature == null) getBaseTerrain() else getTerrainFeature()!!

    val tileResource: TileResource
        get() = if (resource == null) throw Exception("No resource exists for this tile!") else GameBasics.TileResources[resource!!]!!

    fun isCityCenter(): Boolean = getCity()?.location == position

    val tileImprovement: TileImprovement?
        get() = if (improvement == null) null else GameBasics.TileImprovements[improvement!!]


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more CPU efficient to save the list once and for all!
    @Transient private var internalNeighbors : List<TileInfo>?=null
    val neighbors: List<TileInfo>
        get(){
            if(internalNeighbors==null)
                internalNeighbors = tileMap.getTilesAtDistance(position, 1)
            return internalNeighbors!!
        }

    val height: Int
        get() {
            var height = 0
            if (listOf("Forest", "Jungle").contains(terrainFeature)) height += 1
            if ("Hill" == baseTerrain) height += 2
            return height
        }

    fun getBaseTerrain(): Terrain {
        return GameBasics.Terrains[baseTerrain]!!
    }

    fun getOwner(): CivilizationInfo? {
        val containingCity = getCity()
        if(containingCity==null) return null
        return containingCity.civInfo
    }

    fun getTerrainFeature(): Terrain? {
        return if (terrainFeature == null) null else GameBasics.Terrains[terrainFeature!!]
    }


    fun getTileStats(observingCiv: CivilizationInfo): Stats {
        return getTileStats(getCity(), observingCiv)
    }

    fun getTileStats(city: CityInfo?, observingCiv: CivilizationInfo): Stats {
        var stats = getBaseTerrain().clone()

        if (terrainFeature != null) {
            val terrainFeature = getTerrainFeature()
            if (terrainFeature!!.overrideStats)
                stats = terrainFeature.clone()
            else
                stats.add(terrainFeature)
        }

        if (hasViewableResource(observingCiv)) {
            val resource = tileResource
            stats.add(tileResource) // resource base
            if (resource.building != null && city != null && city.cityConstructions.isBuilt(resource.building!!)) {
                stats.add(resource.getBuilding()!!.resourceBonusStats!!) // resource-specific building (eg forge, stable) bonus
            }
        }

        val improvement = tileImprovement
        if (improvement != null) {
            if (resource != null && tileResource.improvement == improvement.name)
                stats.add(tileResource.improvementStats!!) // resource-specifc improvement
            else
                stats.add(improvement) // basic improvement

            if (improvement.improvingTech != null && observingCiv.tech.isResearched(improvement.improvingTech!!)) stats.add(improvement.improvingTechStats!!) // eg Chemistry for mines
            if (improvement.name == "Trading post" && city != null && city.civInfo.policies.isAdopted("Free Thought"))
                stats.science += 1f
            if (improvement.name in listOf("Academy", "Landmark", "Manufactory", "Customs house") && observingCiv.policies.isAdopted("Freedom Complete"))
                stats.add(improvement) // again, for the double effect
        }

        if (isCityCenter()) {
            if (stats.food < 2) stats.food = 2f
            if (stats.production < 1) stats.production = 1f
        }

        if (stats.production < 0) stats.production = 0f

        if ("Jungle" == terrainFeature && city != null
                && city.buildingUniques.contains("Jungles provide +2 science"))
            stats.science += 2f
        if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
            stats.gold++

        return stats
    }

    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean {
        if (isCityCenter() || improvement.name == this.improvement) return false
        val topTerrain = if (terrainFeature == null) getBaseTerrain() else getTerrainFeature()
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!)) return false
        if (improvement.terrainsCanBeBuiltOn.contains(topTerrain!!.name)) return true
        if (improvement.name == "Road" && this.roadStatus === RoadStatus.None) return true
        if (improvement.name == "Railroad" && this.roadStatus !== RoadStatus.Railroad) return true
        if (topTerrain.unbuildable) return false
        return hasViewableResource(civInfo) && tileResource.improvement == improvement.name

    }

    fun startWorkingOnImprovement(improvement: TileImprovement, civInfo: CivilizationInfo) {
        improvementInProgress = improvement.name
        turnsToImprovement = improvement.getTurnsToBuild(civInfo)
    }

    fun stopWorkingOnImprovement() {
        improvementInProgress = null
    }


    override fun toString(): String {
        val SB = StringBuilder()
        if (isCityCenter()) {
            val city = getCity()!!
            SB.appendln(city.name+ " ("+city.health+"),\r\n" + city.cityConstructions.getProductionForTileInfo())
        }
        SB.appendln(this.baseTerrain.tr())
        if (terrainFeature != null) SB.appendln(terrainFeature!!.tr())
        if (hasViewableResource(tileMap.gameInfo.getPlayerCivilization())) SB.appendln(resource!!.tr())
        if (roadStatus !== RoadStatus.None && !isCityCenter()) SB.appendln(roadStatus.toString().tr())
        if (improvement != null) SB.appendln(improvement!!.tr())
        if (improvementInProgress != null) SB.appendln("{$improvementInProgress} in ${this.turnsToImprovement} {turns}".tr())
        val isViewableToPlayer = UnCivGame.Current.gameInfo.getPlayerCivilization().getViewableTiles().contains(this)
            || UnCivGame.Current.viewEntireMapForDebug
        if (civilianUnit != null && isViewableToPlayer) SB.appendln(civilianUnit!!.name)
        if(militaryUnit!=null && isViewableToPlayer){
            var milUnitString = militaryUnit!!.name
            if(militaryUnit!!.health<100) milUnitString += "(" + militaryUnit!!.health + ")"
            SB.appendln(milUnitString)
        }
        return SB.toString().trim()
    }

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean {
        return resource != null && (tileResource.revealedBy == null || civInfo.tech.isResearched(tileResource.revealedBy!!))
    }

    fun hasIdleUnit(): Boolean {
        return getUnits().any{it.isIdle()}
    }

    fun getViewableTiles(distance:Int): MutableList<TileInfo> {
        return tileMap.getViewableTiles(this.position,distance)
    }

    fun getTilesInDistance(distance:Int): List<TileInfo> {
        return tileMap.getTilesInDistance(position,distance)
    }

    fun getDefensiveBonus(): Float {
        var bonus = getBaseTerrain().defenceBonus
        if(terrainFeature!=null) bonus += getTerrainFeature()!!.defenceBonus
        return bonus
    }

    fun isWorked(): Boolean {
        val city = getCity()
        return city!=null && city.workedTiles.contains(position)
    }
}