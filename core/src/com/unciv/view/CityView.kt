package com.unciv.view

import com.unciv.logic.city.City
import com.unciv.logic.city.CityFlags
import com.unciv.logic.map.HexCoord
import com.unciv.logic.city.CityFocus
import com.unciv.logic.city.CityResources
import com.unciv.logic.city.GreatPersonPointsBreakdown
import com.unciv.logic.city.managers.CityReligionManager
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Counter
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import yairm210.purity.annotations.Readonly

/** View of a [City] from the perspective of [viewer]. UI should use this and not city directly. */
class CityView(internal val city: City, internal val viewer: Civilization) {
    val name: String get() = city.name
    val location: HexCoord get() = city.location
    val tilesInRange: Set<Tile> get() = city.tilesInRange

    @Readonly fun civ(): CivView = CivView(city.civ, viewer)
    @Readonly fun centerTile(): TileView = TileView(city.getCenterTile(), viewer)
    @Readonly fun getTiles(): Sequence<TileView> = city.getTiles().map { TileView(it, viewer) }
    @Readonly fun tileView(tile: Tile): TileView = TileView(tile, viewer)

    @Readonly fun getWorkRange(): Int = city.getWorkRange()
    @Readonly fun isWorked(tileView: TileView): Boolean = city.isWorked(tileView.tile)
    @Readonly fun canBuyTile(tileView: TileView): Boolean = city.expansion.canBuyTile(tileView.tile)
    @Readonly fun getGoldCostOfTile(tileView: TileView, extraTiles: Int = 0): Int =
        city.expansion.getGoldCostOfTile(tileView.tile, extraTiles)
    @Readonly fun isSameCivAs(other: CityView): Boolean = city.civ === other.city.civ

    // Population
    @Readonly fun getFreePopulation(): Int = city.population.getFreePopulation()
    @Readonly fun getPopulationCount(): Int = city.population.population
    @Readonly fun getFoodStored(): Int = city.population.foodStored
    @Readonly fun getFoodToNextPopulation(): Int = city.population.getFoodToNextPopulation()
    @Readonly fun getMaxSpecialists(): Counter<String> = city.population.getMaxSpecialists()
    @Readonly fun getNewSpecialists(): Counter<String> = city.population.getNewSpecialists()
    @Readonly fun getNumTurnsToStarvation(): Int? = city.population.getNumTurnsToStarvation()
    @Readonly fun getNumTurnsToNewPopulation(): Int? = city.population.getNumTurnsToNewPopulation()

    // City state
    @Readonly fun getNumberOfFollowers(): Counter<String> = city.religion.getNumberOfFollowers()
    @Readonly fun religion(): CityReligionManager = city.religion
    @Readonly fun isStarving(): Boolean = city.isStarving()
    @Readonly fun isGrowing(): Boolean = city.isGrowing()
    @Readonly fun isInResistance(): Boolean = city.isInResistance()
    @Readonly fun isWeLoveTheKingDayActive(): Boolean = city.isWeLoveTheKingDayActive()
    val demandedResource: String get() = city.demandedResource
    @Readonly fun getFlag(flag: CityFlags): Int = city.getFlag(flag)
    @Readonly fun getCityFocus(): CityFocus = city.getCityFocus()
    @Readonly fun getState(): GameContext = city.state

    // Stats
    @Readonly fun getCurrentCityStats(): Stats = city.cityStats.currentCityStats
    @Readonly fun getHappinessList(): Map<String, Float> = city.cityStats.happinessList

    // Expansion
    @Readonly fun hasChoosableTiles(): Boolean = city.expansion.getChoosableTiles().any()
    @Readonly fun getCultureToNextTile(): Int = city.expansion.getCultureToNextTile()
    @Readonly fun getCultureStored(): Int = city.expansion.cultureStored

