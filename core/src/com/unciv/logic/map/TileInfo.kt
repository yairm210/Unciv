package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tile.Terrain
import com.unciv.models.gamebasics.tile.TileImprovement
import com.unciv.models.gamebasics.tile.TileResource
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.tr
import kotlin.math.abs

open class TileInfo {
    @Transient lateinit var tileMap: TileMap
    @Transient var owningCity:CityInfo?=null

    var militaryUnit:MapUnit?=null
    var civilianUnit:MapUnit?=null

    var position: Vector2 = Vector2.Zero
    lateinit var baseTerrain: String
    var terrainFeature: String? = null
    var resource: String? = null
    var improvement: String? = null
    var improvementInProgress: String? = null

    var roadStatus = RoadStatus.None
    var turnsToImprovement: Int = 0


    fun clone(): TileInfo {
        val toReturn = TileInfo()
        if(militaryUnit!=null) toReturn.militaryUnit=militaryUnit!!.clone()
        if(civilianUnit!=null) toReturn.civilianUnit=civilianUnit!!.clone()
        toReturn.position=position.cpy()
        toReturn.baseTerrain=baseTerrain
        toReturn.terrainFeature=terrainFeature
        toReturn.resource=resource
        toReturn.improvement=improvement
        toReturn.improvementInProgress=improvementInProgress
        toReturn.roadStatus=roadStatus
        toReturn.turnsToImprovement=turnsToImprovement
        return toReturn
    }


    fun getUnits(): List<MapUnit> {
        val list = ArrayList<MapUnit>(2)
        if(militaryUnit!=null) list.add(militaryUnit!!)
        if(civilianUnit!=null) list.add(civilianUnit!!)
        return list
        // this used to be "return listOf(militaryUnit,civilianUnit).filterNotNull()" but profiling revealed that that took considerably longer
    }

    fun getCity(): CityInfo? {
        return owningCity
    }

    val lastTerrain: Terrain
        get() = if (terrainFeature == null) getBaseTerrain() else getTerrainFeature()!!

    fun getTileResource(): TileResource =
            if (resource == null) throw Exception("No resource exists for this tile!")
            else GameBasics.TileResources[resource!!]!!

    fun isCityCenter(): Boolean = getCity()?.location == position

    fun getTileImprovement(): TileImprovement? = if (improvement == null) null else GameBasics.TileImprovements[improvement!!]


