package com.unciv.logic.city

import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueType

object CityResources {

    fun getCityResources(city: City): ResourceSupplyList {
        val cityResources = ResourceSupplyList()

        val resourceModifer = HashMap<String, Float>()
        for (resource in city.civ.gameInfo.ruleset.tileResources.values)
            resourceModifer[resource.name] = city.civ.getResourceModifier(resource)

        getResourcesFromTiles(city, resourceModifer, cityResources)

        getResourceFromUniqueImprovedTiles(city, cityResources, resourceModifer)

        manageCityResourcesRequiredByBuildings(city, cityResources)

        getCityResourcesFromCiv(city, cityResources, resourceModifer)

        if (city.civ.isCityState() && city.isCapital() && city.civ.cityStateResource != null) {
            cityResources.add(
                city.getRuleset().tileResources[city.civ.cityStateResource]!!,
                "Mercantile City-State"
            )
        }

        return cityResources
    }

    /** Gets the number of resources available to this city
     * Accommodates both city-wide and civ-wide resources */
    fun getResourceAmount(city: City, resourceName: String): Int {
        val resource = city.getRuleset().tileResources[resourceName] ?: return 0

        if (resource.hasUnique(UniqueType.CityResource))
            return getCityResources(city).asSequence().filter { it.resource == resource }.sumOf { it.amount }
        return city.civ.getResourceAmount(resourceName)
    }

    private fun getResourcesFromTiles(city: City, resourceModifer: HashMap<String, Float>, cityResources: ResourceSupplyList) {
        for (tileInfo in city.getTiles().filter { it.resource != null }) {
            val resource = tileInfo.tileResource
            val amount = getTileResourceAmount(city, tileInfo) * resourceModifer[resource.name]!!
            if (amount > 0) cityResources.add(resource, "Tiles", amount.toInt())
        }
    }

    private fun getResourceFromUniqueImprovedTiles(city: City, cityResources: ResourceSupplyList, resourceModifer: HashMap<String, Float>) {
        for (tileInfo in city.getTiles().filter { it.getUnpillagedImprovement() != null }) {
            val stateForConditionals = StateForConditionals(city.civ, city, tile = tileInfo)
            val tileImprovement = tileInfo.getUnpillagedTileImprovement()
            for (unique in tileImprovement!!.getMatchingUniques(UniqueType.ProvidesResources, stateForConditionals)) {
                val resource = city.getRuleset().tileResources[unique.params[1]] ?: continue
                cityResources.add(
                    resource, "Improvements",
                    (unique.params[0].toFloat() * resourceModifer[resource.name]!!).toInt()
                )
            }
            for (unique in tileImprovement.getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals)) {
                val resource = city.getRuleset().tileResources[unique.params[1]] ?: continue
                cityResources.add(
                    resource, "Improvements",
                    -1 * unique.params[0].toInt()
                )
            }
        }
    }

    private fun manageCityResourcesRequiredByBuildings(city: City, cityResources: ResourceSupplyList) {
        val freeBuildings = city.civ.civConstructions.getFreeBuildingNames(city)
        for (building in city.cityConstructions.getBuiltBuildings()) {
            // Free buildings cost no resources
            if (building.name in freeBuildings) continue
            cityResources.subtractResourceRequirements(building.getResourceRequirementsPerTurn(StateForConditionals(city.civ, city)), city.getRuleset(), "Buildings")
        }
    }

    private fun getCityResourcesFromCiv(city: City, cityResources: ResourceSupplyList, resourceModifer: HashMap<String, Float>) {
        val stateForConditionals = StateForConditionals(city)
        for (unique in city.getMatchingUniques(UniqueType.ProvidesResources, stateForConditionals)) { // E.G "Provides [1] [Iron]"
            val resource = city.getRuleset().tileResources[unique.params[1]]
                ?: continue
            if (!resource.hasUnique(UniqueType.CityResource, stateForConditionals)) continue
            cityResources.add(
                resource, unique.getSourceNameForUser(),
                (unique.params[0].toFloat() * resourceModifer[resource.name]!!).toInt()
            )
        }
    }

    private fun getTileResourceAmount(city: City, tile: Tile): Int {
        if (tile.resource == null) return 0
        if (!tile.providesResources(city.civ)) return 0

        val resource = tile.tileResource
        var amountToAdd = if (resource.resourceType == ResourceType.Strategic) tile.resourceAmount
        else 1
        if (resource.resourceType == ResourceType.Luxury
            && city.containsBuildingUnique(UniqueType.ProvidesExtraLuxuryFromCityResources))
            amountToAdd += 1

        return amountToAdd
    }
}