    // Constructions
    val constructions: CityConstructionsView get() = CityConstructionsView(city.cityConstructions)
    @Readonly fun currentConstructionName(): String = city.cityConstructions.currentConstructionName()
    @Readonly fun getBuiltBuildings(): Sequence<Building> = city.cityConstructions.getBuiltBuildings()
    @Readonly fun isPuppet(): Boolean = city.isPuppet
    @Readonly fun hasMatchingUnique(uniqueType: UniqueType): Boolean = city.getMatchingUniques(uniqueType).any()
    @Readonly fun getDisabledConstructions(): Set<String> = city.disabledConstructions
    @Readonly fun isStatRelated(stat: Stat, building: Building): Boolean = building.isStatRelated(stat, city)
    @Readonly fun getProductionTooltip(construction: PerpetualConstruction): String = construction.getProductionTooltip(city)
    @Readonly fun getResourceRequirementsPerTurn(construction: IConstruction): Counter<String> =
        if (construction is BaseUnit) construction.getResourceRequirementsPerTurn(city.civ.state)
        else construction.getResourceRequirementsPerTurn(city.state)
    @Readonly fun getStockpiledResourceRequirements(construction: IConstruction): Counter<String> =
        construction.getStockpiledResourceRequirements(city.state)
    @Readonly fun getConstructionProductionCost(construction: INonPerpetualConstruction): Int =
        construction.getProductionCost(city.civ, city)
    @Readonly fun getUnitDescription(unit: BaseUnit): String = unit.getDescription(city)
    @Readonly fun getBuildingDescription(building: Building): String = building.getDescription(city, true)
    @Readonly fun getConversionRate(statConversion: PerpetualConstruction.StatConversion): Int = statConversion.getConversionRate(city)
    @Readonly fun getGoldForSellingBuilding(buildingName: String): Int = city.getGoldForSellingBuilding(buildingName)
    @Readonly fun hasSoldBuildingThisTurn(): Boolean = city.hasSoldBuildingThisTurn
    @Readonly fun isGodModeEnabled(): Boolean = city.civ.gameInfo.gameParameters.godMode
    @Readonly fun getUnitShouldUseSavedPromotion(baseUnit: String): Boolean? = city.unitShouldUseSavedPromotion[baseUnit]
    @Readonly fun getCityAmbienceSound(): String = city.civ.getEra().citySound
    @Readonly fun isBeingRazed(): Boolean = city.isBeingRazed
    @Readonly fun canBeDestroyed(): Boolean = city.canBeDestroyed()
    @Readonly fun getExpandRange(): Int = city.getExpandRange()
    @Readonly fun chooseNewTileToOwn(): Tile? = city.expansion.chooseNewTileToOwn()
    @Readonly fun getImprovementToCreate(construction: Building): TileImprovement? =
        construction.getImprovementToCreate(city.getRuleset(), city.civ)
    @Readonly fun hasFreeBuilding(building: Building): Boolean =
        city.civ.civConstructions.hasFreeBuilding(city, building)

    // Resources/misc
    @Readonly fun getResourceStockpiles(): Counter<String> = city.resourceStockpiles
    @Readonly fun getCityResourcesAvailableToCity(): ResourceSupplyList = CityResources.getCityResourcesAvailableToCity(city)
    @Readonly fun getGreatPersonPointsBreakdown(): GreatPersonPointsBreakdown = GreatPersonPointsBreakdown(city)
    @Readonly fun getRuleset(): Ruleset = city.getRuleset()
    @Readonly fun getBuildingStats(building: Building): Stats = building.getStats(city)

    // ACTIONS
    private fun canChangeState() = city.civ === viewer && viewer.isCurrentPlayer()

    fun tryLockTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        if (!isWorked(tileView)) return false
        return city.lockTile(tileView.tile)
    }
    fun tryUnlockTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.unlockTile(tileView.tile)
    }
    fun tryBuyTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        if (!city.expansion.canBuyTile(tileView.tile)) return false
        city.expansion.buyTile(tileView.tile)
        return true
    }
    fun tryWorkTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.workTile(tileView.tile)
    }
    fun tryStopWorkingTile(tileView: TileView): Boolean {
        if (!canChangeState()) return false
        return city.stopWorkingTile(tileView.tile)
    }
    fun tryAddToQueue(name: String): Boolean {
        if (!canChangeState()) return false
        city.cityConstructions.addToQueue(name)
        return true
    }
    fun tryRemoveFromQueue(index: Int, automatic: Boolean): Boolean {
        if (!canChangeState()) return false
        city.cityConstructions.removeFromQueue(index, automatic)
        return true
    }
    fun tryRaisePriority(index: Int): Int? {
        if (!canChangeState()) return null
        return city.cityConstructions.raisePriority(index)
    }
    fun tryLowerPriority(index: Int): Int? {
        if (!canChangeState()) return null
        return city.cityConstructions.lowerPriority(index)
    }
    fun updateTileStats() = city.cityStats.updateTileStats()

    fun updateCityStats() = city.cityStats.update()
    fun tryAnnexCity(): Boolean {
        if (!canChangeState()) return false
        city.annexCity()
        return true
    }
    fun trySetRazing(raze: Boolean): Boolean {
        if (!canChangeState()) return false
        city.isBeingRazed = raze
        return true
    }
    fun tryAddToQueueWithTile(construction: IConstruction, tile: Tile): Boolean {
        if (!canChangeState()) return false
        city.cityConstructions.addToQueue(construction, tile = tile)
        return true
    }
    fun trySetUnitShouldUseSavedPromotion(baseUnit: String, value: Boolean): Boolean {
        if (!canChangeState()) return false
        city.unitShouldUseSavedPromotion[baseUnit] = value
        return true
    }
    fun trySellBuilding(construction: Building): Boolean {
        if (!canChangeState()) return false
        city.sellBuilding(construction)
        return true
    }
    fun tryReassignPopulation(): Boolean {
        if (!canChangeState()) return false
        city.reassignPopulation()
        return true
    }
    fun trySetCityFocus(focus: CityFocus): Boolean {
        if (!canChangeState()) return false
        city.setCityFocus(focus)
        city.reassignPopulation()
        return true
    }

    override fun equals(other: Any?) = other is CityView && city === other.city
    override fun hashCode() = city.hashCode()
}