    // This is for performance - since we access the neighbors of a tile ALL THE TIME,
    // and the neighbors of a tile never change, it's much more CPU efficient to save the list once and for all!
    @Transient private var internalNeighbors : List<TileInfo>?=null
    val neighbors: List<TileInfo>
        get(){
            if(internalNeighbors==null)
                internalNeighbors = getTilesAtDistance(1)
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

        if((baseTerrain=="Ocean"||baseTerrain=="Coast") && city!=null
                && city.getBuildingUniques().contains("+1 food from Ocean and Coast tiles"))
            stats.food += 1

        if (terrainFeature != null) {
            val terrainFeatureBase = getTerrainFeature()
            if (terrainFeatureBase!!.overrideStats)
                stats = terrainFeatureBase.clone()
            else
                stats.add(terrainFeatureBase)

            if (terrainFeature == "Jungle" && city != null
                    && city.getBuildingUniques().contains("Jungles provide +2 science"))
                stats.science += 2f
            if(terrainFeature=="Oasis" && city!=null
                    && city.getBuildingUniques().contains("+2 Gold for each source of Oil and oasis"))
                stats.gold += 2
        }

        if (hasViewableResource(observingCiv)) {
            val resource = getTileResource()
            stats.add(getTileResource()) // resource base
            if (resource.building != null && city != null && city.cityConstructions.isBuilt(resource.building!!)) {
                stats.add(resource.getBuilding()!!.resourceBonusStats!!) // resource-specific building (eg forge, stable) bonus
            }
            if(resource.resourceType==ResourceType.Strategic
                    && observingCiv.getNation().unique=="Strategic Resources provide +1 Production, and Horses, Iron and Uranium Resources provide double quantity")
                stats.production+=1
            if(resource.name=="Oil" && city!=null
                    && city.getBuildingUniques().contains("+2 Gold for each source of Oil and oasis"))
                stats.gold += 2
        }

        val improvement = getTileImprovement()
        if (improvement != null) {
            if (resource != null && getTileResource().improvement == improvement.name)
                stats.add(getTileResource().improvementStats!!) // resource-specifc improvement
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

        if (stats.gold != 0f && observingCiv.goldenAges.isGoldenAge())
            stats.gold++

        if (stats.production < 0) stats.production = 0f

        return stats
    }

    fun canBuildImprovement(improvement: TileImprovement, civInfo: CivilizationInfo): Boolean {
        if (isCityCenter() || improvement.name == this.improvement) return false
        val topTerrain = if (terrainFeature == null) getBaseTerrain() else getTerrainFeature()
        if (improvement.techRequired != null && !civInfo.tech.isResearched(improvement.techRequired!!)) return false
        if (improvement.terrainsCanBeBuiltOn.contains(topTerrain!!.name)) return true
        if (improvement.name == "Road" && this.roadStatus === RoadStatus.None) return true
        if (improvement.name == "Railroad" && this.roadStatus !== RoadStatus.Railroad) return true
        if (topTerrain.unbuildable && !(topTerrain.name=="Forest" && improvement.name=="Camp")) return false
        return hasViewableResource(civInfo) && getTileResource().improvement == improvement.name

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
        val isViewableToPlayer = UnCivGame.Current.gameInfo.getPlayerCivilization().getViewableTiles().contains(this)
                || UnCivGame.Current.viewEntireMapForDebug

        if (isCityCenter()) {
            val city = getCity()!!
            var cityString = city.name
            if(isViewableToPlayer) cityString += " ("+city.health+")"
            SB.appendln(cityString)
            if(city.civInfo.isPlayerCivilization() || UnCivGame.Current.viewEntireMapForDebug)
                SB.appendln(city.cityConstructions.getProductionForTileInfo())
        }
        SB.appendln(this.baseTerrain.tr())
        if (terrainFeature != null) SB.appendln(terrainFeature!!.tr())
        if (hasViewableResource(tileMap.gameInfo.getPlayerCivilization())) SB.appendln(resource!!.tr())
        if (roadStatus !== RoadStatus.None && !isCityCenter()) SB.appendln(roadStatus.toString().tr())
        if (improvement != null) SB.appendln(improvement!!.tr())
        if (improvementInProgress != null && isViewableToPlayer) SB.appendln("{$improvementInProgress} in ${this.turnsToImprovement} {turns}".tr())
        if (civilianUnit != null && isViewableToPlayer) SB.appendln(civilianUnit!!.name.tr())
        if(militaryUnit!=null && isViewableToPlayer){
            var milUnitString = militaryUnit!!.name.tr()
            if(militaryUnit!!.health<100) milUnitString += "(" + militaryUnit!!.health + ")"
            SB.appendln(milUnitString)
        }
        if(getDefensiveBonus()!=0f){
            var defencePercentString = (getDefensiveBonus()*100).toInt().toString()+"%"
            if(!defencePercentString.startsWith("-")) defencePercentString = "+$defencePercentString"
            SB.appendln("[$defencePercentString] to unit defence".tr())
        }

        return SB.toString().trim()
    }

    fun hasViewableResource(civInfo: CivilizationInfo): Boolean {
        return resource != null && (getTileResource().revealedBy == null || civInfo.tech.isResearched(getTileResource().revealedBy!!))
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

    fun getTilesAtDistance(distance:Int): List<TileInfo> {
        return tileMap.getTilesAtDistance(position,distance)
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

    fun arialDistanceTo(otherTile:TileInfo): Int {
        val xDelta = position.x-otherTile.position.x
        val yDelta = position.y-otherTile.position.y
        return listOf(abs(xDelta),abs(yDelta), abs(xDelta-yDelta)).max()!!.toInt()
    }
}